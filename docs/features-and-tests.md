# 基盤機能 & テストポイント / 基盘功能 & 测试点

> **维护约定（重要）**：本文件是**当前功能 + 怎么用 + 手动测试点**的单一来源——描述"这个项目有哪些功能、怎么用"的**现状快照**,不是变更日志。
> **每次新增或改动功能,就地更新对应小节**使其反映最新行为,并保持其测试点最新(可勾选清单)。**只同步最新状态,不记录历史/变更履历。**
> AI 助手在实现或修改功能后也应主动更新本文件,把它当作"完成"的一部分(像更新测试一样)。

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
- [ ] 会话失效(被踢下线 / 停用 / 过期且刷新失败)→ 下次请求(如 `/menu/me`)跳 `/login` 并显示友好「会话已过期或已被登出,请重新登录」,**不**再显示技术性的「Menu load failed — 401」(router 守卫把 401 与真正的 menu 加载错误 5xx/坏数据 分开处理,后者才带 detail 供排障)。
- [ ] 多 realm:在 A 租户的账号无法登录 B 租户。
- [ ] 新建运维用户用临时密码首登 → KC「修改密码」页,按钮是通用「提交」(不是「发送重置链接」)。Keycloak 主题 `doSubmit` 必须保持通用(多页面共用),不要写成某页专属文案。
- [ ] KC 多字段表单(更新账户信息 / 修改密码)排版整齐:必填 `*` 紧跟 label **同一行**,label↔input、字段↔字段间距正常(不因 `.pf-*__label-text` 被设成 block 而拉开)。
- [ ] **SSO 页跟随应用明暗**:在应用内切到深色(即使系统为亮色)后点登录跳到 KC,登录页为深色;切回亮色则 KC 为亮色;登录失败重渲染后仍保持(sessionStorage `am_ui_mode` 兜底)。亮色保持 navy/gold 品牌入口;**暗黑模式套用应用暖黑配色**——暖近黑背景 + 暖米白文字 + 陶土橙强调(主按钮/输入聚焦环/复选框选中/链接 hover),金色 logo 保留。机制:前端在 `/authorize`(及忘记密码 `reset-credentials`)URL 追加 `ui_mode=dark|light`(`oidc.js`,取自 `<html>.dark`),KC 主题 `login.js` 读取并在 `<html>` 设 `data-theme`(头部同步脚本,无闪烁);KC `access-matrix.css` 的 `[data-theme="dark"]` 重定义 `--am-*` token。改 KC 主题文件需**重启/重同步 KC**(`start-keycloak.bat` 每次启动 xcopy 同步)才生效。

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
- [ ] 菜单管理**操作列**:ops(`*:*`)能看到增删改按钮——写权限码是 `platform:menu:*`,前端 `v-permission` 必须带 `platform:` 前缀(`*:*` 只匹配 `platform:` 命名空间,不匹配 2 段 `menu:*`);业务 super(`tenant:*`)看不到写按钮,但 `menu:read` 仍可用于 RoleEdit 菜单选择器。
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
独立菜单页 `/platform/overview`(菜单 `platform.overview`,门控 `platform:tenant:read`,排在租户管理之前)。按 Claude Design 稿重做成 **bento 网格仪表盘**:顶部「最后更新 + 刷新」;4 张 KPI 卡(图标在左 + 大数字 + delta:总租户/运行中/已停用/本月新增,数据 `/platform/tenants/stats`);状态环形图(圆心放总数,图例运行中/已停用 + 占比);按月新增趋势(**手绘 SVG 面积线,直线段不过冲**);入驻漏斗(tiles + 激活率条);活跃与参与(tiles + 14天登录趋势 + 沉默租户列表);平台健康(tiles + 全部正常/需关注 pill);安全与高权限监视(tiles + 审计列表:支持会话/应急访问,严重度色点 + 字母徽标 + actor→target + 相对时间),数据 `/platform/dashboard`。**保留原有点选切换明细功能**:入驻(待激活/已过期)、平台健康(任务失败/事件积压/最久积压/接口错误)、安全(支持会话/应急访问)的 tile 可点击,切换其下明细列表,默认选中第一个有值的 tile。**平台健康明细每行末尾有小眼睛**,点击弹出抽屉显示该行全部字段(错误信息不截断)。入驻漏斗只统计真实客户租户,内置租户(`system`/`demo`,含平台运维用户的邀请链接)**不计入**待激活/已过期。颜色走 app 主题令牌(适配强调色+深色);图表为自建组件 `AreaChart.vue`(已移除 echarts 与旧 `DashboardPanels.vue`)。**与 2.1 租户管理分页**:dashboard 独立成页,租户表格首屏即可见。
**测试点**
- [ ] 「平台总览」作为独立菜单出现(在「租户管理」之前);ops 与 operator 都能看到。
- [ ] KPI/图表/tiles 显示真实聚合(非 mock);切换强调色+深色模式整页配色正常。
- [ ] 环形图圆心显示总租户数;趋势/登录折线为**直线段**,近 0 数据不出现假驼峰、不下探基线。
- [ ] 平台健康全为 0 → 绿色「一切正常」pill;有任一 >0 → 黄色「需关注」pill。
- [ ] 安全审计列表:支持会话(进行中=红点/历史=黄点)+ 应急访问(火苗图标),相对时间,空时显示「暂无」。
- [ ] **点选切换明细**:入驻/平台健康/安全的 tile 点击切换其下明细列表(待激活↔已过期、任务失败↔积压↔接口错误、支持会话↔应急访问),默认选中第一个有值 tile,选中 tile 高亮主色边框。
- [ ] 活跃与参与卡显示沉默租户列表;明细列表为空显示「暂无」。
- [ ] 平台健康每条明细行末尾小眼睛,点击弹抽屉显示全部字段(任务/事件/接口错误对应不同字段,错误信息完整不截断)。
- [ ] 入驻漏斗「待激活/已过期」**不含** `system`/`demo`:新建平台运维用户(邀请链接未激活)不会让 `system` 出现在待激活租户里。
- [ ] 点「刷新」重新拉取 stats + dashboard 并更新「最后更新」时间。

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
`/platform/users`(`opsuser:*`,仅 ops 可见):列表、新建(KC system realm 用户 + 授 **PLATFORM_OPERATOR**;**与「重发邀请」完全相同的逻辑(plan B)**——KC 用户**不设临时密码**,`mint` 一次性 `/invite/{token}` 链接 + 发 `user-invite` 邮件,用户在落地页自助设永久密码;**页面不弹临时密码、仅 toast**(邀请已发/失败);**走应用 `CORE_MAIL_*`,不依赖 KC realm SMTP**;邮件失败不影响创建,可用「重发邮件」补发)、**编辑(改显示名/邮箱,用户名不可改;同步 KC `updateProfile` + `core_auth_user`)**、停用/启用、**重置密码(轮换临时密码 → 当前密码立即失效;弹窗显示新临时密码作后备 + 发「密码重置」邮件,该邮件**仅含临时密码**;confirm 提示会重置当前密码;并**踢下线**——已登录会话的旧 token 立即失效、需重登)**、**重发邀请邮件(plan B:`mint` 一次性 `/invite/{token}` 链接 + 发 `user-invite` 邮件,用户在落地页**自助设永久密码**;不动当前密码、页面不弹临时密码仅 toast;先作废该用户旧的未用 invite,只最新链接有效)**、**踢下线(ForceLogout:使现有会话立即失效、需重新登录;账号不停用,可立即重登)**、删除。编辑/重置/重发/踢下线走 `opsuser:update`。登录链接指向应用 `/login`(走 SSO 到 KC,不直接暴露 KC UI)。常规运维(PLATFORM_OPERATOR=`*:*`)**看不到本页**。
**测试点**
- [ ] ops 能看到「运维用户」菜单;常规运维**看不到**(`opsuser:*` 不被 `*:*` 覆盖),直接访问 API 403。
- [ ] 新建用户 → toast「邀请邮件已发送至 {email}」(不弹临时密码);新用户点邮件链接落地页自助设密、再登录。
- [ ] 新用户能管租户/事件等,但**看不到运维用户菜单**(只比 ops 低一档)。
- [ ] 停用→该用户无法登录;启用→恢复;重置密码→弹新临时密码;删除→DB 软删 + KC 用户删除。
- [ ] 不能对**自己**或**ops(超级)**执行停用/删除/重置(行内按钮隐藏 + 后端拦截)。
- [ ] 停用/删除会**立即踢下线**(ForceLogout):被停用用户**当前已登录的会话**下次请求即被拒(不只是阻止再次登录);启用会清除踢出标记。
- [ ] 新建表单必填项 `*` 为红色;邮箱有格式校验(前端正则 + 后端 `@Email`),格式不对被拦。
- [ ] 新建重复检测**精确**:用户名已存在 → 「用户名已被占用」;邮箱已存在 → 「该邮箱已被占用」(在写 Keycloak 之前分别预检,不再把邮箱冲突笼统报成用户名重复)。
- [ ] 新建用户邮箱**必填**;首登**不**经过"更新账户信息"页(不报"请指定此字段"),直接进设置密码——KC 用户的 email/firstName/lastName 创建时已补全(单段名 lastName 也填)。
- [ ] 状态徽标有颜色区分:启用=绿(success)、禁用=红(danger)。
- [ ] 编辑:改某用户的显示名/邮箱 → 列表即时更新;用户名为只读(改不了,提示删重建);邮箱格式校验;Keycloak 用户的 email/firstName/lastName 同步更新。
- [ ] 新建(plan B,与重发相同):创建成功 → **toast「邀请邮件已发送至 {email}」**(不弹临时密码);用户收到 `user-invite` 邮件 → 点 `/invite/{token}` 落地页**自助设永久密码** → 登录。邮件发送失败 → toast「已创建但邮件失败,请重发」,且**创建本身不失败**(`CORE_MAIL_*` 未配时同理)。
- [ ] **重置密码**:confirm「会重置当前密码」→ 轮换临时密码(原密码立即失效)+ 弹窗显示新临时密码(后备)+ 发「密码重置」邮件(**仅含临时密码**,无登录地址/用户名/租户行)+ **踢下线**(已登录会话下次请求被拒、需重登)。走应用 `CORE_MAIL_*`。
- [ ] **重发邀请邮件**(plan B):点重发 → 用户收到 `user-invite` 邮件,点 `/invite/{token}` 落地页**自助设永久密码** → 可登录;页面**不弹**临时密码(仅 toast);**不改当前密码**(用户完成链接前仍可用旧密码)。
- [ ] **踢下线**(独立操作):点踢下线 → confirm → ① 应用层 `kickOut` 使已签发 token 失效(下次请求被拒)② KC `logoutUser` 结束其 SSO 会话。跳 `/login` 后**不会被 SSO 静默重登**(KC 会话已结束 → 要求重新输密码);账号不停用,输密码后可重登。**重置密码同样触发**(kickOut + KC logout),否则旧 KC 会话会让 SSO 自动重登、踢下线无效。
- [ ] **邀请链接单次有效(重复使用 bug 修复)**:用 invite 链接设密成功后,**再次打开/提交同链接失效**(原子 `markUsed` 把 `used_at` NULL→now 并检查影响行数,0 行即拒绝;此前 SELECT+UpdateWrapper 不查行数,导致可重复设密)。链接也有时效(默认 7 天);重发会先作废旧链接,只最新有效。

---

## 3. 共通基盤（Cross-cutting）

- **字典(Dictionary)**:内置枚举字典 + 运行时可编辑的管理字典;前端 `useDict`,下拉/标签均走字典,不硬编码。`/platform/dicts` 管理。
- **操作审计(oplog)**:`@OpLog` 写 `core_oplog`;失败行带 `error_code`(BusinessException 的 4xx/7xx vs 未预期 500)。
- **领域事件出箱**:状态变更在同一事务 `EventPublisher.publish(...)` → `core_domain_event` → `OutboxDispatcher` → 可插拔 sink(当前为日志兜底)。详见 backend/AGENTS.md。
- **通知(铃铛)**:`core_notification` + SSE 心跳(15s)。注意与领域事件是两套东西。
- **编号(numbering)**:`core_numbering*`,按租户分配 user_no 等。
- **i18n**:ja_JP / zh_CN / zh_TW / en / ko_KR 五语;权限标签由后端 `I18nPermissionPatcher` 生成占位 `__TODO__`,需人工填 `frontend/src/lang/generated/permissions.*.json`。
- **邮件品牌 logo**:所有邮件模板(`user-invite` / `user-direct-welcome` / `user-password-reset` / `user-break-glass-used`,各 5 语言)header 含品牌 logo;**外链 PNG** `${baseUrl}/access_matrix_logo.png`(由 `frontend/public/access_matrix_favicon.svg` 渲染成位图——邮件客户端不显示 SVG)。`MailService` 对每封邮件统一注入 `logoUrl`(`putIfAbsent`,调用方可覆盖)。

**测试点**
- [ ] 字典:新增/停用管理字典项;被引用的项不可删(报"in use")、枚举项不可删——这些是**业务拒绝**,不计入仪表盘"接口错误"。
- [ ] 任一带 `@OpLog` 的操作在 `core_oplog` 留痕;失败时 `error_code` 正确。
- [ ] 语言切换:5 语言下新功能文案都不出现 key 原文 / `__TODO__`。
- [ ] 通知:触发后铃铛出现未读、可标记已读。
- [ ] 邮件 header 显示品牌 logo:在 Gmail/Outlook 等也能显示(因用 **PNG** 而非 SVG);logo 加载不出时下方文字「Access Matrix」兜底。`logoUrl` 指向 `${baseUrl}/access_matrix_logo.png`(前端静态资源,需前端已部署/可访问)。

---

## 4. フロントエンド基盤（Frontend foundation）

- **多 Tab + keep-alive**:目录占位 `EmptyLayout` 单实例复用(修复了"多 tab 后新 tab 发重复请求")。
- **Tab 栏(Browser 风)**:`AppTabBar`。非激活 tab 之间用 `::before` 竖分割线分隔(相邻激活/hover 时隐藏);tab `flex-1 min-w-[56px] max-w-[120px]` + `truncate` 均匀缩小;缩到极限不向右延伸,多出的收进右侧 `+N`(`ResizeObserver` 测宽算 `visibleCount`,始终保证 active 可见),hover `+N` 弹出隐藏 tab 列表;标题被截断时 hover 在 tab 下方弹完整名 tooltip(`pointer-events-none`,Teleport 到 body 避免被裁,仅 `scrollWidth > clientWidth` 时弹)。拖拽排序 / 右键菜单 / 末端批量操作保留。
- **详细 tab 业务标识**:`tabsStore.tabExtras`(**不持久化**的内存 map,`fullPath → {prefixKey, badge}`)+ `setTabExtra`。业务详细页在自身 `loadDetail` 内调用(切忌用依赖全局 route 的 watch,keep-alive 缓存下会污染别的 tab);`tabLabel` 读它把 `予約番号/施設名` 等业务标识拼到「XX-詳細」后,`prefixKey` 存 i18n key 以跟随语言。基盘暂无业务详细页,此为预置基础设施。
- **确认弹窗**:全项目用 `useConfirm()` 的 `confirm()`(Promise)+ 常驻 `<ConfirmDialog>`(无 `window.confirm`);`ConfirmDialog` z-index = `z-[100]`,高于 `Dialog`(`z-[90]`),保证从 Dialog 内触发删除时确认框可见可点(否则被遮 → Promise 永不 resolve → "点击无反应")。
- **侧边栏**:收藏 / 置顶 / 顶层叶子菜单提升 / 折叠 flyout。
- **主题 / 调色板**:CSS 变量;`.dark` + `data-palette`。单一暖色主题(暖米白 `#f4efe9` 背景 + 纯白卡片 + 页面背景两团极淡品牌径向光晕,深色模式去掉光晕),仅**品牌强调色**可切:陶土铁锈(默认)/ 砖红 / 赭黄 / 暗松绿 / 灰紫;surfaces 五色共用,只换 `--primary/--accent/--ring/--brand-orange`。**深色用 `.dark` class 切换**(`useTheme.js`),`main.css` 必须配 `@custom-variant dark (&:where(.dark, .dark *))`,否则 Tailwind v4 的 `dark:` 默认跟随系统 `prefers-color-scheme`,"系统亮色 + 手动深色"时全项目 `dark:` 静默失效。主题在 app **启动时即应用**(`main.js` 引入 `useTheme`,模块副作用给 `<html>` 加 `.dark`/`data-palette`),使**未登录的 `/login` 等首屏**也跟随;否则 `useTheme` 只被登录后的 `AppHeader` 引入,登录页会停在亮色、且 SSO 跳转会误传 `ui_mode=light`。

**测试点**
- [ ] 打开多个 tab,再开新 tab:每个接口**只发一次**(无重复请求)。
- [ ] 关闭 tab 不残留 404;双击 tab 刷新该页。
- [ ] Tab 栏:非激活 tab 间有竖分割线(激活/hover 及其相邻处隐藏);窗口变窄时 tab 均匀缩小并 `…` 截断,不向右溢出;再窄则多出的收进 `+N`,active 始终可见;hover `+N` 弹隐藏列表可点选/关闭;hover 被截断的 tab 在其下方弹完整名 tooltip。
- [ ] 删除确认:从抽屉/Dialog **内部**触发删除时,确认框浮在最上层可点(不被 Dialog 遮);取消/确认都能正常 resolve。
- [ ] 收藏/置顶菜单显示在顶部;折叠侧栏 hover 出 flyout。
- [ ] 切主题/调色板,图表与界面颜色随之更新。
- [ ] **深色模式**:系统设为亮色、应用内手动切深色,所有 `dark:` 工具类(登录页 / 租户行 / 徽标 / Header / Break-glass 弹窗等)正常生效,无局部变白。
- [ ] **登录页跟随主题**:选深色后退出到 `/login`(或 SSO 跳转过渡页),登录页本身为深色(`<html>` 启动即带 `.dark`);此时点 SSO 登录,KC 也收到 `ui_mode=dark`(印证 `currentUiMode()` 读得到 `.dark`)。
