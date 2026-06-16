-- ============================================================
--  PG CRM — Initial Baseline Schema (V1)
-- ============================================================

-- users table
CREATE TABLE users (
    id VARCHAR(255) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    branch_id VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    first_login BOOLEAN NOT NULL DEFAULT TRUE,
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    full_name VARCHAR(255),
    phone VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
CREATE INDEX idx_user_email ON users(email);

-- buildings table
CREATE TABLE buildings (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(255),
    created_at TIMESTAMP
);

-- floors table
CREATE TABLE floors (
    id VARCHAR(255) PRIMARY KEY,
    building_id VARCHAR(255) NOT NULL REFERENCES buildings(id),
    floor_number INTEGER NOT NULL,
    floor_label VARCHAR(255)
);

-- blocks table
CREATE TABLE blocks (
    id VARCHAR(255) PRIMARY KEY,
    floor_id VARCHAR(255) NOT NULL REFERENCES floors(id),
    name VARCHAR(255) NOT NULL
);

-- rooms table
CREATE TABLE rooms (
    id VARCHAR(255) PRIMARY KEY,
    block_id VARCHAR(255) REFERENCES blocks(id),
    floor_id VARCHAR(255) NOT NULL REFERENCES floors(id),
    room_number VARCHAR(255) NOT NULL,
    sharing_type INTEGER NOT NULL,
    base_rent NUMERIC(10, 2) NOT NULL
);

-- beds table
CREATE TABLE beds (
    id VARCHAR(255) PRIMARY KEY,
    room_id VARCHAR(255) NOT NULL REFERENCES rooms(id),
    bed_label VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL DEFAULT 'VACANT'
);

-- guests table
CREATE TABLE guests (
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL UNIQUE REFERENCES users(id),
    bed_id VARCHAR(255) REFERENCES beds(id),
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(255) NOT NULL,
    whatsapp_number VARCHAR(255),
    vehicle_registration VARCHAR(20),
    kyc_status VARCHAR(255) DEFAULT 'PENDING',
    check_in_date DATE,
    expected_check_out_date DATE,
    notice_date DATE,
    exit_date DATE,
    actual_check_out_date DATE,
    advance_deposit NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    breakfast_preference BOOLEAN NOT NULL DEFAULT FALSE,
    lunch_preference BOOLEAN NOT NULL DEFAULT FALSE,
    dinner_preference BOOLEAN NOT NULL DEFAULT FALSE,
    is_veg_preference BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP
);

-- invoices table
CREATE TABLE invoices (
    id VARCHAR(255) PRIMARY KEY,
    guest_id VARCHAR(255) NOT NULL REFERENCES guests(id),
    invoice_month INTEGER NOT NULL,
    invoice_year INTEGER NOT NULL,
    total_amount NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    status VARCHAR(255) NOT NULL DEFAULT 'GENERATED',
    due_date DATE,
    generated_at TIMESTAMP,
    paid_at TIMESTAMP,
    razorpay_order_id VARCHAR(255),
    razorpay_payment_id VARCHAR(255),
    payment_method VARCHAR(255),
    reminder_sent_at TIMESTAMP
);

-- invoice_line_items table
CREATE TABLE invoice_line_items (
    id VARCHAR(255) PRIMARY KEY,
    invoice_id VARCHAR(255) NOT NULL REFERENCES invoices(id),
    type VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    amount NUMERIC(10, 2) NOT NULL
);

-- eb_bills table
CREATE TABLE eb_bills (
    id VARCHAR(255) PRIMARY KEY,
    block_id VARCHAR(255) NOT NULL REFERENCES blocks(id),
    billing_period_start DATE NOT NULL,
    billing_period_end DATE NOT NULL,
    total_amount NUMERIC(10, 2) NOT NULL,
    rate_per_unit NUMERIC(10, 4),
    split_method VARCHAR(30),
    created_at TIMESTAMP
);

-- eb_bill_guests table
CREATE TABLE eb_bill_guests (
    id VARCHAR(255) PRIMARY KEY,
    eb_bill_id VARCHAR(255) NOT NULL REFERENCES eb_bills(id),
    guest_id VARCHAR(255) NOT NULL REFERENCES guests(id),
    share_amount NUMERIC(10, 2) NOT NULL,
    previous_reading NUMERIC(10, 2),
    current_reading NUMERIC(10, 2),
    units_consumed NUMERIC(10, 2)
);

-- daily_logs table
CREATE TABLE daily_logs (
    id VARCHAR(255) PRIMARY KEY,
    guest_id VARCHAR(255) NOT NULL REFERENCES guests(id),
    log_date DATE NOT NULL,
    breakfast_opted BOOLEAN NOT NULL DEFAULT FALSE,
    lunch_opted BOOLEAN NOT NULL DEFAULT FALSE,
    dinner_opted BOOLEAN NOT NULL DEFAULT FALSE,
    is_veg BOOLEAN NOT NULL DEFAULT TRUE,
    omelette_count INTEGER NOT NULL DEFAULT 0,
    boiled_egg_count INTEGER NOT NULL DEFAULT 0,
    washing_machine_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
CREATE INDEX idx_daily_log_guest_date ON daily_logs(guest_id, log_date);

-- audit_logs table
CREATE TABLE audit_logs (
    id VARCHAR(255) PRIMARY KEY,
    actor_id VARCHAR(255),
    actor_role VARCHAR(30),
    action VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50),
    entity_id VARCHAR(255),
    description VARCHAR(500) NOT NULL,
    metadata VARCHAR(2000),
    timestamp TIMESTAMP NOT NULL
);
CREATE INDEX idx_audit_time ON audit_logs(timestamp);
CREATE INDEX idx_audit_action ON audit_logs(action);

-- maintenance_tickets table
CREATE TABLE maintenance_tickets (
    id VARCHAR(255) PRIMARY KEY,
    raised_by_guest_id VARCHAR(255) REFERENCES guests(id),
    building_id VARCHAR(255),
    location VARCHAR(255) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    status VARCHAR(255) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(255) NOT NULL DEFAULT 'MEDIUM',
    created_at TIMESTAMP,
    resolved_at TIMESTAMP
);

-- notifications table
CREATE TABLE notifications (
    id VARCHAR(255) PRIMARY KEY,
    guest_id VARCHAR(255) REFERENCES guests(id),
    channel VARCHAR(255) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    sent_at TIMESTAMP,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    delivery_status VARCHAR(255)
);

-- pricing_config table
CREATE TABLE pricing_config (
    id VARCHAR(255) PRIMARY KEY,
    building_id VARCHAR(255) NOT NULL,
    price_key VARCHAR(50) NOT NULL,
    price_value NUMERIC(10, 2) NOT NULL,
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    CONSTRAINT unique_building_price_key UNIQUE (building_id, price_key)
);
