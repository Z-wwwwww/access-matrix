-- V44: managed dictionaries (core_dict + core_dict_item).
--
-- The dictionary feature has two sources behind one read API (GET /dict/{code}):
--   1) built-in  — DictEnum classes registered in DictRegistry (status/state/type
--      that code branches on; single source of truth = the enum, NOT these tables)
--   2) managed   — these tables: runtime-editable business lookups that code does
--      NOT branch on, maintained by platform-ops (/admin/dict/**).
--
-- Both tables are a single GLOBAL set — like core_rbac_menu (V41/V43) they carry
-- NO tenant_id and are listed in MybatisPlusConfig.TENANT_EXCLUDED_TABLES. Their
-- entities therefore declare their own mark+audit instead of extending BaseEntity
-- (allowlisted in ArchitectureTest). Idempotent.

CREATE TABLE IF NOT EXISTS core_dict (
    id           char(26)     NOT NULL PRIMARY KEY,
    dict_code    varchar(64)  NOT NULL,
    name_i18n    jsonb,
    builtin      smallint     NOT NULL DEFAULT 0,
    remark       varchar(255),
    mark         smallint     NOT NULL DEFAULT 1,
    create_user  varchar(64),
    update_user  varchar(64),
    create_time  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_core_dict_code ON core_dict (dict_code) WHERE mark = 1;

COMMENT ON TABLE  core_dict             IS 'Managed dictionary types (global set; runtime-editable business lookups)';
COMMENT ON COLUMN core_dict.dict_code   IS 'Stable lookup key, e.g. gender';
COMMENT ON COLUMN core_dict.builtin     IS '1=protected (cannot delete type / edit items), 0=ordinary';

CREATE TABLE IF NOT EXISTS core_dict_item (
    id           char(26)     NOT NULL PRIMARY KEY,
    dict_code    varchar(64)  NOT NULL,
    item_value   varchar(64)  NOT NULL,
    label_i18n   jsonb,
    sort_no      int          NOT NULL DEFAULT 0,
    css_class    varchar(64),
    status       smallint     NOT NULL DEFAULT 1,
    ext          jsonb,
    mark         smallint     NOT NULL DEFAULT 1,
    create_user  varchar(64),
    update_user  varchar(64),
    create_time  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_core_dict_item ON core_dict_item (dict_code, item_value) WHERE mark = 1;
CREATE INDEX IF NOT EXISTS idx_core_dict_item_code ON core_dict_item (dict_code) WHERE mark = 1;

COMMENT ON COLUMN core_dict_item.item_value IS 'Stored value (FROZEN once used; business rows hold it raw)';
COMMENT ON COLUMN core_dict_item.status     IS '1=enabled (offered in dropdowns), 0=disabled (hidden but still resolves labels)';

-- Seed one managed example: gender. Demonstrates the admin UI + read path end to
-- end (no code branches on it). Built-in dicts (task_status/task_priority) are NOT
-- seeded here — they live in code (DictRegistry), not these tables.
INSERT INTO core_dict (id, dict_code, name_i18n, builtin, remark, create_user)
VALUES ('DICT0000000000000000000001', 'gender',
        '{"ja_JP":"性別","en":"Gender","zh_CN":"性别","zh_TW":"性別","ko_KR":"성별"}'::jsonb,
        0, 'Sample managed dictionary', 'v44')
ON CONFLICT DO NOTHING;

INSERT INTO core_dict_item (id, dict_code, item_value, label_i18n, sort_no, status, create_user)
VALUES
    ('DITM0000000000000000000001', 'gender', '1',
     '{"ja_JP":"男性","en":"Male","zh_CN":"男","zh_TW":"男","ko_KR":"남성"}'::jsonb, 1, 1, 'v44'),
    ('DITM0000000000000000000002', 'gender', '2',
     '{"ja_JP":"女性","en":"Female","zh_CN":"女","zh_TW":"女","ko_KR":"여성"}'::jsonb, 2, 1, 'v44'),
    ('DITM0000000000000000000003', 'gender', '3',
     '{"ja_JP":"その他","en":"Other","zh_CN":"其他","zh_TW":"其他","ko_KR":"기타"}'::jsonb, 3, 1, 'v44')
ON CONFLICT DO NOTHING;
