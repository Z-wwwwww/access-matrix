-- V46: seed the demo task dictionaries as MANAGED dicts (form 1 / form 3).
--
-- task is a demo BUSINESS feature, so its lookups are managed in core_dict_item
-- (runtime-editable, visible in the /platform/dicts console) — NOT built-in enums.
--   - task_priority = form 1 (pure DB; code never branches on it)
--   - task_status   = form 3 (DB-managed, but TaskService branches on a subset via
--                     the TaskStatus enum; that enum is NOT registered in DictRegistry,
--                     it only feeds branching + DictGuards delete-protection)
-- Delete-protection is computed at runtime by DictGuards (enum value OR referenced by
-- demo_task) — that's why the items carry no per-row protected flag and the TYPE is
-- builtin=0 (editable: ops may ADD statuses, just can't delete code-/data-bound ones).
-- Labels live in label_i18n here (managed dicts resolve by locale on the frontend,
-- not via a labelKey). Idempotent.

INSERT INTO core_dict (id, dict_code, name_i18n, builtin, remark, create_user) VALUES
    ('DICT0000000000000000000002', 'task_status',
     '{"ja_JP":"タスク状態","en":"Task status","zh_CN":"任务状态","zh_TW":"任務狀態","ko_KR":"작업 상태"}'::jsonb,
     0, 'Demo (form 3: DB + TaskStatus enum for branching)', 'v46'),
    ('DICT0000000000000000000003', 'task_priority',
     '{"ja_JP":"優先度","en":"Priority","zh_CN":"优先级","zh_TW":"優先級","ko_KR":"우선순위"}'::jsonb,
     0, 'Demo (form 1: pure DB lookup)', 'v46')
ON CONFLICT DO NOTHING;

INSERT INTO core_dict_item (id, dict_code, item_value, label_i18n, sort_no, css_class, status, create_user) VALUES
    ('DITM0000000000000000000010', 'task_status', '1',
     '{"ja_JP":"未着手","en":"To do","zh_CN":"未开始","zh_TW":"未開始","ko_KR":"미시작"}'::jsonb, 1, 'outline', 1, 'v46'),
    ('DITM0000000000000000000011', 'task_status', '2',
     '{"ja_JP":"進行中","en":"In progress","zh_CN":"进行中","zh_TW":"進行中","ko_KR":"진행 중"}'::jsonb, 2, 'default', 1, 'v46'),
    ('DITM0000000000000000000012', 'task_status', '3',
     '{"ja_JP":"完了","en":"Done","zh_CN":"完成","zh_TW":"完成","ko_KR":"완료"}'::jsonb, 3, 'default', 1, 'v46'),
    ('DITM0000000000000000000013', 'task_status', '4',
     '{"ja_JP":"取消","en":"Cancelled","zh_CN":"已取消","zh_TW":"已取消","ko_KR":"취소"}'::jsonb, 4, 'destructive', 1, 'v46'),
    ('DITM0000000000000000000020', 'task_priority', '1',
     '{"ja_JP":"低","en":"Low","zh_CN":"低","zh_TW":"低","ko_KR":"낮음"}'::jsonb, 1, 'outline', 1, 'v46'),
    ('DITM0000000000000000000021', 'task_priority', '2',
     '{"ja_JP":"中","en":"Medium","zh_CN":"中","zh_TW":"中","ko_KR":"중간"}'::jsonb, 2, 'default', 1, 'v46'),
    ('DITM0000000000000000000022', 'task_priority', '3',
     '{"ja_JP":"高","en":"High","zh_CN":"高","zh_TW":"高","ko_KR":"높음"}'::jsonb, 3, 'destructive', 1, 'v46')
ON CONFLICT DO NOTHING;
