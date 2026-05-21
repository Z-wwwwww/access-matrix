<!-- Conventional Commits title 例: feat(backend): add /demo/task/list endpoint -->

## What

<!-- 1-3 行说明改动 -->

## Why

<!-- 动机：bug / 需求 / 重构原因。链 issue 或讨论 -->

## 影响范围

- [ ] backend (`backend/**`)
- [ ] frontend (`frontend/**`)
- [ ] repo (workflows / docs / config / `AGENTS.md`)

## 验证

<!-- 列出 CI 之外人工验证的步骤 -->

- [ ] `cd backend && ./mvnw -DskipTests compile` 通过（涉及后端时）
- [ ] `cd frontend && npm run build` 通过（涉及前端时）
- [ ] 手动验证：

## Breaking changes

<!-- 如有，说明数据库迁移 / API 契约变化 / 配置项删除等。否则填"无"。 -->
