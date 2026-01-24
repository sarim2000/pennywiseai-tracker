-- Feedback items (bugs + feature requests)
CREATE TABLE IF NOT EXISTS feedback_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    item_type VARCHAR(20) NOT NULL CHECK (item_type IN ('BUG', 'FEATURE')),
    source VARCHAR(20) NOT NULL CHECK (source IN ('ANDROID_APP', 'WEB_TOOL')),
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PLANNED', 'IN_PROGRESS', 'COMPLETED', 'REJECTED')),
    vote_count INTEGER NOT NULL DEFAULT 0,
    submitter_fingerprint VARCHAR(64) NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Votes (one per fingerprint per item)
CREATE TABLE IF NOT EXISTS votes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    feedback_item_id UUID NOT NULL REFERENCES feedback_items(id) ON DELETE CASCADE,
    voter_fingerprint VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(feedback_item_id, voter_fingerprint)
);

-- Rate limiting
CREATE TABLE IF NOT EXISTS rate_limits (
    fingerprint VARCHAR(64) PRIMARY KEY,
    submission_count INTEGER NOT NULL DEFAULT 1,
    last_submission_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX idx_feedback_type ON feedback_items(item_type);
CREATE INDEX idx_feedback_status ON feedback_items(status);
CREATE INDEX idx_feedback_votes ON feedback_items(vote_count DESC);
CREATE INDEX idx_votes_item ON votes(feedback_item_id);
