-- Initialize pg_trgm extension for trigram search optimization
-- This extension enables GIN indexes for efficient substring matching
CREATE EXTENSION IF NOT EXISTS pg_trgm;
