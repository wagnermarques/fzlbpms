-- =============================================================================
-- BPMN Drawer Database Schema (PostgreSQL / fzldb)
-- Applied automatically on bundle start or on first connection
-- =============================================================================

CREATE TABLE IF NOT EXISTS bpmndrawer_diagrams (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    process_key VARCHAR(255),
    xml TEXT NOT NULL,
    version INT NOT NULL DEFAULT 1,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_bpmndrawer_diagrams_updated_at ON bpmndrawer_diagrams(updated_at DESC);
