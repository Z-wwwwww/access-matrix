# frontend — Access Matrix Web

[English](README.md) · **中文**

Access Matrix 管理后台 + 业务示例前端。**Vue 3.5 + Vite 6 + Tailwind v4 + Radix Vue**，JavaScript only。

配套后端: [../backend/](../backend/)（Spring Boot 4 + JWT + 多租户）。Vite 通过 `/proxy_url` 代理到后端 `:9135/api`，开发端口默认 `:5273`。

## 启动

```bash
npm install
npm run dev          # 开发模式
npm run build        # 生产构建
npm run lint         # ESLint
```

## 开发规约

所有约定见 [AGENTS.zh-CN.md](AGENTS.zh-CN.md)：

- 组件分层（`ui/` / `shared/` / `layout/` / `views/`）
- 业务前缀的 services 命名（`pmsXxx.js` / `demoXxx.js`）
- 必用共享组件：`DataTable` / `Drawer` / `Input` / `Select` / `Checkbox` / `DatePicker` / `UserPicker` / `DictPicker` / `ConfirmDialog`
- 禁手写 `<table>` / `<input type=date>` / `window.confirm()` / 内联 style

## AI Skill 入口

```bash
npm run ai:create-page UserList
npm run ai:create-component UserAvatar
npm run ai:generate userService
npm run ai:inspect compliance
npm run ai:analyze src/views/system/User/User.vue
```

底层走 `scripts/ai-cli.mjs` 调 Claude Code。

## 主要目录

```
services/         API 封装（平铺、前缀区分）
src/components/
  ├── ui/         基础 UI
  ├── shared/     通用业务组件
  └── layout/     AppLayout 等
src/views/
  ├── system/     系统管理（User/Role/Permission/Menu/Dept/OpLog/Profile）
  └── demo/       业务示例（Task —— 数据范围演示）
src/composables/  useDict / useConfirm / useTheme ...
src/dict/storage.js   静态字典
src/lang/         vue-i18n 翻译
```
