-- Applied manually against the `contatemeantes` database (owner: `contatemeantes`
-- role) in fzl-postgresql. Kept here for reference/reproducibility — the bundle
-- does not run migrations itself yet.

CREATE TABLE device_status (
    device_id      VARCHAR(64) PRIMARY KEY,
    user_name      VARCHAR(100),
    latitude       DOUBLE PRECISION NOT NULL,
    longitude      DOUBLE PRECISION NOT NULL,
    accuracy       REAL,
    battery_level  INT,
    is_charging    BOOLEAN,
    last_seen      TIMESTAMPTZ NOT NULL DEFAULT now()
);
