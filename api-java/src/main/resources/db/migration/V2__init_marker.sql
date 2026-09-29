-- Marker table mirroring Express prisma model Init: seed runs once per database.
CREATE TABLE init_marker (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
