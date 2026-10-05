import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * On-sale stampede against a live URL.   Usage: java burst/Burst.java <BASE_URL> [ADMIN_TOKEN]
 * Env: SEATS (2000) REQUESTS (20000) CONCURRENCY (1000) HOT_SEATS (20) HOT_USERS (500)
 */
public class Burst {
    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    static String base;
    static final Map<String, LongAdder> tally = new ConcurrentHashMap<>();
    static final Map<String, LongAdder> wins = new ConcurrentHashMap<>();
    static final LongAdder n201 = new LongAdder();

    record R(int status, String body) {}

    static R call(String method, String path, String token, String idem, String body) {
        try {
            var b = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json");
            if (token != null) b.header("Authorization", "Bearer " + token);
            if (idem != null) b.header("Idempotency-Key", idem);
            b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
            var r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
            return new R(r.statusCode(), r.body());
        } catch (Exception e) {
            return new R(-1, String.valueOf(e));
        }
    }

    static String find(String body, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
        return m.find() ? m.group(1) : "?";
    }

    static long num(String body, String field) {
        Matcher m = Pattern.compile("\"" + field + "\"\\s*:\\s*(\\d+)").matcher(body);
        return m.find() ? Long.parseLong(m.group(1)) : -1;
    }

    static String classify(R r) {
        if (r.status == 201) return "201 confirmed";
        if (r.status == 200) return "200 idempotent-replay";
        if (r.status == 409 || r.status == 422) return r.status + " declined:" + find(r.body, "reason");
        if (r.status >= 500 || r.status < 0) return "5xx/transport-error(" + r.status + ")";
        return r.status + " other";
    }

    static R record(String seat, R r) {
        tally.computeIfAbsent(classify(r), k -> new LongAdder()).increment();
        if (r.status == 201) {
            n201.increment();
            wins.computeIfAbsent(seat, k -> new LongAdder()).increment();
        }
        return r;
    }

    static String reserveBody(String seat, String userIdSpoof) {
        return "{\"seats\":[\"" + seat + "\"]" + (userIdSpoof == null ? "" : ",\"user_id\":\"" + userIdSpoof + "\"") + "}";
    }

    static <T> List<T> parallel(int conc, List<Supplier<T>> tasks) throws Exception {
        var sem = new Semaphore(conc);
        try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<T>> fs = new ArrayList<>();
            for (var t : tasks) fs.add(ex.submit(() -> { sem.acquire(); try { return t.get(); } finally { sem.release(); } }));
            List<T> out = new ArrayList<>();
            for (var f : fs) out.add(f.get());
            return out;
        }
    }

    static int envInt(String k, int d) { String v = System.getenv(k); return v == null ? d : Integer.parseInt(v); }
    static boolean ok = true;
    static void check(String name, boolean cond) { System.out.println((cond ? "  PASS  " : "  FAIL  ") + name); if (!cond) ok = false; }

    public static void main(String[] a) throws Exception {
        base = a[0].replaceAll("/+$", "");
        String admin = a.length > 1 ? a[1] : System.getenv().getOrDefault("ADMIN_TOKEN", "admin-secret");
        int seatsN = envInt("SEATS", 2000), requests = envInt("REQUESTS", 20000), conc = envInt("CONCURRENCY", 1000);
        int hotSeats = envInt("HOT_SEATS", 20), hotUsers = envInt("HOT_USERS", 500);
        String run = Long.toString(System.currentTimeMillis(), 36);

        System.out.println("== readiness ==");
        R rd = call("GET", "/readyz", null, null, null);
        System.out.println("  /readyz -> " + rd.status + " " + rd.body);

        StringBuilder sb = new StringBuilder("{\"name\":\"burst-" + run + "\",\"price_paise\":25000,\"seats\":[");
        for (int i = 1; i <= seatsN; i++) sb.append(i > 1 ? "," : "").append("\"S").append(i).append('"');
        sb.append("]}");
        R created = call("POST", "/shows", admin, null, sb.toString());
        if (created.status != 201) { System.out.println("create show failed: " + created.status + " " + created.body); System.exit(2); }
        String show = find(created.body, "id");
        System.out.println("show " + show + " with " + seatsN + " seats");
        String path = "/shows/" + show + "/reserve";

        // ---- Phase 1: hot-seat storm: HOT_USERS different users, ONE seat, all at once
        System.out.println("\n== phase 1: hot-seat storm (" + hotUsers + " users -> S1) ==");
        List<Supplier<R>> t1 = new ArrayList<>();
        for (int i = 0; i < hotUsers; i++) { int u = i; t1.add(() -> record("S1", call("POST", path, "storm-" + u, "storm-key-" + u, reserveBody("S1", null)))); }
        long c0 = n201.sum();
        parallel(hotUsers, t1);
        check("exactly one 201 for S1 (got " + (n201.sum() - c0) + ")", n201.sum() - c0 == 1);

        // ---- Phase 2: per-user limit (limit=4): one user fires 10 parallel reserves on distinct seats
        System.out.println("\n== phase 2: per-user limit (10 parallel, limit 4) ==");
        List<Supplier<R>> t2 = new ArrayList<>();
        for (int i = 0; i < 10; i++) { int s = 1001 + i; t2.add(() -> record("S" + s, call("POST", path, "limit-user", "limit-" + s, reserveBody("S" + s, null)))); }
        c0 = n201.sum();
        parallel(10, t2);
        check("limit-user holds <= 4 (got " + (n201.sum() - c0) + ")", n201.sum() - c0 <= 4 && n201.sum() - c0 >= 1);

        // ---- Phase 3: idempotency, spoofing, cancel ownership, re-book
        System.out.println("\n== phase 3: idempotency / identity / cancel ==");
        R first = record("S1900", call("POST", path, "idem-user", "idem-1", reserveBody("S1900", "mallory")));
        String rid = find(first.body, "reservation_id");
        check("first reserve 201", first.status == 201);
        check("spoofed body user_id ignored (user_id is token's)", find(first.body, "user_id").equals("idem-user"));
        R again = record("S1900", call("POST", path, "idem-user", "idem-1", reserveBody("S1900", null)));
        check("same key+body -> original reservation (200, same id)", again.status == 200 && find(again.body, "reservation_id").equals(rid));
        R diff = record("S1901", call("POST", path, "idem-user", "idem-1", reserveBody("S1901", null)));
        check("same key, different seats -> 409", diff.status == 409);
        R steal = call("POST", "/reservations/" + rid + "/cancel", "mallory", null, null);
        check("non-owner cancel -> 403", steal.status == 403);
        R own = call("POST", "/reservations/" + rid + "/cancel", "idem-user", null, null);
        check("owner cancel -> 200 cancelled", own.status == 200 && find(own.body, "status").equals("cancelled"));
        int cancels = 1;
        R rebook = record("S1900", call("POST", path, "someone-else", "rebook-1", reserveBody("S1900", null)));
        check("released seat re-bookable by another user", rebook.status == 201);

        // ---- Phase 4: the stampede
        System.out.println("\n== phase 4: stampede (" + requests + " requests, concurrency " + conc + ", ~60% on " + hotSeats + " hot seats, ~10% retries) ==");
        Random rnd = new Random(42);
        List<Supplier<R>> t4 = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            String seat = rnd.nextInt(100) < 60 ? "S" + (1 + rnd.nextInt(hotSeats)) : "S" + (1 + rnd.nextInt(900));
            String user = "buyer-" + (i % 8000);
            String key = "k-" + i;
            t4.add(() -> record(seat, call("POST", path, user, key, reserveBody(seat, null))));
            if (rnd.nextInt(100) < 10) t4.add(() -> record(seat, call("POST", path, user, key, reserveBody(seat, null))));
        }
        Collections.shuffle(t4, rnd);
        long t0 = System.nanoTime();
        parallel(conc, t4);
        double secs = (System.nanoTime() - t0) / 1e9;
        System.out.printf("  %d requests in %.1fs (%.0f req/s)%n", t4.size(), secs, t4.size() / secs);

        // ---- Results
        System.out.println("\n== outcome distribution (all phases) ==");
        new TreeMap<>(tally).forEach((k, v) -> System.out.printf("  %-45s %d%n", k, v.sum()));
        long fiveXX = tally.entrySet().stream().filter(e -> e.getKey().startsWith("5xx")).mapToLong(e -> e.getValue().sum()).sum();

        System.out.println("\n== final reconciliation ==");
        R fin = call("GET", "/shows/" + show + "?include_seats=false", null, null, null);
        long avail = num(fin.body, "available"), held = num(fin.body, "held"), conf = num(fin.body, "confirmed"), total = num(fin.body, "total_seats");
        System.out.printf("  available=%d held=%d confirmed=%d total=%d%n", avail, held, conf, total);
        check("available + held + confirmed == total_seats", avail + held + conf == total);
        check("confirmed seats == 201s - cancels (" + n201.sum() + " - " + cancels + ")", conf == n201.sum() - cancels);
        long dupes = wins.entrySet().stream().filter(e -> e.getValue().sum() > (e.getKey().equals("S1900") ? 2 : 1)).count();
        check("no seat confirmed to two users (violations: " + dupes + ")", dupes == 0);
        check("zero 5xx / transport errors (got " + fiveXX + ")", fiveXX == 0);

        System.out.println("\n== /metrics (reservations_*, seats_*) ==");
        R m = call("GET", "/metrics", null, null, null);
        for (String line : m.body.split("\n"))
            if (line.startsWith("reservations_") || line.startsWith("seats_")) System.out.println("  " + line);

        System.out.println("\n" + (ok ? "ALL CHECKS PASSED" : "SOME CHECKS FAILED"));
        System.exit(ok ? 0 : 1);
    }
}
