-- V31: core_numbering_management をテナント単位に分割。
--
-- 経緯:
--   V4 で {@code core_numbering_management} を「単一実装グローバル」として
--   PK=code_kbn で作成した。incrementManagement (fixedKey 無しの
--   {@code next()}) パスはこの行の {@code seq_id} を直接 +1 する。結果として
--   {@code UserAdminService.create()} 経由で発番される {@code user_no} は
--   <b>全テナント共通のグローバル自動採番</b>になっていた:
--     demo→U00000001, acme→U00000002, demo→U00000003 ...
--   業務要件は「テナントごとに 1 から採番」のため、テナント分離が必要。
--
-- 修復:
--   PK を {@code (tenant_id, code_kbn)} に変更し、各テナントが独立した
--   {@code seq_id} を持つ。既存のグローバル行は {@code DEFAULT 'demo'} で
--   demo の行として残り、他の active テナント (system) には定義をクローン。
--   新規テナント作成時の seed 処理は {@link TenantAdminService#create} →
--   {@link NumberingService#seedDefaultsForTenant} で行う。
--
-- 冪等: 全ステップが {@code IF NOT EXISTS} / {@code IF EXISTS} / {@code ON
--       CONFLICT DO NOTHING} で防御してあるので再実行安全。

-- 1. tenant_id カラム追加（既存行は DEFAULT で demo に振り分け）
ALTER TABLE core_numbering_management
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'demo';

-- 2. PK を組み替え
ALTER TABLE core_numbering_management
    DROP CONSTRAINT IF EXISTS core_numbering_management_pkey;
ALTER TABLE core_numbering_management
    ADD CONSTRAINT core_numbering_management_pkey PRIMARY KEY (tenant_id, code_kbn);

-- 3. 他の active テナント (system 等) にも定義をクローン
--    seq_id は demo の現在値をそのまま継承（業務影響を最小化）。
--    seq_id をリセットしたい新規テナントは TenantAdminService.create が
--    NumberingService.seedDefaultsForTenant 経由で seq_id = min_value -
--    step_value を入れて作る。
INSERT INTO core_numbering_management
    (tenant_id, code_kbn, format_sentence, recycle_division, zero_insert,
     seq_id_digit, date_format_sentence, min_value, max_value, step_value,
     seq_id, description)
SELECT t.tenant_code, n.code_kbn, n.format_sentence, n.recycle_division, n.zero_insert,
       n.seq_id_digit, n.date_format_sentence, n.min_value, n.max_value, n.step_value,
       n.seq_id, n.description
  FROM core_tenant t
  CROSS JOIN core_numbering_management n
 WHERE t.mark = 1
   AND n.tenant_id = 'demo'
   AND t.tenant_code != 'demo'
ON CONFLICT (tenant_id, code_kbn) DO NOTHING;
