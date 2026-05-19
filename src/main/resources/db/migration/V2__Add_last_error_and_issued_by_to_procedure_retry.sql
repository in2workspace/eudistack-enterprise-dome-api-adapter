ALTER TABLE dome_adapter.procedure_retry
    ADD COLUMN IF NOT EXISTS last_error TEXT;

ALTER TABLE dome_adapter.procedure_retry
    ADD COLUMN IF NOT EXISTS issued_by TEXT;

COMMENT ON COLUMN dome_adapter.procedure_retry.last_error IS 'Error message from the last failed delivery attempt';
COMMENT ON COLUMN dome_adapter.procedure_retry.issued_by IS 'Subject (sub) from the OIDC ID token of the user who initiated the label credential upload';
