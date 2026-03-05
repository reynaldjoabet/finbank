CREATE TABLE payments (
  tenant_id         TEXT        NOT NULL,
  payment_id        UUID        NOT NULL,
  idempotency_key   TEXT        NOT NULL,
  user_id           TEXT        NOT NULL,

  amount_minor      BIGINT      NOT NULL,
  currency          TEXT        NOT NULL,

  rail              TEXT        NOT NULL, -- e.g. MTN_MOMO / ORANGE_MONEY / BANK_TRANSFER
  destination_type  TEXT        NOT NULL, -- MOBILE_MONEY / BANK_ACCOUNT
  destination_msisdn TEXT,
  destination_iban   TEXT,
  destination_name   TEXT,

  status            TEXT        NOT NULL, -- PENDING/SUBMITTED/COMPLETED/FAILED
  provider_ref      TEXT,
  description       TEXT,

  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

  PRIMARY KEY (tenant_id, payment_id),
  UNIQUE (tenant_id, idempotency_key)
);

CREATE INDEX payments_tenant_user_idx ON payments (tenant_id, user_id);
CREATE INDEX payments_status_idx ON payments (tenant_id, status);