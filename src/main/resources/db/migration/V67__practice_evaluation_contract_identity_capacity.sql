-- Forward-only capacity correction for component-complete Writing/Speaking
-- evaluation authority identities. V62 remains immutable.
ALTER TABLE practice_attempt_evaluation_jobs
    MODIFY COLUMN evaluation_contract_identity VARCHAR(1024) NOT NULL;
