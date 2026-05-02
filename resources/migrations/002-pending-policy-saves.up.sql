CREATE TABLE IF NOT EXISTS pending_policy_saves (
  policy_id UUID PRIMARY KEY,
  partner_id UUID NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 1,
  last_error TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  last_attempt_at TIMESTAMP NOT NULL DEFAULT NOW()
);
