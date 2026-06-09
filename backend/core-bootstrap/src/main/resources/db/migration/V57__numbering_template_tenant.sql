-- V57: decouple the new-tenant numbering seed from the live `demo` tenant.
--
-- 経緯:
--   NumberingService.seedDefaultsForTenant() はこれまで tenant_id='demo' の
--   numbering 定義をクローンしていた。これは demo を「削除不可の供給テンプレ」
--   にしてしまう —— しかし demo は本来「ごく普通の、削除可能なサンプルテナント」
--   であるべき。よって canonical な numbering テンプレを、実テナントではない
--   予約センチネル tenant_id ('__template__') 配下に切り出す。これは一覧にも
--   出ず、削除対象にもならないため、新規テナント作成が demo の存在に依存しなく
--   なる。
--
-- 冪等: 全ステップ ON CONFLICT DO NOTHING で再実行安全。

-- 1. demo リファレンステナントが現在持つ定義をテンプレへスナップショット。
INSERT INTO core_numbering_management
    (tenant_id, code_kbn, format_sentence, recycle_division, zero_insert,
     seq_id_digit, date_format_sentence, min_value, max_value, step_value,
     seq_id, description)
SELECT '__template__', code_kbn, format_sentence, recycle_division, zero_insert,
       seq_id_digit, date_format_sentence, min_value, max_value, step_value,
       seq_id, description
  FROM core_numbering_management
 WHERE tenant_id = 'demo'
ON CONFLICT (tenant_id, code_kbn) DO NOTHING;

-- 2. demo が既に削除済 / 定義ゼロでも canonical な USER 定義は必ず存在させる
--    (V4 のシードと同一値)。
INSERT INTO core_numbering_management
    (tenant_id, code_kbn, format_sentence, recycle_division, zero_insert,
     seq_id_digit, min_value, max_value, step_value, seq_id, description)
VALUES
    ('__template__', 'USER', 'U[%]', 0, '0', 8, 1, 99999999, 1, 1, 'User account number sequence')
ON CONFLICT (tenant_id, code_kbn) DO NOTHING;
