-- Applied manually against the `contatemeantes` database (owner: `contatemeantes`
-- role) in fzl-postgresql. Kept here for reference/reproducibility — the bundle
-- does not run migrations itself yet.

-- group_id: one group per device (a plain column, not a join table) — the
-- smallest thing that answers "which devices are in group X", not general
-- many-group membership/ACL. See GET /contatemeantes/group/{groupId}/devices.
CREATE TABLE device_status (
    device_id      VARCHAR(64) PRIMARY KEY,
    user_name      VARCHAR(100),
    latitude       DOUBLE PRECISION NOT NULL,
    longitude      DOUBLE PRECISION NOT NULL,
    accuracy       REAL,
    battery_level  INT,
    is_charging    BOOLEAN,
    last_seen      TIMESTAMPTZ NOT NULL DEFAULT now(),
    group_id       VARCHAR(64)
);

CREATE INDEX device_status_group_id_idx ON device_status (group_id);
