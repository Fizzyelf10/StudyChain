CREATE TABLE orders (
    id            UUID PRIMARY KEY,
    listing_id    UUID           NOT NULL REFERENCES listings (id),
    buyer_id      UUID           NOT NULL REFERENCES users (id),
    seller_id     UUID           NOT NULL REFERENCES users (id),
    -- wallets are copied at order time so a later wallet change cannot redirect an escrow
    buyer_wallet  VARCHAR(42)    NOT NULL,
    seller_wallet VARCHAR(42)    NOT NULL,
    amount_usdc   NUMERIC(10, 2) NOT NULL,
    status        VARCHAR(20)    NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_orders_buyer ON orders (buyer_id);
CREATE INDEX idx_orders_seller ON orders (seller_id);
CREATE INDEX idx_orders_listing ON orders (listing_id);
CREATE INDEX idx_orders_status ON orders (status);
