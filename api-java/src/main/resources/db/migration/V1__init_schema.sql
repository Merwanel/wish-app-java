-- V1: Initialize Schema with Timestamps and Soft Deletion
-- This migration creates the wishes table with clean start support

CREATE TABLE wishes (
    id BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP WITH TIME ZONE NULL,
    name VARCHAR(255) NOT NULL,
    tags TEXT[] NOT NULL DEFAULT '{}',
    comment TEXT NOT NULL DEFAULT '',
    picture BYTEA NOT NULL DEFAULT '\x'
);

CREATE INDEX idx_wishes_updated_at ON wishes(updated_at);
CREATE INDEX idx_wishes_deleted_at ON wishes(deleted_at);
CREATE INDEX idx_wishes_tags ON wishes USING GIN (tags);
