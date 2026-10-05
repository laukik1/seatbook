package com.seatbook.config;

import com.seatbook.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

/**
 * "Authorization: Bearer <token>".
 *  - token == ADMIN_TOKEN        -> admin (POST /shows only)
 *  - any other token matching ID -> that token IS the user id ("alice" or "user:alice")
 * Identity is ONLY ever taken from this header, never from the body. In production verify a signed JWT here instead.
 */
@Component
public class AuthService {
    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9_.@-]{1,64}$");
    private final byte[] admin;

    public AuthService(@Value("${app.admin-token}") String adminToken) {
        this.admin = adminToken.getBytes(StandardCharsets.UTF_8);
    }

    private String token(String header) {
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7))
            throw new ApiException(401, "unauthorized", "missing bearer token");
        String t = header.substring(7).trim();
        if (t.isEmpty()) throw new ApiException(401, "unauthorized", "empty token");
        return t;
    }

    private boolean isAdmin(String t) {
        return MessageDigest.isEqual(admin, t.getBytes(StandardCharsets.UTF_8));
    }

    public void requireAdmin(String header) {
        if (!isAdmin(token(header))) throw new ApiException(403, "forbidden", "admin only");
    }

    public String requireUser(String header) {
        String t = token(header);
        if (isAdmin(t)) throw new ApiException(403, "forbidden", "admin token cannot act as a user");
        if (t.startsWith("user:")) t = t.substring(5);
        if (!ID.matcher(t).matches()) throw new ApiException(401, "unauthorized", "invalid token");
        return t;
    }
}
