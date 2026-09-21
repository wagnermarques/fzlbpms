-- Applied manually against the `chamadasonline` database (owner:
-- `chamadasonline` role) in fzl-postgresql. Kept here for reference/
-- reproducibility — the bundle does not run migrations itself yet.
-- Ported 1:1 from fzl-chamadasonline's apps/api/prisma/schema.prisma
-- (the two migrations under apps/api/prisma/migrations/ are already
-- folded into this single definition).

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role                  VARCHAR(10) NOT NULL CHECK (role IN ('STUDENT', 'STAFF')),
    registration_number   VARCHAR(64) UNIQUE,
    email                 VARCHAR(255) UNIQUE,
    name                  VARCHAR(255) NOT NULL,
    pin_hash              TEXT NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX users_role_idx ON users (role);

CREATE TABLE events (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                     VARCHAR(255) NOT NULL,
    date                     TIMESTAMPTZ NOT NULL,
    geofence_lat             DOUBLE PRECISION NOT NULL,
    geofence_lng             DOUBLE PRECISION NOT NULL,
    geofence_radius_meters   DOUBLE PRECISION NOT NULL DEFAULT 150,
    -- JSON array of {lat,lng}, same shape as Prisma's Json? geofencePolygon.
    geofence_polygon         JSONB,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE event_periods (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id    UUID NOT NULL REFERENCES events (id),
    label       VARCHAR(255) NOT NULL,
    starts_at   TIMESTAMPTZ NOT NULL,
    ends_at     TIMESTAMPTZ NOT NULL
);
CREATE INDEX event_periods_event_id_idx ON event_periods (event_id);

CREATE TABLE checkin_codes (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_period_id  UUID NOT NULL REFERENCES event_periods (id),
    code             VARCHAR(12) NOT NULL,
    issued_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at       TIMESTAMPTZ NOT NULL
);
CREATE INDEX checkin_codes_period_expires_idx ON checkin_codes (event_period_id, expires_at);

CREATE TABLE devices (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_token           UUID NOT NULL UNIQUE,
    student_id             UUID REFERENCES users (id),
    fingerprint_hash       TEXT,
    first_seen_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    verified               BOOLEAN NOT NULL DEFAULT false,
    verified_at            TIMESTAMPTZ,
    verified_by_staff_id   UUID REFERENCES users (id)
);
CREATE INDEX devices_fingerprint_hash_idx ON devices (fingerprint_hash);
CREATE INDEX devices_student_verified_idx ON devices (student_id, verified);

CREATE TABLE device_enrollment_codes (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id         UUID NOT NULL REFERENCES users (id),
    code               VARCHAR(10) NOT NULL,
    issued_by_staff_id UUID NOT NULL REFERENCES users (id),
    issued_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at         TIMESTAMPTZ NOT NULL,
    used_at            TIMESTAMPTZ
);
CREATE INDEX device_enrollment_codes_student_expires_idx ON device_enrollment_codes (student_id, expires_at);

CREATE TABLE checkins (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id        UUID NOT NULL REFERENCES users (id),
    event_period_id   UUID NOT NULL REFERENCES event_periods (id),
    device_id         UUID NOT NULL REFERENCES devices (id),
    lat               DOUBLE PRECISION NOT NULL,
    lng               DOUBLE PRECISION NOT NULL,
    distance_meters   DOUBLE PRECISION NOT NULL,
    flagged           BOOLEAN NOT NULL DEFAULT false,
    flag_reasons      TEXT[] NOT NULL DEFAULT '{}',
    reviewed          BOOLEAN NOT NULL DEFAULT false,
    review_decision   VARCHAR(20),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (student_id, event_period_id)
);
CREATE INDEX checkins_event_period_id_idx ON checkins (event_period_id);
CREATE INDEX checkins_flagged_idx ON checkins (flagged);

-- Seed: one staff account so /auth/login has something to authenticate
-- against before real student/staff data is imported. PIN below is
-- 'staff123' hashed with jBCrypt (fzlbpms.chamadasonline.PasswordUtil) —
-- change it before this goes anywhere near production.
-- INSERT INTO users (role, email, name, pin_hash)
--   VALUES ('STAFF', 'staff@escola.test', 'Staff', '<bcrypt hash here>');
