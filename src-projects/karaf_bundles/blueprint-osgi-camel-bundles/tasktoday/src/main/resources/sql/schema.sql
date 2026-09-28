-- =============================================================================
-- Task Today App (fzl-tasktodayapp) Database Schema for fzlbpms (fzldb)
--
-- Idempotent: SchemaInitializer runs this whole script on bundle start (and
-- again on first use if PostgreSQL was not up yet), so every statement must
-- be safe to repeat.
-- =============================================================================

CREATE TABLE IF NOT EXISTS tasktoday_categories (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64),
    name VARCHAR(120) NOT NULL,
    color VARCHAR(32) DEFAULT '#6750a4',
    icon VARCHAR(64) DEFAULT 'label',
    is_native BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tasktoday_tasks (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64),
    category_id VARCHAR(64) REFERENCES tasktoday_categories(id) ON DELETE SET NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    priority VARCHAR(32) DEFAULT 'MEDIA',     -- 'BAIXA', 'MEDIA', 'ALTA', 'URGENTE'
    status VARCHAR(32) DEFAULT 'PENDENTE',    -- 'PENDENTE', 'EM_ANDAMENTO', 'CONCLUIDA'
    deadline TIMESTAMP WITH TIME ZONE,
    alert_type VARCHAR(32) DEFAULT 'sound',   -- 'sound', 'notification', 'none'
    trigger_minutes INTEGER DEFAULT 15,
    is_archived BOOLEAN DEFAULT FALSE,
    completed_at TIMESTAMP WITH TIME ZONE,
    alarm_fired BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tasktoday_push_subscriptions (
    id VARCHAR(64) PRIMARY KEY,               -- hex SHA-256 of endpoint (one row per browser)
    user_id VARCHAR(64),
    endpoint TEXT NOT NULL,
    p256dh TEXT NOT NULL,
    auth TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tasks_user_deadline ON tasktoday_tasks (user_id, deadline);
CREATE INDEX IF NOT EXISTS idx_tasks_status ON tasktoday_tasks (status);
CREATE INDEX IF NOT EXISTS idx_push_subscriptions_user ON tasktoday_push_subscriptions (user_id);

-- VAPID key pair (single row, id = 1). Generated on first start when
-- TASKTODAY_VAPID_PUBLIC_KEY / TASKTODAY_VAPID_PRIVATE_KEY are not set, and
-- kept here so browser subscriptions survive container restarts: a push
-- subscription is bound to the public key it was created with.
CREATE TABLE IF NOT EXISTS tasktoday_vapid_keys (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    public_key TEXT NOT NULL,                 -- base64url, uncompressed P-256 point (65 bytes)
    private_key TEXT NOT NULL,                -- base64url, raw scalar (32 bytes)
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Native categories (RF02) — same ids the PWA seeds offline (src/services/storage.js),
-- shared by every user (user_id NULL) and never deletable through the API.
INSERT INTO tasktoday_categories (id, user_id, name, color, icon, is_native) VALUES
    ('cat-cursos',     NULL, 'Cursos',             '#1976d2', 'school',   TRUE),
    ('cat-cotidianas', NULL, 'Tarefas Cotidianas', '#388e3c', 'routine',  TRUE),
    ('cat-financeira', NULL, 'Financeira',         '#f57c00', 'payments', TRUE),
    ('cat-pessoais',   NULL, 'Pessoais',           '#7b1fa2', 'person',   TRUE)
ON CONFLICT (id) DO NOTHING;
