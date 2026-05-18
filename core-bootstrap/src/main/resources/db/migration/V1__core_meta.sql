-- V1: service metadata placeholder
CREATE TABLE IF NOT EXISTS core_meta (
    id           CHAR(26)     PRIMARY KEY,
    meta_key     VARCHAR(64)  UNIQUE NOT NULL,
    meta_value   TEXT,
    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE core_meta IS 'Service metadata placeholder';
COMMENT ON COLUMN core_meta.meta_key IS 'Metadata key';
COMMENT ON COLUMN core_meta.meta_value IS 'Metadata value';

INSERT INTO core_meta (id, meta_key, meta_value)
VALUES ('01HKMETA000000000000000001', 'schema.version', '1.0.0')
ON CONFLICT (meta_key) DO NOTHING;
