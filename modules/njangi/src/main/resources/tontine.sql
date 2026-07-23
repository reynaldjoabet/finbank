-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. The Members Table (Global Users)
CREATE TABLE member (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    phone_number VARCHAR(20) UNIQUE NOT NULL, -- Crucial for MoMo API
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Index for fast lookups during MoMo webhooks
CREATE INDEX idx_member_phone ON member(phone_number);

-- 2. The Circles Table (The Tontine Groups)
CREATE TABLE tontine_circle (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    contribution_amount DECIMAL(15, 2) NOT NULL, -- Storing CFA amounts safely
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. The Junction Table (Many-to-Many: Members in Circles)
CREATE TABLE circle_member (
    circle_id UUID REFERENCES tontine_circle(id) ON DELETE CASCADE,
    member_id UUID REFERENCES member(id) ON DELETE CASCADE,
    joined_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (circle_id, member_id)
);

-- 4. The Cycles Table (The Weekly/Monthly Rounds)
CREATE TABLE payment_cycle (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    circle_id UUID REFERENCES tontine_circle(id) ON DELETE CASCADE,
    winner_id UUID REFERENCES member(id),
    round_number INT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'Pending', -- 'Pending', 'Collecting', 'Disbursed'
    scheduled_date TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (circle_id, round_number) -- Prevents duplicate rounds in the same circle
);

-- 5. The Contributions Table (Audit Trail of every MoMo transaction)
CREATE TABLE contribution (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    cycle_id UUID REFERENCES payment_cycle(id) ON DELETE CASCADE,
    member_id UUID REFERENCES member(id),
    amount DECIMAL(15, 2) NOT NULL,
    momo_transaction_id VARCHAR(255) UNIQUE, -- From the Orange/MTN webhook
    status VARCHAR(50) NOT NULL DEFAULT 'Pending', -- 'Pending', 'Success', 'Failed'
    processed_at TIMESTAMP WITH TIME ZONE
);


create index idx_contribution_member on contribution(member_id);

create index idx_contribution_cycle on contribution(cycle_id);

-- 6. The Disbursements Table (Tracking Payouts to Winners)
CREATE TABLE disbursement (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    cycle_id UUID REFERENCES payment_cycle(id) ON DELETE CASCADE,
    winner_id UUID REFERENCES member(id),
    amount DECIMAL(15, 2) NOT NULL,
    momo_transaction_id VARCHAR(255) UNIQUE, -- From the Orange/MTN webhook
    status VARCHAR(50) NOT NULL DEFAULT 'Pending', -- 'Pending', 'Success', 'Failed'
    processed_at TIMESTAMP WITH TIME ZONE
);

create index idx_disbursement_winner on disbursement(winner_id);
create index idx_disbursement_cycle on disbursement(cycle_id);

-- 7. The Admins Table (For Managing the System)
CREATE TABLE admin (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL, -- Store hashed passwords securely
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);



