CREATE TABLE reports (
    id              UUID PRIMARY KEY,
    listing_id      UUID         NOT NULL REFERENCES listings (id),
    reporter_id     UUID         NOT NULL REFERENCES users (id),
    reason          VARCHAR(30)  NOT NULL,
    details         VARCHAR(1000),
    status          VARCHAR(20)  NOT NULL,
    resolution_note VARCHAR(500),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    resolved_at     TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_reports_listing ON reports (listing_id);
CREATE INDEX idx_reports_status ON reports (status);
