# frontend — Access Matrix Web

**English** · [中文](README.zh-CN.md)

The Access Matrix admin console plus business demo frontend. **Vue 3.5 + Vite 6 + Tailwind v4 + Radix Vue**, JavaScript only.

Companion backend: [../backend/](../backend/) (Spring Boot 4 + JWT + multi-tenant). Vite proxies to the backend at `:9135/api` via `/proxy_url`; the dev port defaults to `:5273`.

## Getting started

```bash
npm install
npm run dev          # development mode
npm run build        # production build
npm run lint         # ESLint
```

## Development conventions

All conventions live in [AGENTS.md](AGENTS.md):

- Component layering (`ui/` / `shared/` / `layout/` / `views/`)
- Business-prefixed service naming (`pmsXxx.js` / `demoXxx.js`)
- Mandatory shared components: `DataTable` / `Drawer` / `Input` / `Select` / `Checkbox` / `DatePicker` / `UserPicker` / `DictPicker` / `ConfirmDialog`
- No hand-written `<table>` / `<input type=date>` / `window.confirm()` / inline `style`

## AI Skill entry points

```bash
npm run ai:create-page UserList
npm run ai:create-component UserAvatar
npm run ai:generate userService
npm run ai:inspect compliance
npm run ai:analyze src/views/system/User/User.vue
```

Under the hood, `scripts/ai-cli.mjs` calls Claude Code.

## Main directories

```
services/         API wrappers (flat, prefix-distinguished)
src/components/
  ├── ui/         foundation UI
  ├── shared/     shared business components
  └── layout/     AppLayout etc.
src/views/
  ├── system/     system admin (User/Role/Permission/Menu/Dept/OpLog/Profile)
  └── demo/      business demo (Task — data-scope showcase)
src/composables/  useDict / useConfirm / useTheme ...
src/dict/storage.js   static dictionaries
src/lang/         vue-i18n translations
```
