# 服务注册表（强制维护）

所有 service 必须登记，避免重复 API 封装。

## 使用规则

1. 生成服务前必须读取此文件
2. 如果存在相似服务 → 禁止新建
3. 必须优先复用已有服务
4. 生成服务后必须自动追加记录
5. 删除服务时同步删除此表行

## 命名规则

- **系统域 service** —— 无前缀简单名词（`auth.js`、`user.js`、`role.js`）
- **业务域 service** —— **业务模块名为前缀** + camelCase 拼具体资源（例 `pmsReservation.js`、`crmCustomer.js`）
- **不开子目录**（单业务 service 文件 > 15 个时再考虑）

---

| 名称 | 路径 | 用途 | 作者 | 日期 |
|------|------|------|------|------|
| request | services/request.js | Axios 实例（拦截器、Bearer token、X-Tenant-Id、错误统一处理）—— 任何业务文件不绕过 | AI | 2026-04-08 |
| auth | services/auth.js | 認証 API (login / refresh / logout / userInfo) | AI | 2026-04-08 |
| user | services/user.js | ユーザー管理 CRUD（list/get/add/update/delete）+ ロール割当・部署変更・ステータス変更・強制ログアウト・パスワードリセット・ロック解除 | AI | 2026-05-21 |
| role | services/role.js | ロール CRUD + 権限/メニュー/部署バインディング | AI | 2026-05-21 |
| permission | services/permission.js | 権限 CRUD + モジュール別取得 | AI | 2026-05-21 |
| menu | services/menu.js | メニュー CRUD + tree 取得 + `/menu/me`（自分のメニューツリー） | AI | 2026-05-21 |
| dept | services/dept.js | 部署 CRUD + tree 取得 | AI | 2026-05-21 |
| scope | services/scope.js | データ範囲（`/scope/me` で現在ユーザーの decision） | AI | 2026-05-21 |
| oplog | services/oplog.js | 操作ログ 一覧・詳細 | AI | 2026-05-21 |
| dict | services/dict.js | 辞書マスタ + 辞書データ CRUD（旧 PMS 互換 / `useDict` composable は別ルートで静的取得） | AI | 2026-04-16 |
