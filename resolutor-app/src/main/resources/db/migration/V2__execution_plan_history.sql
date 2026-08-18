-- Append-only ExecutionPlan history (v2). Latest row remains in execution_plan_latest.

CREATE TABLE execution_plans (
    id            UUID PRIMARY KEY,
    generated_at  TIMESTAMPTZ NOT NULL,
    plan          JSONB NOT NULL
);

CREATE INDEX idx_execution_plans_generated_at ON execution_plans (generated_at DESC);
