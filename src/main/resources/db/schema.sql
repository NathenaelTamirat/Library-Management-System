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
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS books (
    isbn VARCHAR(20) PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    author VARCHAR(300) NOT NULL,
    total_copies INTEGER NOT NULL CHECK (total_copies >= 0),
    available_copies INTEGER NOT NULL,
    search_document TSVECTOR GENERATED ALWAYS AS (
        to_tsvector('english', coalesce(title, '') || ' ' || coalesce(author, ''))
    ) STORED,
    CHECK (available_copies >= 0 AND available_copies <= total_copies)
);

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
    paid_status BOOLEAN NOT NULL DEFAULT FALSE,
    issued_date DATE NOT NULL DEFAULT CURRENT_DATE
);

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
