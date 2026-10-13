-- =============================================================================
-- Evasão Escolar Database Schema (PostgreSQL / fzldb)
-- Applied automatically on bundle start or on first connection
-- =============================================================================

CREATE TABLE IF NOT EXISTS evasao_classes (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    course VARCHAR(255) NOT NULL,
    year INT NOT NULL,
    period VARCHAR(32) NOT NULL, -- MATUTINO, VESPERTINO, NOTURNO, INTEGRAL
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS evasao_students (
    id VARCHAR(64) PRIMARY KEY,
    registration_number VARCHAR(64) UNIQUE NOT NULL, -- RM / Matrícula
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(64),
    class_id VARCHAR(64) REFERENCES evasao_classes(id) ON DELETE SET NULL,
    status VARCHAR(32) DEFAULT 'ATIVO' CHECK (status IN ('ATIVO', 'EM_RISCO', 'EVADIDO', 'TRANSFERIDO', 'CONCLUIDO')),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_evasao_students_class ON evasao_students(class_id);
CREATE INDEX IF NOT EXISTS idx_evasao_students_status ON evasao_students(status);
CREATE INDEX IF NOT EXISTS idx_evasao_students_rm ON evasao_students(registration_number);

CREATE TABLE IF NOT EXISTS evasao_attendance (
    id VARCHAR(64) PRIMARY KEY,
    student_id VARCHAR(64) UNIQUE NOT NULL REFERENCES evasao_students(id) ON DELETE CASCADE,
    total_classes INT DEFAULT 0,
    attended_classes INT DEFAULT 0,
    absences INT DEFAULT 0,
    consecutive_absences INT DEFAULT 0,
    attendance_rate NUMERIC(5,2) DEFAULT 100.00,
    last_absence_date DATE,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS evasao_grades (
    id VARCHAR(64) PRIMARY KEY,
    student_id VARCHAR(64) NOT NULL REFERENCES evasao_students(id) ON DELETE CASCADE,
    subject VARCHAR(128) NOT NULL,
    term VARCHAR(32) NOT NULL, -- 1_BIMESTRE, 2_BIMESTRE, etc.
    grade NUMERIC(4,2) NOT NULL,
    failing BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_evasao_grades_student ON evasao_grades(student_id);

CREATE TABLE IF NOT EXISTS evasao_risk_evaluations (
    id VARCHAR(64) PRIMARY KEY,
    student_id VARCHAR(64) UNIQUE NOT NULL REFERENCES evasao_students(id) ON DELETE CASCADE,
    risk_level VARCHAR(32) NOT NULL CHECK (risk_level IN ('BAIXO', 'MEDIO', 'ALTO', 'CRITICO')),
    risk_score INT NOT NULL,
    reasons TEXT NOT NULL, -- JSON array of string reasons
    evaluated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_evasao_risk_level ON evasao_risk_evaluations(risk_level);

CREATE TABLE IF NOT EXISTS evasao_interventions (
    id VARCHAR(64) PRIMARY KEY,
    student_id VARCHAR(64) NOT NULL REFERENCES evasao_students(id) ON DELETE CASCADE,
    type VARCHAR(64) NOT NULL CHECK (type IN ('CONTATO_RESPONSAVEIS', 'REUNIAO_PEDAGOGICA', 'REFORCO_ESCOLAR', 'VISITA_DOMICILIAR', 'ENCAMINHAMENTO_CONSELHO_TUTELAR', 'OUTRO')),
    description TEXT NOT NULL,
    status VARCHAR(32) DEFAULT 'PLANEJADA' CHECK (status IN ('PLANEJADA', 'EM_ANDAMENTO', 'CONCLUIDA', 'CANCELADA')),
    assigned_to VARCHAR(255),
    outcome TEXT,
    scheduled_for TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_evasao_interventions_student ON evasao_interventions(student_id);
CREATE INDEX IF NOT EXISTS idx_evasao_interventions_status ON evasao_interventions(status);

-- Seed initial test class and students if empty
INSERT INTO evasao_classes (id, name, course, year, period)
VALUES ('turma-1a-ds-2026', '1º Ano A - Informática', 'Desenvolvimento de Sistemas', 2026, 'MATUTINO')
ON CONFLICT (id) DO NOTHING;

INSERT INTO evasao_classes (id, name, course, year, period)
VALUES ('turma-2a-ds-2026', '2º Ano A - Informática', 'Desenvolvimento de Sistemas', 2026, 'VESPERTINO')
ON CONFLICT (id) DO NOTHING;

INSERT INTO evasao_students (id, registration_number, name, email, phone, class_id, status)
VALUES
  ('aluno-1001', 'RM1001', 'Lucas Silva Santos', 'lucas.silva@escola.local', '(11) 98765-4321', 'turma-1a-ds-2026', 'ATIVO'),
  ('aluno-1002', 'RM1002', 'Beatriz Oliveira Ramos', 'beatriz.ramos@escola.local', '(11) 97654-3210', 'turma-1a-ds-2026', 'EM_RISCO'),
  ('aluno-1003', 'RM1003', 'Carlos Eduardo Moreira', 'carlos.moreira@escola.local', '(11) 96543-2109', 'turma-1a-ds-2026', 'EM_RISCO'),
  ('aluno-1004', 'RM1004', 'Mariana Costa Ferreira', 'mariana.costa@escola.local', '(11) 95432-1098', 'turma-2a-ds-2026', 'ATIVO')
ON CONFLICT (id) DO NOTHING;

-- Initial attendance seed
INSERT INTO evasao_attendance (id, student_id, total_classes, attended_classes, absences, consecutive_absences, attendance_rate, last_absence_date)
VALUES
  ('att-1001', 'aluno-1001', 120, 114, 6, 0, 95.00, '2026-09-15'),
  ('att-1002', 'aluno-1002', 120, 84, 36, 6, 70.00, '2026-10-10'),
  ('att-1003', 'aluno-1003', 120, 78, 42, 8, 65.00, '2026-10-11'),
  ('att-1004', 'aluno-1004', 120, 110, 10, 1, 91.67, '2026-10-05')
ON CONFLICT (id) DO NOTHING;

-- Initial risk evaluations seed
INSERT INTO evasao_risk_evaluations (id, student_id, risk_level, risk_score, reasons)
VALUES
  ('risk-1001', 'aluno-1001', 'BAIXO', 5, '["frequencia_regular"]'),
  ('risk-1002', 'aluno-1002', 'ALTO', 70, '["frequencia_abaixo_75","faltas_consecutivas_5"]'),
  ('risk-1003', 'aluno-1003', 'CRITICO', 85, '["frequencia_abaixo_75","faltas_consecutivas_5"]'),
  ('risk-1004', 'aluno-1004', 'BAIXO', 10, '["frequencia_regular"]')
ON CONFLICT (id) DO NOTHING;

-- Initial intervention seed
INSERT INTO evasao_interventions (id, student_id, type, description, status, assigned_to, scheduled_for)
VALUES
  ('int-2001', 'aluno-1002', 'CONTATO_RESPONSAVEIS', 'Contato telefônico com responsável sobre 6 faltas consecutivas', 'EM_ANDAMENTO', 'Coordenação Pedagógica', NOW() + INTERVAL '1 day'),
  ('int-2002', 'aluno-1003', 'REUNIAO_PEDAGOGICA', 'Convocação presencial dos responsáveis para plano de recuperação de frequência', 'PLANEJADA', 'Orientação Educacional', NOW() + INTERVAL '2 days')
ON CONFLICT (id) DO NOTHING;
