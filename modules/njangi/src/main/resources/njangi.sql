CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Enable RLS for all subsequent tables
ALTER TABLE tenants ENABLE ROW LEVEL SECURITY;

-- 1. Members (The People)
CREATE TABLE members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    full_name VARCHAR(255) NOT NULL,
    momo_number VARCHAR(20) NOT NULL,
    trust_score INT DEFAULT 500, -- The "Social Capital" metric
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 2. Tontine Circles (The Groups)
CREATE TABLE tontine_circles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    name VARCHAR(255) NOT NULL,
    contribution_amount DECIMAL(18, 2) NOT NULL,
    cycle_frequency_days INT NOT NULL,
    status VARCHAR(50) DEFAULT 'active',
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 3. Circle Members (Junction Table)
CREATE TABLE circle_members (
    tontine_circle_id UUID REFERENCES tontine_circles(id),
    member_id UUID REFERENCES members(id),
    payout_position INT, -- The "turn" in the rotation
    joined_at TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (tontine_circle_id, member_id)
);

-- 4. Contributions (The Ledger)
CREATE TABLE contributions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    tontine_circle_id UUID REFERENCES tontine_circles(id),
    member_id UUID REFERENCES members(id),
    amount DECIMAL(18, 2) NOT NULL,
    transaction_reference VARCHAR(255), -- External MoMo ID
    status VARCHAR(50) NOT NULL, -- 'pending', 'confirmed', 'failed'
    paid_at TIMESTAMPTZ
);

-- Apply this to members, tontine_circles, and contributions
CREATE POLICY tenant_isolation_policy ON contributions
    USING (tenant_id = current_setting('app.current_tenant')::UUID);

ALTER TABLE contributions ENABLE ROW LEVEL SECURITY;

-- 1. Identity & Trust
CREATE TABLE users (
    id UUID PRIMARY KEY,
    fapi_subject_id VARCHAR(255) UNIQUE NOT NULL,
    global_trust_score INT DEFAULT 500,
    kyc_level INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 2. Circles & Vaults
CREATE TABLE njangi_circles (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    contribution_amount DECIMAL(18, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    rules JSONB, -- Flexible storage for auction rules, late fees, etc.
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE vaults (
    id UUID PRIMARY KEY,
    njangi_circle_id UUID REFERENCES njangi_circles(id),
    provider VARCHAR(50) NOT NULL, -- 'BANK', 'STABLECOIN', 'PAPSS'
    balance DECIMAL(18, 6) DEFAULT 0,
    rail_address TEXT,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 3. Advanced Ledger
CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    vault_id UUID REFERENCES vaults(id),
    amount DECIMAL(18, 2) NOT NULL,
    payment_method VARCHAR(50) NOT NULL, -- 'MOMO', 'BANK', 'CRYPTO'
    settlement_rail VARCHAR(50) NOT NULL, -- 'GIMAC', 'PAPSS', 'USDC'
    status VARCHAR(50) NOT NULL,
    idempotency_key UUID UNIQUE NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 4. Bidding & Auctions
CREATE TABLE auctions (
    id UUID PRIMARY KEY,
    njangi_circle_id UUID REFERENCES njangi_circles(id),
    pot_amount DECIMAL(18, 2) NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ
);