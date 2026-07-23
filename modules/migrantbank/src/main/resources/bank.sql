CREATE TABLE wallets (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL,
  asset varchar(16) NOT NULL,
  balance_minor bigint NOT NULL,
  version bigint NOT NULL,
  created_at timestamptz NOT NULL,
  UNIQUE(user_id, asset)
);

CREATE TABLE ledger_txs (
  id uuid PRIMARY KEY,
  user_id uuid NOT NULL,
  kind varchar(32) NOT NULL,
  created_at timestamptz NOT NULL
);

CREATE TABLE ledger_entries (
  id uuid PRIMARY KEY,
  tx_id uuid REFERENCES ledger_txs(id),
  wallet_id uuid REFERENCES wallets(id),
  asset varchar(16),
  delta_minor bigint,
  created_at timestamptz
);

CREATE TABLE kiosk_vouchers (
  code varchar(64) PRIMARY KEY,
  asset varchar(16) NOT NULL,
  amount_minor bigint NOT NULL,
  expires_at timestamptz NOT NULL,
  redeemed_by uuid,
  redeemed_at timestamptz
);

CREATE TABLE idempotency_keys (
  user_id uuid NOT NULL,
  idem_key varchar(128) NOT NULL,
  request_hash varchar(128) NOT NULL,
  response_json text NOT NULL,
  created_at timestamptz NOT NULL,
  PRIMARY KEY(user_id, idem_key)
);