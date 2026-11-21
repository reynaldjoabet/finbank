-- migrantbank initial schema. Targets PostgreSQL 19.
--
-- Conventions:
--   * Column order of users/accounts must match UserRow/AccountRow in
--     repo/Repositories.scala: Magnum reads `SELECT *` / `RETURNING *` by position.
--   * Enum-like TEXT columns carry CHECK constraints mirroring domain/Domain.scala.
--   * Every foreign key column is indexed so ON DELETE CASCADE / SET NULL / RESTRICT
--     checks never fall back to sequential scans.
--   * Constraints are named so violations surface readable names in app errors.
--   * No IF NOT EXISTS: a versioned migration should fail loudly on schema drift.

-- The application uses INSERT ... ON CONFLICT DO SELECT (new in 19), so refuse to
-- migrate an older server rather than failing later at request time.
DO $$
BEGIN
  IF current_setting('server_version_num')::int < 190000 THEN
    RAISE EXCEPTION 'migrantbank requires PostgreSQL 19 or later (server is %)',
      current_setting('server_version');
  END IF;
END
$$;

-- ---------------------------------------------------------------------------
-- Identity
-- ---------------------------------------------------------------------------

CREATE TABLE users (
  id              UUID        PRIMARY KEY,
  phone           TEXT        NOT NULL CONSTRAINT uq_users_phone UNIQUE,
  first_name      TEXT        NOT NULL,
  last_name       TEXT        NOT NULL,
  date_of_birth   TEXT        NOT NULL, -- kept TEXT: UserProfile.dateOfBirth is a String
  address         TEXT        NOT NULL,
  ssn_enc         TEXT        NOT NULL,
  ssn_last4       TEXT        NOT NULL CONSTRAINT ck_users_ssn_last4 CHECK (char_length(ssn_last4) = 4),
  kyc_status      TEXT        NOT NULL CONSTRAINT ck_users_kyc_status
                    CHECK (kyc_status IN ('PENDING', 'VERIFIED', 'REJECTED', 'MANUAL_REVIEW_REQUIRED')),
  password_hash   TEXT,
  role            TEXT        NOT NULL DEFAULT 'user' CONSTRAINT ck_users_role CHECK (role IN ('user', 'admin')),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- UserRepo.listPendingKyc: WHERE kyc_status IN (...) ORDER BY created_at DESC LIMIT 200.
-- A partial index holds only the review queue (a small slice of users) and returns
-- rows already sorted, instead of a low-selectivity index on kyc_status.
CREATE INDEX idx_users_kyc_review_queue ON users (created_at DESC)
  WHERE kyc_status IN ('PENDING', 'MANUAL_REVIEW_REQUIRED');

CREATE TABLE sms_codes (
  user_id     UUID        PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
  code_hash   TEXT        NOT NULL,
  expires_at  TIMESTAMPTZ NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
  token_id    UUID        PRIMARY KEY,
  user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  token_hash  TEXT        NOT NULL,
  expires_at  TIMESTAMPTZ NOT NULL,
  revoked_at  TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- RefreshTokenRepo.findValid looks up by (user_id, token_hash); the leading user_id
-- column also serves the ON DELETE CASCADE from users.
CREATE INDEX idx_refresh_user_hash ON refresh_tokens (user_id, token_hash);
-- Purge job for expired tokens.
CREATE INDEX idx_refresh_expires ON refresh_tokens (expires_at);

-- ---------------------------------------------------------------------------
-- Accounts and money movement
-- ---------------------------------------------------------------------------

-- Balances are updated on every money movement. fillfactor 90 leaves room on each
-- page so those updates stay HOT (balance_minor is not indexed), avoiding index
-- churn and reducing vacuum work on the hottest table.
CREATE TABLE accounts (
  id            UUID        PRIMARY KEY,
  user_id       UUID        REFERENCES users (id) ON DELETE SET NULL,
  account_type  TEXT        NOT NULL CONSTRAINT ck_accounts_type CHECK (account_type IN ('USER', 'SYSTEM')),
  name          TEXT        NOT NULL,
  currency      TEXT        NOT NULL CONSTRAINT ck_accounts_currency CHECK (currency ~ '^[A-Z]{3}$'),
  balance_minor BIGINT      NOT NULL DEFAULT 0,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  -- System (clearing / fund) accounts are owned by no user; they may go negative.
  CONSTRAINT ck_accounts_system_unowned CHECK (account_type <> 'SYSTEM' OR user_id IS NULL),
  -- Last line of defence behind the service-level "Insufficient funds" check.
  CONSTRAINT ck_accounts_user_non_negative CHECK (account_type <> 'USER' OR balance_minor >= 0)
) WITH (fillfactor = 90);

-- One USER account per user. Also the arbiter for AccountRepo.ensureUserAccount's
-- INSERT ... ON CONFLICT (user_id) WHERE account_type = 'USER' DO SELECT FOR UPDATE.
-- NULL user_id (orphaned after a user delete) never conflicts, so the predicate
-- does not need an IS NOT NULL term.
CREATE UNIQUE INDEX uq_accounts_user_main ON accounts (user_id)
  WHERE account_type = 'USER';

CREATE TABLE transfers (
  id              UUID        PRIMARY KEY,
  transfer_type   TEXT        NOT NULL CONSTRAINT ck_transfers_type CHECK (transfer_type IN ('P2P', 'ACH')),
  from_user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
  to_user_id      UUID        REFERENCES users (id) ON DELETE RESTRICT,
  ach_destination TEXT,
  amount_minor    BIGINT      NOT NULL CONSTRAINT ck_transfers_amount_positive CHECK (amount_minor > 0),
  currency        TEXT        NOT NULL CONSTRAINT ck_transfers_currency CHECK (currency ~ '^[A-Z]{3}$'),
  note            TEXT,
  status          TEXT        NOT NULL CONSTRAINT ck_transfers_status
                    CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED')),
  idempotency_key TEXT,
  risk_flag       BOOLEAN     NOT NULL DEFAULT FALSE,
  risk_reason     TEXT,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  -- P2P goes to a user, ACH goes to an external destination; never both.
  CONSTRAINT ck_transfers_destination CHECK (
    (transfer_type = 'P2P' AND to_user_id IS NOT NULL AND ach_destination IS NULL)
    OR (transfer_type = 'ACH' AND ach_destination IS NOT NULL AND to_user_id IS NULL)
  ),
  CONSTRAINT ck_transfers_no_self CHECK (to_user_id IS DISTINCT FROM from_user_id)
);

-- Sender history, newest first (listByUser), and TransferRepo.sumOutgoingVolume:
-- INCLUDE (status, amount_minor) lets the SUM run as an index-only scan.
-- The leading column also serves the ON DELETE RESTRICT check from users.
CREATE INDEX idx_transfers_from ON transfers (from_user_id, created_at DESC)
  INCLUDE (status, amount_minor);

-- Recipient side; ACH rows have no recipient, so leave them out of the index.
CREATE INDEX idx_transfers_to ON transfers (to_user_id, created_at DESC)
  WHERE to_user_id IS NOT NULL;

-- Idempotent retries: one transfer per (sender, type, key).
CREATE UNIQUE INDEX uq_transfers_idem
  ON transfers (from_user_id, transfer_type, idempotency_key)
  WHERE idempotency_key IS NOT NULL;

-- Fraud queue (listFlagged): flagged rows are rare, so index only those, newest
-- first, instead of a boolean index the planner would never use.
CREATE INDEX idx_transfers_flagged ON transfers (created_at DESC)
  WHERE risk_flag;

-- Time-range analytics over an append-mostly table: BRIN is a few pages in size
-- versus a full B-tree, and created_at correlates with physical order.
CREATE INDEX brin_transfers_created_at ON transfers USING brin (created_at);

-- ---------------------------------------------------------------------------
-- Lending
-- ---------------------------------------------------------------------------

CREATE TABLE loans (
  id              UUID        PRIMARY KEY,
  user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  principal_minor BIGINT      NOT NULL CONSTRAINT ck_loans_principal_positive CHECK (principal_minor > 0),
  currency        TEXT        NOT NULL CONSTRAINT ck_loans_currency CHECK (currency ~ '^[A-Z]{3}$'),
  fee_minor       BIGINT      NOT NULL CONSTRAINT ck_loans_fee_non_negative CHECK (fee_minor >= 0),
  due_date        DATE        NOT NULL,
  status          TEXT        NOT NULL CONSTRAINT ck_loans_status
                    CHECK (status IN ('OFFERED', 'ACTIVE', 'REPAID', 'DEFAULTED')),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_loans_user ON loans (user_id);

-- ---------------------------------------------------------------------------
-- Ledger (double entry)
-- ---------------------------------------------------------------------------

-- Created after loans so loan_id can carry a real foreign key (it had none before).
CREATE TABLE ledger_entries (
  id            UUID        PRIMARY KEY,
  account_id    UUID        NOT NULL REFERENCES accounts (id) ON DELETE RESTRICT,
  transfer_id   UUID        REFERENCES transfers (id) ON DELETE SET NULL,
  loan_id       UUID        REFERENCES loans (id) ON DELETE SET NULL,
  entry_type    TEXT        NOT NULL,
  amount_minor  BIGINT      NOT NULL CONSTRAINT ck_ledger_amount_nonzero CHECK (amount_minor <> 0),
  currency      TEXT        NOT NULL CONSTRAINT ck_ledger_currency CHECK (currency ~ '^[A-Z]{3}$'),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Account statements: entries for one account in time order.
CREATE INDEX idx_ledger_account ON ledger_entries (account_id, created_at);
-- Most entries reference at most one of transfer / loan; keep NULLs out.
CREATE INDEX idx_ledger_transfer ON ledger_entries (transfer_id) WHERE transfer_id IS NOT NULL;
CREATE INDEX idx_ledger_loan     ON ledger_entries (loan_id)     WHERE loan_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- Cards, family, payroll, support
-- ---------------------------------------------------------------------------

CREATE TABLE cards (
  id              UUID        PRIMARY KEY,
  user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  kind            TEXT        NOT NULL CONSTRAINT ck_cards_kind CHECK (kind IN ('VIRTUAL', 'PHYSICAL')),
  last4           TEXT        NOT NULL CONSTRAINT ck_cards_last4 CHECK (last4 ~ '^[0-9]{4}$'),
  status          TEXT        NOT NULL CONSTRAINT ck_cards_status CHECK (status IN ('ACTIVE', 'BLOCKED', 'CLOSED')),
  delivery_status TEXT        NOT NULL CONSTRAINT ck_cards_delivery_status
                    CHECK (delivery_status IN ('NOT_ORDERED', 'ORDERED', 'SHIPPED', 'DELIVERED', 'FAILED')),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_cards_user ON cards (user_id);

CREATE TABLE family_groups (
  id            UUID        PRIMARY KEY,
  owner_user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_family_owner ON family_groups (owner_user_id);

CREATE TABLE family_members (
  group_id UUID NOT NULL REFERENCES family_groups (id) ON DELETE CASCADE,
  user_id  UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  PRIMARY KEY (group_id, user_id)
);

-- The PK leads with group_id; this covers "groups a user belongs to" and the
-- ON DELETE CASCADE from users, which previously had no usable index.
CREATE INDEX idx_family_members_user ON family_members (user_id);

CREATE TABLE paycheck_enrollments (
  user_id       UUID        PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
  employer_name TEXT        NOT NULL,
  partner_ref   TEXT        NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE support_tickets (
  id         UUID        PRIMARY KEY,
  user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  message    TEXT        NOT NULL,
  status     TEXT        NOT NULL CONSTRAINT ck_tickets_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'CLOSED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tickets_user ON support_tickets (user_id, created_at DESC);
-- Support work queue: only unresolved tickets, oldest first.
CREATE INDEX idx_tickets_open ON support_tickets (created_at)
  WHERE status <> 'CLOSED';

-- ---------------------------------------------------------------------------
-- Audit log (append-only)
-- ---------------------------------------------------------------------------

CREATE TABLE audit_events (
  id             BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  kind           TEXT        NOT NULL,
  user_id        UUID,       -- deliberately no FK: audit rows must outlive the user
  correlation_id TEXT        NOT NULL,
  details        TEXT        NOT NULL
);

-- "Latest N events" is served by the identity PK (ORDER BY id DESC). For time
-- ranges over an append-only log, BRIN is far smaller than a B-tree.
CREATE INDEX brin_audit_at ON audit_events USING brin (at);
CREATE INDEX idx_audit_user ON audit_events (user_id, id DESC) WHERE user_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- Seed: system accounts. IDs must match SystemAccounts in repo/Repositories.scala.
-- ---------------------------------------------------------------------------

INSERT INTO accounts (id, user_id, account_type, name, currency, balance_minor)
VALUES
  ('00000000-0000-0000-0000-000000000001', NULL, 'SYSTEM', 'TOPUP_CLEARING', 'USD', 0),
  ('00000000-0000-0000-0000-000000000002', NULL, 'SYSTEM', 'CASH_CLEARING',  'USD', 0),
  ('00000000-0000-0000-0000-000000000003', NULL, 'SYSTEM', 'LOAN_FUND',      'USD', 100000000000),
  ('00000000-0000-0000-0000-000000000004', NULL, 'SYSTEM', 'ACH_CLEARING',   'USD', 0);
