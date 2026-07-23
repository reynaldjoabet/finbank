-- 1. Create the base table
CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    amount DECIMAL(18, 2),
    currency VARCHAR(3),
    status VARCHAR(20),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 2. Enable RLS
ALTER TABLE transactions ENABLE ROW LEVEL SECURITY;

-- 3. Create the Isolation Policy
-- This policy uses a session variable 'app.current_tenant' set by your Scala app
CREATE POLICY tenant_isolation_policy ON transactions
    USING (tenant_id = current_setting('app.current_tenant')::UUID);