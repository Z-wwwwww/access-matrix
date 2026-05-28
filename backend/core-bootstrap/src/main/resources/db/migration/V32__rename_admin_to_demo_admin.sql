-- V32: demo realm の built-in 管理者ユーザー名を `admin` → `demo-admin` に変更。
--
-- 経緯:
--   `admin` という汎用名は「Keycloak の master realm の admin」「KC admin
--   client 用の credential」「OS の admin アカウント」など至るところで使われ
--   ており、demo realm の業務管理者を指すコンテキスト依存度が高すぎた。
--   system realm の管理者を `ops` と命名している (= 用途を名前に埋め込む) のと
--   対称になるよう、業務側も `demo-admin` にリネームする。
--
-- 影響:
--   - DB: core_auth_user.username / email / display_name を更新
--   - DB: keycloak_id を NULL に戻して、次回 SSO ログイン時に
--     OidcJitUserService が (tenant=demo, username=demo-admin) で
--     新しい KC UUID に紐づけ直すよう仕向ける（KC 側の admin ユーザーは
--     残骸として残るが、誰も使わなくなるので無害）
--   - LocalAdminSeeder / LocalKeycloakAdminSeeder のコード定数は
--     "demo-admin" に更新済み
--   - ROLE binding / dept binding / user_no は ID ベースなので変更不要
--
-- 冪等: WHERE で旧 `admin` 行のみマッチするので再実行は 0 行更新。
--       既に rename 済みの環境では何もしない。

UPDATE core_auth_user
   SET username     = 'demo-admin',
       email        = CASE WHEN email = 'admin@platform.local'
                           THEN 'demo-admin@platform.local'
                           ELSE email END,
       display_name = CASE WHEN display_name = 'Local Admin'
                           THEN 'Demo Admin'
                           ELSE display_name END,
       keycloak_id  = NULL,
       update_user  = 'migration-v32',
       update_time  = CURRENT_TIMESTAMP
 WHERE tenant_id = 'demo'
   AND username  = 'admin'
   AND mark      = 1;
