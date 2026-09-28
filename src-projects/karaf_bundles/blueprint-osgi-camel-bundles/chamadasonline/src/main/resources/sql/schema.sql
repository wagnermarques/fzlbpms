-- =============================================================================
-- Chamadas Online (fzl-chamadasonline) Database Schema for fzlbpms (fzldb)
-- =============================================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'ChamadasRole') THEN
        CREATE TYPE "ChamadasRole" AS ENUM ('STUDENT', 'STAFF');
    END IF;
END$$;

CREATE TABLE IF NOT EXISTS "ChamadasUser" (
    "id" TEXT PRIMARY KEY,
    "role" "ChamadasRole" NOT NULL,
    "registrationNumber" TEXT UNIQUE,
    "email" TEXT UNIQUE,
    "name" TEXT NOT NULL,
    "pinHash" TEXT DEFAULT '',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS "ChamadasEvent" (
    "id" TEXT PRIMARY KEY,
    "name" TEXT NOT NULL,
    "date" TIMESTAMP(3) NOT NULL,
    "geofenceLat" DOUBLE PRECISION NOT NULL,
    "geofenceLng" DOUBLE PRECISION NOT NULL,
    "geofenceRadiusMeters" DOUBLE PRECISION NOT NULL DEFAULT 150,
    "geofencePolygon" JSONB,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS "ChamadasEventPeriod" (
    "id" TEXT PRIMARY KEY,
    "eventId" TEXT NOT NULL REFERENCES "ChamadasEvent"("id") ON DELETE CASCADE,
    "label" TEXT NOT NULL,
    "startsAt" TIMESTAMP(3) NOT NULL,
    "endsAt" TIMESTAMP(3) NOT NULL
);

CREATE TABLE IF NOT EXISTS "ChamadasCheckinCode" (
    "id" TEXT PRIMARY KEY,
    "eventPeriodId" TEXT NOT NULL REFERENCES "ChamadasEventPeriod"("id") ON DELETE CASCADE,
    "code" TEXT NOT NULL,
    "issuedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "expiresAt" TIMESTAMP(3) NOT NULL
);

CREATE TABLE IF NOT EXISTS "ChamadasDevice" (
    "id" TEXT PRIMARY KEY,
    "clientToken" TEXT UNIQUE NOT NULL,
    "studentId" TEXT REFERENCES "ChamadasUser"("id") ON DELETE SET NULL,
    "fingerprintHash" TEXT,
    "firstSeenAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "lastSeenAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "verified" BOOLEAN NOT NULL DEFAULT false,
    "verifiedAt" TIMESTAMP(3),
    "verifiedByStaffId" TEXT REFERENCES "ChamadasUser"("id") ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS "ChamadasDeviceEnrollmentCode" (
    "id" TEXT PRIMARY KEY,
    "studentId" TEXT NOT NULL REFERENCES "ChamadasUser"("id") ON DELETE CASCADE,
    "code" TEXT NOT NULL,
    "issuedByStaffId" TEXT NOT NULL REFERENCES "ChamadasUser"("id"),
    "issuedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "expiresAt" TIMESTAMP(3) NOT NULL,
    "usedAt" TIMESTAMP(3)
);

CREATE TABLE IF NOT EXISTS "ChamadasCheckin" (
    "id" TEXT PRIMARY KEY,
    "studentId" TEXT NOT NULL REFERENCES "ChamadasUser"("id") ON DELETE CASCADE,
    "eventPeriodId" TEXT NOT NULL REFERENCES "ChamadasEventPeriod"("id") ON DELETE CASCADE,
    "deviceId" TEXT NOT NULL REFERENCES "ChamadasDevice"("id"),
    "lat" DOUBLE PRECISION NOT NULL,
    "lng" DOUBLE PRECISION NOT NULL,
    "distanceMeters" DOUBLE PRECISION NOT NULL,
    "flagged" BOOLEAN NOT NULL DEFAULT false,
    "flagReasons" TEXT[] DEFAULT ARRAY[]::TEXT[],
    "reviewed" BOOLEAN NOT NULL DEFAULT false,
    "reviewDecision" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "ChamadasCheckin_unique" UNIQUE ("studentId", "eventPeriodId")
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS "idx_chamadas_checkin_event_period" ON "ChamadasCheckin"("eventPeriodId");
CREATE INDEX IF NOT EXISTS "idx_chamadas_checkin_student" ON "ChamadasCheckin"("studentId");
CREATE INDEX IF NOT EXISTS "idx_chamadas_device_client_token" ON "ChamadasDevice"("clientToken");
CREATE INDEX IF NOT EXISTS "idx_chamadas_device_student" ON "ChamadasDevice"("studentId");
CREATE INDEX IF NOT EXISTS "idx_chamadas_checkin_code_period" ON "ChamadasCheckinCode"("eventPeriodId", "expiresAt");
