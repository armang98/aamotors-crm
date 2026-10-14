-- Phase 7: transient provider errors are retried on the same channel; record that outcome explicitly.
ALTER TABLE message_attempts DROP CONSTRAINT ck_message_attempts_outcome;
ALTER TABLE message_attempts ADD CONSTRAINT ck_message_attempts_outcome
    CHECK (outcome IS NULL OR outcome IN ('SKIPPED', 'ACCEPTED', 'FAILED', 'RETRY', 'UNCERTAIN'));
