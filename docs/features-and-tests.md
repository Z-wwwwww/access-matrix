# 基盤機能 & テストポイント / 基盘功能 & 测试点

> **维护约定（重要）**：本文件是**基盘功能清单 + 手动测试点**的单一来源。
> **每次新增或改动功能,必须同步更新本文件**——在对应小节追加/修改功能说明,并**列出该功能的测试点**(可勾选清单)。
> 加了什么功能,就要在这里列出"怎么验证它能用"。AI 助手在实现新功能后也应主动更新本文件。

约定标记:`mode=oidc` 指 Keycloak SSO 模式(dev/prod 默认);本地 dev 账号见 `docs/getting-started`。
平台运维(ops)= `system` 租户用户;业务用户 = 各业务租户(demo / acme …)。

---

## 0. 認証 / ログイン方式（Auth / Login）

| 方式 | 说明 |
|---|---|
| **OIDC SSO** | 主路径。各租户对应一个 Keycloak realm;平台运维用 `system` realm。登录后 JWT 带 `tid`(租户)。 |
| **Break-glass 密码登录** | SSO 不可用时的应急通道(`AdminAuthController` HS256)。`mode=oidc` 下 `/auth/login` 成功即视为 break-glass,会写审计 `system/auth.breakGlass` + 给本人发告警邮件。仅 super-admin 的 `password_hash` 被保留。 |
| **安全模式** | `app.security.mode` = `oidc`(默认) / `jwt`(legacy 密码) / `permit-all`(裸跑无 IdP)。 |
| **租户解析** | `utils/tenant.js`:子域名 → `?tenant=` → localStorage → `default` 的优先级。 |
| **会话维持** | access token(Bearer)+ refresh token(HttpOnly cookie);401 时单飞刷新并重放;刷新失败踢回登录。 |
| **入驻** | 业务租户管理员:邀请邮件 + 落地页设密;平台运维用户:一次性临时密码(KC 强制首登改密)。 |

**测试点**
- [ ] SSO 正常登录(各租户 + system),登录后能进对应首页。
- [ ] SSO 不可达时,登录页出现"使用应急密码登录"通道;break-glass 登录成功。
- [ ] break-glass 登录后:`core_oplog` 有 `system/auth.breakGlass` 记录;安全面板"break-glass(7天)"+1;本人收到告警邮件(或日志)。
- [ ] access token 过期后自动刷新、原请求重放成功;刷新失败被踢回 `/login`。
- [ ] 多 realm:在 A 租户的账号无法登录 B 租户。
- [ ] 新建运维用户用临时密码首登 → KC「修改密码」页,按钮是通用「提交」(不是「发送重置链接」)。Keycloak 主题 `doSubmit` 必须保持通用(多页面共用),不要写成某页专属文案。
- [ ] KC 多字段表单(更新账户信息 / 修改密码)排版整齐:必填 `*` 紧跟 label **同一行**,label↔input、字段↔字段间距正常(不因 `.pf-*__label-text` 被设成 block 而拉开)。

---

## 1. マルチテナント & RBAC 基盤

- **多租户**:业务行带 `tenant_id`;`system` 租户(平台运维)被 MyBatis-Plus 拦截器识别为"绕过租户范围",可跨租户读写。
- **权限通配符**(`PermissionMatcher`):`*:*`=平台超级(仅匹配 `platform:` 命名空间);`tenant:*`=租户超级(匹配 `platform:` 之外的业务权限);`resource:*`=某资源全动作;精确匹配。两个超级互不覆盖。
- **角色**:SUPER_ADMIN(各租户,`tenant:*`)、PLATFORM_ADMIN(ops,`*:*`+`opsuser:*`)、PLATFORM_OPERATOR(常规运维,`*:*`)。
- **菜单**:全局一套(`core_rbac_menu`,无 tenant_id),按 `permission_code` 过滤;前端由 `menu-to-routes` 动态注册路由。
- **数据范围(data scope)**:`DataScopeHelper` 按 dept/创建人重写查询(见 `docs/data-scope-demo`)。

**测试点**
- [ ] 业务 super(`tenant:*`)看不到平台菜单 / 调 `/platform/*` 返回 403。
- [ ] 平台 ops(`*:*`)能管租户但不能模拟业务用户(无 `tenant:*`)。
- [ ] 改某角色权限后,持有该角色的用户**下次请求**即生效(权限缓存按角色失效)。
- [ ] 菜单按权限显隐;无权限的路由直接访问被拦/404。
- [ ] 菜单管理列表的类型徽标按 `menu_type` dict 的 css_class 着色:目录=info(蓝)、菜单=success(绿)、按钮=violet(紫)。
- [ ] 数据范围:不同 data_scope 的用户看到的 task 行集不同。

---

## 2. プラットフォーム運用コンソール（Platform Ops Console）

> 入口均为顶层平台菜单(V48 拍平),仅平台运维可见。

### 2.1 租户管理(Tenant management)
新建(KC realm + 注册行 + 管理员邀请)、停用/恢复、硬删除(回收站式:先停用→输入 code 确认)、重发邀请、发起支持会话。
**列表 UI(重设计)**:页头(eyebrow/标题/副标题)+ 工具栏(实时搜索 + 状态分段筛选 All/运行中/已停用,计数来自 stats + 表格/卡片视图切换,记忆于 localStorage)+ 可折叠/可关闭的回收站提示 + 列表面板 + 分页页脚。每行:字母徽标(按 code 取色,基于 `--card/--foreground` 适配调色板+深色)、双行单元格(显示名 + 等宽 code)、语义状态点(运行中=绿/已停用=琥珀)、相对时间(hover 显示绝对值)、**悬停才出现**的操作图标簇;已停用行有斜纹底纹;内置租户(system/demo)禁用破坏性操作。点击行 → 右侧**详情抽屉**(联系邮箱、KC realm=tenantCode、创建/更新时间 + 操作按钮)。颜色/字体走 app 主题令牌(非设计稿硬编码),设计稿中后端没有的字段(plan/users/region/owner)已省略。状态分段筛选走**后端** `GET /platform/tenants?status=0|1`(新增可选参数)。
**测试点**
- [ ] 新建租户:KC realm 建好、管理员收到邀请、列表出现该租户。
- [ ] 停用→登录被拒;恢复→可登录。
- [ ] 硬删除需先停用 + 输入正确 tenantCode;内置租户(system/demo)不可改/删。
- [ ] 重发邀请(可改邮箱)。
- [ ] 状态分段(运行中/已停用)服务端过滤正确、跨分页一致;计数与列表吻合。
- [ ] 实时搜索(去抖)、表格/卡片切换(刷新后记忆)、详情抽屉(Esc/遮罩/X 关闭)。
- [ ] 行操作权限门控与原表一致;悬停显示;内置租户禁用编辑/停用/删除/重发。
- [ ] 切换调色板 + 深色模式:徽标、状态点、抽屉、分页样式随主题正常显示。

### 2.2 平台总览(Platform overview / Ops dashboard)
独立菜单页 `/platform/overview`(菜单 `platform.overview`,门控 `platform:tenant:read`,排在租户管理之前)。租户 KPI 卡 + 状态环形图 + 按月新增趋势(`/platform/tenants/stats`);4 个监控面板(`/platform/dashboard`):入驻激活、活跃参与、平台健康、安全。可选卡片点击切换明细;默认选中第一个有值的卡。**与 2.1 租户管理分页**:dashboard 从租户管理页拆出独立成页,使租户表格首屏即可见。
**测试点**
- [ ] 「平台总览」作为独立菜单出现(在「租户管理」之前);ops 与 operator 都能看到。
- [ ] KPI/图表显示真实聚合(非 mock);主题切换图表重新着色。
- [ ] 入驻/平台健康/安全三个面板:点卡片切换明细列表,默认选中第一个有值卡。
- [ ] 指标 hover 有简短解释。
- [ ] 各明细列表上限 8 行(不会无限拉长)。
- [ ] 「接口错误(24h)」只计真正 500(业务拒绝 4xx/7xx 不算)。
- [ ] 「租户管理」页不再显示 dashboard,搜索栏 + 表格首屏即可见。

### 2.3 领域事件控制台(Domain events)
`/platform/events`:分页列表(按分发状态/类型/关键字筛)、详情(payload)、失败事件**重发**(单条/批量,仅 `dispatch_state=2`)。
**测试点**
- [ ] 增删改 demo task → 产生 `demo.task.*` 事件并出现在列表(几秒内 待分发→已分发)。
- [ ] 按"失败"筛选;对失败事件点重发 → 状态回到待分发并被重新分发。
- [ ] 查看详情能看到 JSON payload。
- [ ] 非平台运维访问 → 看不到菜单 / API 403。

### 2.4 定时任务(Scheduled jobs)
`ScheduledJob` SPI → `core_job`,管理台可启停/改 cron/立即执行/看日志。删类重启 → 配置行软删下线。Outbox 保留作业(`core:outbox-retention`,每天 03:30,删已分发的旧事件,V49/V50)。
**测试点**
- [ ] 新建的 `ScheduledJob` 重启后出现在管理台并按 cron 执行。
- [ ] 启停/改 cron/立即执行生效;执行日志可见。
- [ ] 删除 job 类 + 重启 → 管理台不再显示(`core_job` 软删 mark=0,日志保留)。
- [ ] 保留作业只删 `dispatch_state=1` 的旧事件,pending/failed 不动。

### 2.5 支持会话 / 模拟登录(Support session / impersonation)
ops 以目标租户 SUPER_ADMIN 身份操作 30 分钟(`tenant.impersonate.start`);服务端会话表 `core_support_session`(V52)记录 started/expires/**ended_at**。退出调 `/support-session/terminate` 置 ended。
**测试点**
- [ ] 发起支持会话 → 顶部红色横幅 + 倒计时;能以租户身份操作。
- [ ] 安全面板「支持会话进行中」= 真正在线数;退出后立刻归零(不再卡 30 分钟)。
- [ ] 最近会话列表:在线绿点 / 已结束灰点。
- [ ] 内置租户不可被模拟。

### 2.6 運用ユーザー管理 / 平台用户(Platform users,两级)
`/platform/users`(`opsuser:*`,仅 ops 可见):列表、新建(KC system realm 用户 + 临时密码 + 授 **PLATFORM_OPERATOR**)、停用/启用、重置密码、删除。常规运维(PLATFORM_OPERATOR=`*:*`)**看不到本页**。
**测试点**
- [ ] ops 能看到「运维用户」菜单;常规运维**看不到**(`opsuser:*` 不被 `*:*` 覆盖),直接访问 API 403。
- [ ] 新建用户 → 弹一次性临时密码;新用户用它登录、KC 强制改密。
- [ ] 新用户能管租户/事件等,但**看不到运维用户菜单**(只比 ops 低一档)。
- [ ] 停用→该用户无法登录;启用→恢复;重置密码→弹新临时密码;删除→DB 软删 + KC 用户删除。
- [ ] 不能对**自己**或**ops(超级)**执行停用/删除/重置(行内按钮隐藏 + 后端拦截)。
- [ ] 停用/删除会**立即踢下线**(ForceLogout):被停用用户**当前已登录的会话**下次请求即被拒(不只是阻止再次登录);启用会清除踢出标记。
- [ ] 新建表单必填项 `*` 为红色;邮箱有格式校验(前端正则 + 后端 `@Email`),格式不对被拦。
- [ ] 新建用户邮箱**必填**;首登**不**经过"更新账户信息"页(不报"请指定此字段"),直接进设置密码——KC 用户的 email/firstName/lastName 创建时已补全(单段名 lastName 也填)。
- [ ] 状态徽标有颜色区分:启用=绿(success)、禁用=红(danger)。

---

## 3. 共通基盤（Cross-cutting）

- **字典(Dictionary)**:内置枚举字典 + 运行时可编辑的管理字典;前端 `useDict`,下拉/标签均走字典,不硬编码。`/platform/dicts` 管理。
- **操作审计(oplog)**:`@OpLog` 写 `core_oplog`;失败行带 `error_code`(BusinessException 的 4xx/7xx vs 未预期 500)。
- **领域事件出箱**:状态变更在同一事务 `EventPublisher.publish(...)` → `core_domain_event` → `OutboxDispatcher` → 可插拔 sink(当前为日志兜底)。详见 backend/AGENTS.md。
- **通知(铃铛)**:`core_notification` + SSE 心跳(15s)。注意与领域事件是两套东西。
- **编号(numbering)**:`core_numbering*`,按租户分配 user_no 等。
- **i18n**:ja_JP / zh_CN / zh_TW / en / ko_KR 五语;权限标签由后端 `I18nPermissionPatcher` 生成占位 `__TODO__`,需人工填 `frontend/src/lang/generated/permissions.*.json`。

**测试点**
- [ ] 字典:新增/停用管理字典项;被引用的项不可删(报"in use")、枚举项不可删——这些是**业务拒绝**,不计入仪表盘"接口错误"。
- [ ] 任一带 `@OpLog` 的操作在 `core_oplog` 留痕;失败时 `error_code` 正确。
- [ ] 语言切换:5 语言下新功能文案都不出现 key 原文 / `__TODO__`。
- [ ] 通知:触发后铃铛出现未读、可标记已读。

---

## 4. フロントエンド基盤（Frontend foundation）

- **多 Tab + keep-alive**:目录占位 `EmptyLayout` 单实例复用(修复了"多 tab 后新 tab 发重复请求")。
- **侧边栏**:收藏 / 置顶 / 顶层叶子菜单提升 / 折叠 flyout。
- **主题 / 调色板**:CSS 变量;`.dark` + `data-palette`。单一暖色主题(暖米白 `#f4efe9` 背景 + 纯白卡片 + 页面背景两团极淡品牌径向光晕,深色模式去掉光晕),仅**品牌强调色**可切:陶土铁锈(默认)/ 砖红 / 赭黄 / 暗松绿 / 灰紫;surfaces 五色共用,只换 `--primary/--accent/--ring/--brand-orange`。

**测试点**
- [ ] 打开多个 tab,再开新 tab:每个接口**只发一次**(无重复请求)。
- [ ] 关闭 tab 不残留 404;双击 tab 刷新该页。
- [ ] 收藏/置顶菜单显示在顶部;折叠侧栏 hover 出 flyout。
- [ ] 切主题/调色板,图表与界面颜色随之更新。

---

## 変更履歴 / 变更记录(追加在最上)

- 2026-06-05 — 徽标着色区分:平台用户状态徽标启用=绿(success)/禁用=红(danger)(原 `destructive` 非法变体,实际无色);菜单管理类型徽标由 `MenuType` enum 新增 `css_class`(目录=info/菜单=success/按钮=violet)驱动,前端 `menuType.cssClass(row.menuType) || 'outline'`(后端重启后生效)。

- 2026-06-05 — 主题系统精简为单一暖色主题:背景改暖米白 `#f4efe9` + 纯白卡片 + 页面两团极淡品牌径向光晕(深色去掉);删除其余 12 套调色板,只保留 5 个品牌强调色(陶土铁锈默认 / 砖红 / 赭黄 / 暗松绿 / 灰紫,共用 surfaces)。`useTheme.js` PALETTES 缩为 5、默认 `warm`(旧 localStorage 失效值自动回落);`main.css` 重写 `:root`/`.dark` + 强调色变体 + body 光晕。

- 2026-06-05 — 租户管理列表按 Claude Design 稿重构:字母徽标 + 双行单元格 + 语义状态点 + 相对时间 + 悬停操作 + 表格/卡片切换 + 状态分段筛选(后端新增可选 `status` 参数)+ 详情抽屉 + 可折叠回收站提示。颜色/字体走 app 主题令牌(适配主题强调色+深色),设计稿中后端无的字段(plan/users/region/owner)省略。新增 `TenantRowActions.vue`、5 语言 `platform.tenant.list.*` 文案、`list_statusFilter_addsStatusPredicate` 单测。

- 2026-06-05 — 平台 dashboard 拆出独立「平台总览」页(`/platform/overview`,菜单 `platform.overview`,门控 `platform:tenant:read`,V56):KPI/图表/4 监控面板从租户管理页移出,租户表格首屏即可见。另:活跃与参与卡曲线图上方加「登录趋势(14天)」小标题。

- 2026-06-05 — 停用/删除运维用户时同时 ForceLogout 踢下线(在飞 token 立即失效);启用清除踢出标记。

- 2026-06-05 — 全项目邮箱输入统一前端格式校验(`@/lib/validators` 的 `isValidEmail` + `common.message.invalidEmail`):平台用户、租户新建/编辑、重发邀请、用户编辑;后端各 DTO 本就有 `@Email`。

- 2026-06-05 — 新建运维用户:email 改必填 + 单段名也填 lastName,使首登跳过 UPDATE_PROFILE(不再一进来就报"请指定此字段")。
- 2026-06-05 — 修复 KC 主题多字段表单(更新账户/修改密码)排版:必填 `*` 回到 label 同行、收紧 label/字段间距(`.pf-*__label-text` 不再被设成 block)。
- 2026-06-05 — 修复 KC 登录主题 `doSubmit` 被改成"发送重置链接"导致"设置/修改密码"页按钮文案错误;改回通用"提交"。
- 2026-06-05 — 平台运维用户控制台(两级:ops / PLATFORM_OPERATOR;新建/停用/删除/重置密码)。
- 2026-06-05 — 支持会话服务端追踪(`core_support_session`)+ 安全面板可选卡。
- 2026-06-05 — 领域事件控制台(列表 + 重发)+ demo task emit 事件 + outbox 保留作业。
- 2026-06-04 — 租户管理改 dashboard(KPI/图表 + 4 监控面板)+ break-glass 审计 + oplog error_code。
- 2026-06-04 — 修复多 tab keep-alive 重复请求;平台菜单图标修正 + 拍平。
