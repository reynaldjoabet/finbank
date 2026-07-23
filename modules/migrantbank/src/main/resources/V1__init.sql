CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
  id              UUID PRIMARY KEY,
  phone           TEXT UNIQUE NOT NULL,
  first_name      TEXT NOT NULL,
  last_name       TEXT NOT NULL,
  date_of_birth   TEXT NOT NULL,
  address         TEXT NOT NULL,
  ssn_enc         TEXT NOT NULL,
  ssn_last4       TEXT NOT NULL,
  kyc_status      TEXT NOT NULL,
  password_hash   TEXT,
  role            TEXT NOT NULL DEFAULT 'user',
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_users_kyc_status ON users(kyc_status);

CREATE TABLE IF NOT EXISTS sms_codes (
  user_id     UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  code_hash   TEXT NOT NULL,
  expires_at  TIMESTAMPTZ NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS accounts (
  id            UUID PRIMARY KEY,
  user_id       UUID REFERENCES users(id) ON DELETE SET NULL,
  account_type  TEXT NOT NULL,
  name          TEXT NOT NULL,
  currency      TEXT NOT NULL,
  balance_minor BIGINT NOT NULL DEFAULT 0,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_user_account ON accounts(user_id)
  WHERE user_id IS NOT NULL AND account_type = 'USER';

CREATE TABLE IF NOT EXISTS cards (
  id              UUID PRIMARY KEY,
  user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  kind            TEXT NOT NULL,
  last4           TEXT NOT NULL,
  status          TEXT NOT NULL,
  delivery_status TEXT NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_cards_user ON cards(user_id);

CREATE TABLE IF NOT EXISTS transfers (
  id              UUID PRIMARY KEY,
  transfer_type   TEXT NOT NULL,
  from_user_id    UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  to_user_id      UUID REFERENCES users(id) ON DELETE RESTRICT,
  ach_destination TEXT,
  amount_minor    BIGINT NOT NULL,
  currency        TEXT NOT NULL,
  note            TEXT,
  status          TEXT NOT NULL,
  idempotency_key TEXT,
  risk_flag       BOOLEAN NOT NULL DEFAULT FALSE,
  risk_reason     TEXT,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_transfers_from ON transfers(from_user_id);
CREATE INDEX IF NOT EXISTS idx_transfers_to ON transfers(to_user_id);
CREATE INDEX IF NOT EXISTS idx_transfers_created_at ON transfers(created_at);
CREATE INDEX IF NOT EXISTS idx_transfers_risk_flag ON transfers(risk_flag);

CREATE UNIQUE INDEX IF NOT EXISTS uq_transfers_idem
  ON transfers(from_user_id, transfer_type, idempotency_key)
  WHERE idempotency_key IS NOT NULL;

CREATE TABLE IF NOT EXISTS ledger_entries (
  id            UUID PRIMARY KEY,
  account_id    UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
  transfer_id   UUID REFERENCES transfers(id) ON DELETE SET NULL,
  loan_id       UUID,
  entry_type    TEXT NOT NULL,
  amount_minor  BIGINT NOT NULL,
  currency      TEXT NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ledger_account ON ledger_entries(account_id);
CREATE INDEX IF NOT EXISTS idx_ledger_transfer ON ledger_entries(transfer_id);

CREATE TABLE IF NOT EXISTS family_groups (
  id            UUID PRIMARY KEY,
  owner_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS family_members (
  group_id UUID NOT NULL REFERENCES family_groups(id) ON DELETE CASCADE,
  user_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  PRIMARY KEY (group_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_family_owner ON family_groups(owner_user_id);

CREATE TABLE IF NOT EXISTS paycheck_enrollments (
  user_id       UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  employer_name TEXT NOT NULL,
  partner_ref   TEXT NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS loans (
  id              UUID PRIMARY KEY,
  user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  principal_minor BIGINT NOT NULL,
  currency        TEXT NOT NULL,
  fee_minor       BIGINT NOT NULL,
  due_date        DATE NOT NULL,
  status          TEXT NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_loans_user ON loans(user_id);

CREATE TABLE IF NOT EXISTS support_tickets (
  id         UUID PRIMARY KEY,
  user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  message    TEXT NOT NULL,
  status     TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_tickets_user ON support_tickets(user_id);
CREATE INDEX IF NOT EXISTS idx_tickets_status ON support_tickets(status);

CREATE TABLE IF NOT EXISTS audit_events (
  id             BIGSERIAL PRIMARY KEY,
  at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  kind           TEXT NOT NULL,
  user_id        UUID,
  correlation_id TEXT NOT NULL,
  details        TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_at ON audit_events(at);

CREATE TABLE IF NOT EXISTS refresh_tokens (
  token_id    UUID PRIMARY KEY,
  user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash  TEXT NOT NULL,
  expires_at  TIMESTAMPTZ NOT NULL,
  revoked_at  TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_refresh_user ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_expires ON refresh_tokens(expires_at);

INSERT INTO accounts (id, user_id, account_type, name, currency, balance_minor)
VALUES
  ('00000000-0000-0000-0000-000000000001', NULL, 'SYSTEM', 'ACH_CLEARING', 'USD', 0),
  ('00000000-0000-0000-0000-000000000002', NULL, 'SYSTEM', 'LOAN_FUND', 'USD', 100000000000),
  ('00000000-0000-0000-0000-000000000003', NULL, 'SYSTEM', 'TOPUP_CLEARING', 'USD', 0),
  ('00000000-0000-0000-0000-000000000004', NULL, 'SYSTEM', 'CASH_CLEARING', 'USD', 0)
ON CONFLICT (id) DO NOTHING;
