-- MySQL 8.0.16+ (CHECK constraints are enforced). InnoDB. Use a fresh database when upgrading from the PostgreSQL version.

CREATE TABLE IF NOT EXISTS shows (
  id             CHAR(36)     NOT NULL,
  name           VARCHAR(255) NOT NULL,
  price_paise    BIGINT       NOT NULL,
  per_user_limit INT          NOT NULL,
  total_seats    INT          NOT NULL,
  version        BIGINT       NOT NULL DEFAULT 0,
  created_at     TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  CONSTRAINT chk_shows_price CHECK (price_paise >= 0),
  CONSTRAINT chk_shows_limit CHECK (per_user_limit > 0),
  CONSTRAINT chk_shows_total CHECK (total_seats > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- One row per seat. Pessimistic row lock + version is the atomic decision point.
-- label uses a binary collation: exact, case-sensitive, and PK order == ORDER BY label order (deterministic lock order).
CREATE TABLE IF NOT EXISTS seats (
  show_id        CHAR(36)    NOT NULL,
  label          VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  status         VARCHAR(16) NOT NULL DEFAULT 'available',
  reservation_id CHAR(36)    NULL,
  version        BIGINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (show_id, label),
  KEY seats_reservation_idx (reservation_id),
  CONSTRAINT fk_seats_show FOREIGN KEY (show_id) REFERENCES shows (id),
  CONSTRAINT chk_seats_status CHECK (status IN ('available','held','confirmed')),
  CONSTRAINT chk_seats_res CHECK ((status = 'available') = (reservation_id IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS reservations (
  id           CHAR(36)     NOT NULL,
  show_id      CHAR(36)     NOT NULL,
  user_id      VARCHAR(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  seats        TEXT         NOT NULL,
  amount_paise BIGINT       NOT NULL,
  status       VARCHAR(16)  NOT NULL,
  version      BIGINT       NOT NULL DEFAULT 0,
  created_at   TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  CONSTRAINT fk_reservations_show FOREIGN KEY (show_id) REFERENCES shows (id),
  CONSTRAINT chk_reservations_status CHECK (status IN ('confirmed','cancelled'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Per (show,user) counter: pessimistically locked to serialise a user's own requests (per-user limit).
CREATE TABLE IF NOT EXISTS user_show_counts (
  show_id CHAR(36)    NOT NULL,
  user_id VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  active  INT         NOT NULL DEFAULT 0,
  version BIGINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (show_id, user_id),
  CONSTRAINT chk_counts_active CHECK (active >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Idempotency: written in the SAME transaction as the reservation. The PK is the cross-instance exactly-once gate.
CREATE TABLE IF NOT EXISTS idempotency_keys (
  user_id        VARCHAR(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  idem_key       VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  request_hash   CHAR(64)     NOT NULL,
  reservation_id CHAR(36)     NOT NULL,
  version        BIGINT       NOT NULL DEFAULT 0,
  created_at     TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (user_id, idem_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
