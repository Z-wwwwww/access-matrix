-- V16: demo データを「実在しそうな」日本語に置き換える
-- ============================================
-- 背景：「ユーザー名」列を業務担当者が見たとき、demo_all のような英数 ID では
-- 何者か想像しづらい。ログイン ID（username）は romaji のまま残しつつ、
-- 表示名（displayName）を実際の日本人姓名にし、ロール名も実在しそうな
-- 職位に揃えて、業務確認用データとしての見映えを整える。
--
-- admin ユーザーは対象外（運用者の要望）。

-- ============================================
-- 1. demo 用 5 ユーザー
-- ============================================
UPDATE core_auth_user
   SET username     = CASE id
                        WHEN '00000000000000000000USER11' THEN 'tanaka_taro'
                        WHEN '00000000000000000000USER12' THEN 'yamada_hanako'
                        WHEN '00000000000000000000USER13' THEN 'sato_ken'
                        WHEN '00000000000000000000USER14' THEN 'suzuki_misaki'
                        WHEN '00000000000000000000USER15' THEN 'takahashi_shinichi'
                      END,
       display_name = CASE id
                        WHEN '00000000000000000000USER11' THEN '田中 太郎'
                        WHEN '00000000000000000000USER12' THEN '山田 花子'
                        WHEN '00000000000000000000USER13' THEN '佐藤 健'
                        WHEN '00000000000000000000USER14' THEN '鈴木 美咲'
                        WHEN '00000000000000000000USER15' THEN '高橋 慎一'
                      END,
       email        = CASE id
                        WHEN '00000000000000000000USER11' THEN 'tanaka.taro@demo.local'
                        WHEN '00000000000000000000USER12' THEN 'yamada.hanako@demo.local'
                        WHEN '00000000000000000000USER13' THEN 'sato.ken@demo.local'
                        WHEN '00000000000000000000USER14' THEN 'suzuki.misaki@demo.local'
                        WHEN '00000000000000000000USER15' THEN 'takahashi.shinichi@demo.local'
                      END,
       user_no      = CASE id
                        WHEN '00000000000000000000USER11' THEN 'U00000011'
                        WHEN '00000000000000000000USER12' THEN 'U00000012'
                        WHEN '00000000000000000000USER13' THEN 'U00000013'
                        WHEN '00000000000000000000USER14' THEN 'U00000014'
                        WHEN '00000000000000000000USER15' THEN 'U00000015'
                      END
 WHERE id IN (
        '00000000000000000000USER11',
        '00000000000000000000USER12',
        '00000000000000000000USER13',
        '00000000000000000000USER14',
        '00000000000000000000USER15'
   );

-- ============================================
-- 2. demo 用 5 ロール（V15 で「デモ：xxx」にしたものを、より実在する職位名に置き換え）
-- ============================================
UPDATE core_rbac_role
   SET name = CASE id
                WHEN '00000000000000000000ROLE11' THEN '取締役'
                WHEN '00000000000000000000ROLE12' THEN '東京支社長'
                WHEN '00000000000000000000ROLE13' THEN '大阪支社課長'
                WHEN '00000000000000000000ROLE14' THEN '一般社員'
                WHEN '00000000000000000000ROLE15' THEN '京都連絡担当'
              END,
       description = CASE id
                       WHEN '00000000000000000000ROLE11' THEN '全社のタスクを閲覧可能'
                       WHEN '00000000000000000000ROLE12' THEN '東京支社と配下の京都支社のタスクを閲覧'
                       WHEN '00000000000000000000ROLE13' THEN '大阪支社のタスクのみ閲覧'
                       WHEN '00000000000000000000ROLE14' THEN '自分が作成したタスクのみ閲覧'
                       WHEN '00000000000000000000ROLE15' THEN '京都支社のタスクのみ閲覧（カスタム範囲）'
                     END,
       update_time = CURRENT_TIMESTAMP
 WHERE id IN (
        '00000000000000000000ROLE11',
        '00000000000000000000ROLE12',
        '00000000000000000000ROLE13',
        '00000000000000000000ROLE14',
        '00000000000000000000ROLE15'
   );
