-- V4: numbering tables + back-fill columns on core_auth_user (idempotent)

ALTER TABLE core_auth_user ADD COLUMN IF NOT EXISTS user_no VARCHAR(32);
ALTER TABLE core_auth_user ADD COLUMN IF NOT EXISTS email VARCHAR(255);
ALTER TABLE core_auth_user ADD COLUMN IF NOT EXISTS display_name VARCHAR(128);

CREATE TABLE IF NOT EXISTS core_numbering_management (
    code_kbn              VARCHAR(64)   PRIMARY KEY,
    format_sentence       VARCHAR(255)  NOT NULL,
    recycle_division      SMALLINT      NOT NULL DEFAULT 0,
    zero_insert           VARCHAR(8)    DEFAULT '0',
    seq_id_digit          SMALLINT      NOT NULL DEFAULT 6,
    date_format_sentence  VARCHAR(32),
    min_value             BIGINT        NOT NULL DEFAULT 1,
    max_value             BIGINT        NOT NULL DEFAULT 999999,
    step_value            BIGINT        NOT NULL DEFAULT 1,
    seq_id                BIGINT        NOT NULL DEFAULT 0,
    description           VARCHAR(255)
);

COMMENT ON TABLE core_numbering_management IS 'Numbering definition (code_kbn = category)';
COMMENT ON COLUMN core_numbering_management.recycle_division IS '0=none, 1=daily, 2=monthly, 3=yearly';
COMMENT ON COLUMN core_numbering_management.format_sentence IS 'Template using [%K], [%D], [%] placeholders';

CREATE TABLE IF NOT EXISTS core_numbering_key (
    tenant_id      VARCHAR(64)  NOT NULL DEFAULT 'default',
    code_kbn       VARCHAR(64)  NOT NULL,
    numbering_key  VARCHAR(255) NOT NULL,
    seq_id         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_core_numbering_key PRIMARY KEY (tenant_id, code_kbn, numbering_key)
);

COMMENT ON TABLE core_numbering_key IS 'Numbering counter buckets per (tenant, category, key)';

-- Seed: USER numbering definition (format U + 8-digit seq, no recycle)
INSERT INTO core_numbering_management
    (code_kbn, format_sentence, recycle_division, zero_insert, seq_id_digit,
     min_value, max_value, step_value, seq_id, description)
VALUES
    ('USER', 'U[%]', 0, '0', 8, 1, 99999999, 1, 1, 'User account number sequence')
ON CONFLICT (code_kbn) DO NOTHING;
