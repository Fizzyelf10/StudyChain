CREATE TABLE listings (
    id                UUID PRIMARY KEY,
    seller_id         UUID           NOT NULL REFERENCES users (id),
    title             VARCHAR(200)   NOT NULL,
    description       VARCHAR(2000),
    course_code       VARCHAR(20)    NOT NULL,
    university        VARCHAR(100)   NOT NULL,
    kind              VARCHAR(20)    NOT NULL,
    source            VARCHAR(30)    NOT NULL,
    source_note       VARCHAR(500)   NOT NULL,
    price_usdc        NUMERIC(10, 2) NOT NULL CHECK (price_usdc > 0),
    status            VARCHAR(20)    NOT NULL,
    file_key          VARCHAR(36)    NOT NULL,
    original_filename VARCHAR(255)   NOT NULL,
    file_size_bytes   BIGINT         NOT NULL,
    file_sha256       VARCHAR(64)    NOT NULL UNIQUE,
    page_count        INT            NOT NULL,
    has_preview       BOOLEAN        NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_listings_course_code ON listings (course_code);
CREATE INDEX idx_listings_status_created ON listings (status, created_at);
CREATE INDEX idx_listings_seller ON listings (seller_id);
