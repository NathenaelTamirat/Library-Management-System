CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'user_role') THEN
        CREATE TYPE user_role AS ENUM ('MEMBER', 'LIBRARIAN', 'ADMIN');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'loan_status') THEN
        CREATE TYPE loan_status AS ENUM ('ACTIVE', 'RETURNED', 'OVERDUE', 'LOST');
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role user_role NOT NULL DEFAULT 'MEMBER',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE users ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_login_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS books (
    isbn VARCHAR(20) PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    author VARCHAR(300) NOT NULL,
    total_copies INTEGER NOT NULL CHECK (total_copies >= 0),
    available_copies INTEGER NOT NULL,
    genre VARCHAR(100),
    publication_year INTEGER CHECK (
        publication_year IS NULL OR (publication_year BETWEEN 0 AND 9999)),
    publisher TEXT,
    subject TEXT,
    CHECK (available_copies >= 0 AND available_copies <= total_copies)
);

ALTER TABLE books ADD COLUMN IF NOT EXISTS genre VARCHAR(100);
ALTER TABLE books ADD COLUMN IF NOT EXISTS publication_year INTEGER;
ALTER TABLE books ADD COLUMN IF NOT EXISTS publisher TEXT;
ALTER TABLE books ADD COLUMN IF NOT EXISTS subject TEXT;

DROP INDEX IF EXISTS books_search_idx;
ALTER TABLE books DROP COLUMN IF EXISTS search_document;
ALTER TABLE books ADD COLUMN search_document TSVECTOR GENERATED ALWAYS AS (
    to_tsvector(
        'english',
        coalesce(title, '') || ' ' || coalesce(author, '') || ' ' || coalesce(genre, '')
            || ' ' || coalesce(publication_year::text, '')
            || ' ' || coalesce(publisher, '') || ' ' || coalesce(subject, ''))
) STORED;

CREATE INDEX IF NOT EXISTS books_search_idx ON books USING GIN (search_document);

CREATE TABLE IF NOT EXISTS loans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    isbn VARCHAR(20) NOT NULL REFERENCES books(isbn),
    checkout_date DATE NOT NULL DEFAULT CURRENT_DATE,
    due_date DATE NOT NULL,
    return_date DATE,
    status loan_status NOT NULL DEFAULT 'ACTIVE',
    renewal_count INTEGER NOT NULL DEFAULT 0 CHECK (renewal_count >= 0),
    CHECK (due_date >= checkout_date),
    CHECK (return_date IS NULL OR return_date >= checkout_date)
);

ALTER TABLE loans ADD COLUMN IF NOT EXISTS renewal_count INTEGER NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX IF NOT EXISTS one_active_loan_per_member_book
    ON loans (user_id, isbn)
    WHERE status IN ('ACTIVE', 'OVERDUE');

CREATE TABLE IF NOT EXISTS fines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    loan_id UUID NOT NULL UNIQUE REFERENCES loans(id),
    amount NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),
    amount_paid NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (amount_paid >= 0),
    paid_status BOOLEAN NOT NULL DEFAULT FALSE,
    waived BOOLEAN NOT NULL DEFAULT FALSE,
    issued_date DATE NOT NULL DEFAULT CURRENT_DATE,
    CHECK (amount_paid <= amount)
);

ALTER TABLE fines ADD COLUMN IF NOT EXISTS waived BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE fines ADD COLUMN IF NOT EXISTS amount_paid NUMERIC(12, 2) NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS fine_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fine_id UUID NOT NULL REFERENCES fines(id),
    actor_id UUID NOT NULL REFERENCES users(id),
    event_type VARCHAR(20) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (amount >= 0),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS fine_events_fine_id_idx ON fine_events (fine_id, occurred_at);

CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    kind VARCHAR(30) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS notifications_pending_idx
    ON notifications (created_at)
    WHERE sent_at IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'hold_status') THEN
        CREATE TYPE hold_status AS ENUM ('WAITING', 'READY', 'FULFILLED', 'CANCELLED', 'EXPIRED');
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS holds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    isbn VARCHAR(20) NOT NULL REFERENCES books(isbn),
    status hold_status NOT NULL DEFAULT 'WAITING',
    placed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS one_active_hold_per_member_book
    ON holds (user_id, isbn)
    WHERE status IN ('WAITING', 'READY');

CREATE INDEX IF NOT EXISTS holds_isbn_waiting_idx
    ON holds (isbn, placed_at)
    WHERE status IN ('WAITING', 'READY');

CREATE TABLE IF NOT EXISTS library_policy (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    loan_days INTEGER NOT NULL CHECK (loan_days >= 1),
    daily_fine NUMERIC(12, 2) NOT NULL CHECK (daily_fine >= 0),
    replacement_fine NUMERIC(12, 2) NOT NULL CHECK (replacement_fine >= 0),
    max_renewals INTEGER NOT NULL CHECK (max_renewals >= 0),
    borrow_limit INTEGER NOT NULL CHECK (borrow_limit >= 1)
);

INSERT INTO library_policy (id, loan_days, daily_fine, replacement_fine, max_renewals, borrow_limit)
VALUES (1, 14, 0.50, 50.00, 2, 5)
ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    action TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ip_address INET,
    details JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE OR REPLACE FUNCTION record_write_audit() RETURNS TRIGGER AS $$
DECLARE
    actor UUID;
BEGIN
    actor := NULLIF(current_setting('app.user_id', true), '')::UUID;
    INSERT INTO audit_log (user_id, action, ip_address, details)
    VALUES (
        actor,
        TG_OP || ' ' || TG_TABLE_NAME,
        inet_client_addr(),
        jsonb_build_object('table', TG_TABLE_NAME)
    );
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE
    table_name TEXT;
BEGIN
    FOREACH table_name IN ARRAY ARRAY['users', 'books', 'loans', 'fines']
    LOOP
        EXECUTE format('DROP TRIGGER IF EXISTS audit_writes ON %I', table_name);
        EXECUTE format(
            'CREATE TRIGGER audit_writes AFTER INSERT OR UPDATE OR DELETE ON %I '
            'FOR EACH ROW EXECUTE FUNCTION record_write_audit()',
            table_name
        );
    END LOOP;
END $$;
