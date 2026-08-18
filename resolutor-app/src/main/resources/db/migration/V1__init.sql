-- Resolutor initial schema. See docs/implementation-plan.md §9.

CREATE TABLE tasks (
    id                  UUID PRIMARY KEY,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    payload             JSONB NOT NULL,
    search_dsl          JSONB NOT NULL,
    resources           JSONB,
    cursor              TEXT,
    state               TEXT NOT NULL
        CHECK (state IN ('RECEIVED','PENDING','STARTED','PROCESSING','PAUSED','COMPLETED','TIMEOUT','FAILED')),
    timeout_at          TIMESTAMPTZ,
    top_resource_class  TEXT NOT NULL,
    top_resource_id     TEXT NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_tasks_state_created ON tasks(state, created_at);
CREATE INDEX idx_tasks_top_resource  ON tasks(top_resource_class, top_resource_id);
CREATE INDEX idx_tasks_timeout       ON tasks(timeout_at)
    WHERE state IN ('STARTED','PROCESSING');

CREATE TABLE task_progress (
    task_id        UUID PRIMARY KEY REFERENCES tasks(id) ON DELETE CASCADE,
    total_count    BIGINT NOT NULL DEFAULT 0,
    success_count  BIGINT NOT NULL DEFAULT 0,
    failed_count   BIGINT NOT NULL DEFAULT 0,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    version        BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE shedlock (
    name        VARCHAR(64) PRIMARY KEY,
    lock_until  TIMESTAMPTZ NOT NULL,
    locked_at   TIMESTAMPTZ NOT NULL,
    locked_by   VARCHAR(255) NOT NULL
);

CREATE TABLE execution_plan_latest (
    id            SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    plan          JSONB NOT NULL,
    generated_at  TIMESTAMPTZ NOT NULL
);
