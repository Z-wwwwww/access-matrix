/**
 * 简体中文
 *
 * Mirror of ja_JP.js — see that file for the key-tree documentation.
 */
import permissions from './generated/permissions.zh_CN.json'

export default {
  error: {
    common: {
      concurrentEdit: '该记录已被他人修改，请刷新后重试。',
      duplicateKey: '该名称或编码已被占用，请刷新后换一个值重试。'
    },
    dict: {
      codeExists: '字典编码已存在',
      codeIsBuiltin: '该编码是内置（枚举）字典，不可管理',
      typeBuiltinProtected: '内置字典类型不可删除',
      typeHasItems: '字典下仍有字典项，请先删除',
      itemsNotEditable: '内置字典项不可编辑',
      itemValueExists: '字典项值已存在',
      itemBranchProtected: '该值由代码（枚举）定义，不可删除，请改为停用',
      itemInUse: '该值已被现有数据引用，不可删除，请改为停用',
      notFound: '字典或字典项不存在',
      invalidValue: '无效的字典值'
    },
    opsuser: {
      usernameExists: '用户名已被占用',
      emailExists: '该邮箱已被占用'
    },
    user: {
      usernameExists: '用户名已被占用',
      emailExists: '该邮箱已被占用',
      passwordRequired: '直接设密方式需要填写密码',
      emailRequired: '邀请邮件方式需要填写邮箱',
      superAdminSingleton: '超级管理员角色为租户所有者保留，不能授予其他用户',
      roleNotFound: '所选角色中有的已不存在于本租户，请刷新页面后重试',
      adminProtected: '该管理员账号受保护，其他用户不能修改、停用、删除或强制登出；管理员本人可在「个人资料」页修改自己的信息',
      noKeycloakLink: '该用户未关联 Keycloak 账号，无法重置密码',
      selfManagementForbidden: '不能在此管理自己的账号，请在「个人资料」页修改自己的信息'
    },
    role: {
      superPermissionReserved: '租户级超级权限为内置超级管理员角色保留，不能分配给自定义角色'
    },
    auth: {
      tenantSuspended: '租户已被停用，无法登录'
    },
    keycloak: {
      operationFailed: 'Keycloak 拒绝了该操作(可能不符合用户名长度/格式等策略),请检查输入'
    },
    invite: {
      notFoundOrUsed: '邀请链接无效或已被使用',
      expired: '邀请链接已过期',
      invalid: '邀请链接已失效'
    }
  },
  dict: {
    title: '字典管理',
    description: '统一管理代码内置字典（status 等）与运行时可编辑的管理字典。',
    type: {
      columnCode: '编码', columnName: '名称', columnItems: '项数', columnActions: '操作',
      new: '新增字典', edit: '编辑字典', create: '创建字典',
      builtin: '内置', selectHint: '选择左侧字典查看其字典项', empty: '暂无字典',
      codeFrozen: '编码创建后不可修改'
    },
    item: {
      columnValue: '值', columnLabel: '标签', columnSort: '排序', columnStatus: '状态', columnActions: '操作',
      new: '新增项', edit: '编辑项', create: '创建项',
      enabled: '启用', disabled: '停用', empty: '暂无字典项',
      valueFrozen: '值创建后不可修改（历史数据会引用）'
    },
    label: {
      code: '编码', name: '名称', remark: '备注', value: '值', label: '标签',
      sort: '排序', status: '状态', cssClass: '颜色'
    },
    message: {
      saveSuccess: '保存成功', saveFailed: '保存失败',
      deleteSuccess: '删除成功', deleteFailed: '删除失败', loadFailed: '加载失败'
    },
    confirm: {
      deleteTypeTitle: '删除字典', deleteTypeMessage: '确认删除字典「{code}」？',
      deleteItemTitle: '删除字典项', deleteItemMessage: '确认删除字典项「{value}」？通常停用更安全。'
    }
  },
  job: {
    search: { placeholder: { keyword: '按任务名 / 编码搜索' } },
    triggerType: { cron: '定时', manual: '手动', startup: '启动' },
    runStatus: { running: '运行中', success: '成功', fail: '失败', skipped: '跳过', none: '—' },
    column: { label: '名称', name: '任务', cron: 'Cron 表达式', status: '状态', nextFire: '下次执行', lastResult: '最近结果', actions: '操作' },
    action: { edit: '编辑 Cron', run: '立即执行', viewLog: '日志' },
    edit: { title: '任务设置', label: { cron: 'Cron 表达式', maxRunSeconds: '最大执行秒数', concurrent: '允许重复执行', remark: '备注' }, tip: { concurrent: '上一次执行还没结束时，是否并行启动下一次。关闭（默认）：本次触发被跳过，同一任务不会重叠（由分布式锁保证）。仅当任务可安全并行时才开启。' }, placeholder: { cron: '例: 0 0 3 * * *', remark: '备注(可选)' } },
    log: { title: '执行日志', column: { triggerType: '触发', status: '状态', startTime: '开始', duration: '耗时(ms)', node: '节点', triggeredBy: '执行人', error: '错误' }, empty: '暂无执行历史' },
    confirm: { runTitle: '立即执行', runMessage: '确定立即执行一次「{name}」吗?' },
    message: { saveSuccess: '已保存', runStarted: '已开始执行', enabled: '已启用', disabled: '已停止', updateFailed: '保存失败', runFailed: '执行失败' }
  },
  common: {
    // 星期标签，周日开头（与 dayjs .day() 的 0=周日一致）。逗号分隔，
    // 调用方 split(',') 后按星期序号取值。
    weekStrings: '周日,周一,周二,周三,周四,周五,周六',
    button: {
      search: '搜索',
      reset: '重置',
      save: '保存',
      cancel: '取消',
      confirm: '确认',
      delete: '删除',
      forceDelete: '强制删除',
      edit: '编辑',
      new: '新增',
      detail: '详情',
      apply: '应用',
      clear: '清空',
      selectAll: '全选',
      back: '返回',
      close: '关闭',
      refresh: '刷新',
      export: '导出',
      import: '导入',
      upload: '上传',
      download: '下载',
      submit: '提交'
    },
    confirm: {
      forceTitle: '强制删除',
      forceMessage: '{detail}\n\n强制删除将清除所有关联引用并使其失效。是否继续？'
    },
    status: {
      active: '启用',
      inactive: '禁用',
      enabled: '启用',
      disabled: '禁用',
      builtIn: '内置'
    },
    message: {
      saveSuccessful: '保存成功',
      saveFailed: '保存失败',
      deleteSuccessful: '删除成功',
      loading: '加载中',
      processing: '处理中...',
      sending: '发送中...',
      loginSuccessful: '登录成功',
      networkError: '网络错误',
      sessionExpired: '会话已过期',
      operationSuccessful: '操作成功',
      iframeLoadFailed: '无法加载外部页面',
      invalidEmail: '邮箱格式不正确'
    },
    label: {
      keyword: '关键词'
    },
    placeholder: {
      keyword: '关键词',
      pleaseInput: '请输入',
      pleaseSelect: '请选择',
      search: '搜索...',
      deptId: '请选择部门'
    },
    tooltip: {
      pagePrevious5: '向前 5 页',
      pageNext5: '向后 5 页'
    },
    datePicker: {
      today: '今天',
      now: '现在',
      year: '年',
      yearMonth: '年月',
      placeholder: '选择日期',
      startPlaceholder: '开始日期',
      endPlaceholder: '结束日期',
      dateTimePlaceholder: '选择日期时间',
      timePlaceholder: '选择时间',
      confirm: '确定'
    }
  },

  layout: {
    header: {
      profile: '个人资料',
      password: '修改密码',
      breakGlass: '应急密码',
      logout: '退出登录',
      userFallback: '用户'
    },
    sidebar: {
      adminGroup: '管理员设置',
      favorites: '收藏',
      favorite: '收藏',
      unfavorite: '取消收藏'
    },
    tabs: {
      tabAction: '标签操作',
      closeCurrent: '关闭当前标签',
      closeOthers: '关闭其他标签',
      closeAll: '关闭所有标签'
    },
    footer: {
      copyright: 'Copyright © 2026 Access Matrix'
    },
    notification: {
      title: '通知',
      empty: '暂无通知',
      markAllRead: '全部标为已读',
      actionRequired: '待处理'
    }
  },

  login: {
    identifierLabel: '用户名 / 邮箱 / 编号',
    identifierPlaceholder: '用户名 · 邮箱 · 用户编号',
    passwordLabel: '密码',
    passwordPlaceholder: '请输入密码',
    tenantLabel: '租户',
    tenantPlaceholder: 'demo',
    showAdvanced: '高级（切换租户）',
    hideAdvanced: '收起',
    submit: '登录',
    submitting: '登录中...',
    forgotPassword: '忘记密码？',
    ssoDivider: '或',
    ssoButton: 'SSO 登录',
    ssoOnlyHint: '请使用单点登录 (SSO) 进入系统。',
    ssoRedirecting: '正在跳转到 SSO...',
    ssoUnreachable: {
      title: 'SSO 服务器无法访问',
      body: 'Keycloak（SSO）当前没有响应。可能正在维护、重启，或网络出现问题。可以使用应急密码继续登录，或稍后再试。',
      useBreakGlass: '使用应急密码登录',
      retry: '重试 SSO',
      retrying: '检测中...'
    },
    tenantRecovered: '租户 "{stale}" 已不存在，已自动切换回默认租户。',
    passwordBreakGlass: '密码登录模式（应急）',
    backToSso: '返回 SSO',
    passwordModeHotzone: '2 秒内连击 5 次可解锁密码登录',
    message: {
      enterUsername: '请输入用户名',
      enterPassword: '请输入密码',
      loginFailed: '登录失败',
      sessionExpired: '会话已过期或已被登出，请重新登录。',
      noAccess: '您的账号暂无可访问的页面，请联系管理员为您分配角色。',
      ssoFailed: 'SSO 登录失败'
    }
  },

  platform: {
    event: {
      title: '领域事件',
      column: { occurredAt: '发生时间', eventType: '事件类型', aggregate: '聚合', status: '分发状态', attempts: '尝试', actions: '操作' },
      state: { all: '全部', pending: '待分发', dispatched: '已分发', failed: '失败' },
      actorType: { human: '人', ai: 'AI', system: '系统' },
      search: { eventTypePlaceholder: '按事件类型筛选', keywordPlaceholder: '聚合ID / 类型 / traceId' },
      button: { redriveAll: '批量重发失败' },
      tooltip: { view: '查看详情 / payload', redrive: '重发（重置为待分发）', redriveOnlyFailed: '仅失败事件可重发' },
      drawer: { title: '事件详情', payload: '负载', actor: '执行者', dispatchedAt: '分发时间', traceId: '追踪ID' },
      confirm: {
        redriveTitle: '重发事件', redriveMessage: '将「{eventType}」重置为待分发并重新投递吗？', redriveConfirm: '重发',
        redriveAllTitle: '批量重发失败事件', redriveAllMessage: '将所有失败事件重置为待分发并重新投递吗？', redriveAllConfirm: '批量重发'
      },
      message: { redriveSuccess: '已请求重发', redriveFailed: '重发失败', redriveAllSuccess: '已重发 {count} 条', loadFailed: '加载事件失败' }
    },
    user: {
      title: '平台用户',
      column: { username: '用户名', displayName: '显示名', email: '邮箱', role: '角色', status: '状态', createTime: '创建时间', actions: '操作' },
      role: { admin: '管理员', operator: '运维' },
      action: { disable: '停用', enable: '启用', reset: '重置密码', delete: '删除', edit: '编辑', resend: '重发邮件', forceLogout: '踢下线' },
      edit: {
        title: '编辑平台用户',
        intro: '修改显示名 / 邮箱（同步到 Keycloak）。用户名是登录标识，不可修改。',
        usernameReadonly: '用户名不可修改；如需更改请删除后重建。',
        save: '保存',
        saving: '保存中...'
      },
      confirm: {
        disableTitle: '停用用户', disableMessage: '确认停用「{username}」？Keycloak 也会被禁用，无法登录。',
        resetTitle: '重置密码', resetMessage: '确认重置「{username}」的密码？当前密码将立即失效；会生成新的临时密码并（若有邮箱）发送到其邮箱。',
        deleteTitle: '删除用户', deleteMessage: '确认删除「{username}」？不可恢复（同时删除 Keycloak 用户）。',
        resendTitle: '重发邮件', resendMessage: '向「{username}」（{email}）重发邀请邮件？对方可凭邮件中的一次性链接自行设置新密码（链接有时效、仅可使用一次）。',
        forceLogoutTitle: '踢下线', forceLogoutMessage: '强制「{username}」下线？其当前所有会话立即失效、需重新登录（账号不会被停用）。'
      },
      secret: { titleNew: '已创建平台用户', titleReset: '已重置密码' },
      search: { placeholder: '按用户名 / 邮箱搜索' },
      button: { new: '新建用户' },
      create: {
        title: '添加平台用户',
        intro: '在 system 租户开通运维人员：在 Keycloak 的 system realm 创建用户，并授予 Platform Admin 权限。',
        usernamePlaceholder: 'ops2',
        usernameHint: '小写字母数字、连字符、下划线；创建后不可修改。',
        displayNamePlaceholder: '运维张三',
        emailPlaceholder: 'ops2@example.com',
        creating: '创建中...',
        doneIntro: '已创建「{username}」。',
        emailSent: '凭据邮件已发送至 {email}（含登录地址、用户名、临时密码；用户登录后需修改密码）。',
        emailNotSent: '邮件未发送（请检查应用邮件配置 CORE_MAIL_*）。可先转交下方临时密码，或稍后在列表中重发。',
        resetIntro: '已为「{username}」重置密码，新的临时密码如下（对方首次登录需修改）。',
        resendIntro: '已为「{username}」重发开户邮件，并重置了临时密码（原密码已失效）。新的临时密码如下。',
        tempPassword: '临时密码',
        tempPasswordHint: '关闭此页后将无法再次查看。',
        copy: '复制'
      },
      message: {
        loadFailed: '加载用户列表失败', required: '请填写所有必填项',
        invalidUsername: '用户名需 3-64 位，小写字母或数字开头，仅含小写字母、数字、连字符、下划线',
        createSuccess: '已创建平台用户', createFailed: '创建用户失败',
        createdInviteSent: '已创建，邀请邮件已发送至 {email}（对方点击链接自行设置密码）。', createdInviteFailed: '已创建，但邀请邮件发送失败，请稍后用「重发邮件」重试。',
        copied: '已复制', copyFailed: '复制失败',
        disableSuccess: '已停用', enableSuccess: '已启用', resetSuccess: '已重置密码',
        deleteSuccess: '已删除', updateSuccess: '已更新', resendSuccess: '邮件已发送', forceLogoutSuccess: '已踢下线', opFailed: '操作失败'
      }
    },
    tenant: {
      column: {
        tenantCode: '租户代码',
        displayName: '显示名称',
        contactEmail: '联系邮箱',
        status: '状态',
        userCount: '用户数',
        createTime: '创建时间',
        actions: '操作'
      },
      status: { active: '运行中', suspended: '已停用' },
      search: { placeholder: '按代码或名称搜索' },
      list: {
        viewTable: '表格视图', viewCards: '卡片视图',
        segAll: '全部',
        showing: '显示 {shown} / {total} 个租户',
        perPage: '{n} / 页',
        noMatch: '没有匹配「{q}」的租户。',
        clearFilters: '清除筛选',
        noAdmin: '尚无管理员',
        builtIn: '内置',
        overview: '概览',
        fieldRealm: 'KC realm',
        fieldUpdated: '更新时间',
        actSupport: '支持会话',
        actResend: '重发邀请'
      },
      overview: {
        updated: '最后更新', refresh: '刷新',
        allRealms: '全部租户', uptime: '运行中', recycleBin: '回收站', thisMonth: '本月',
        healthOk: '一切正常 · 无故障', healthIssues: '有需关注项'
      },
      dashboard: {
        total: '总租户',
        newThisMonth: '本月新增',
        statusTitle: '状态分布',
        trendTitle: '新增趋势（按月）',
        trendSeries: '新增租户'
      },
      ops: {
        empty: '暂无',
        activation: {
          title: '入驻 / 激活漏斗', pending: '待激活', expired: '已过期', rate: '激活率',
          median: '入驻时长(中位)', listTitle: '待激活租户(邀请未使用)', expiredBadge: '已过期',
          tip: {
            pending: '已发送邀请但管理员尚未激活的租户数',
            expired: '邀请已过期且从未激活的租户数',
            rate: '非内置租户中管理员已成功登录过的比例',
            median: '从创建租户到管理员首次登录的时长中位数'
          }
        },
        engagement: {
          title: '活跃与参与', active7d: '活跃(7天)', active30d: '活跃(30天)', dau: 'DAU', mau: 'MAU',
          silent: '沉默', trendTitle: '登录趋势(14天)', silentListTitle: '沉默租户(30天无登录)', never: '从未登录',
          tip: {
            active7d: '近7天有成功登录的租户数',
            active30d: '近30天有成功登录的租户数',
            dau: '近24小时活跃用户数(按用户去重)',
            mau: '近30天活跃用户数(按用户去重)',
            silent: '运行中但近30天无登录的租户数(流失预警)'
          }
        },
        reliability: {
          title: '平台健康', jobFailures: '任务失败(24h)', eventBacklog: '事件积压',
          backlogAge: '最久积压', oplogErrors: '接口错误(24h)', failuresListTitle: '最近任务失败',
          errorsListTitle: '最近接口错误', statePending: '待分发', stateFailed: '失败',
          detail: {
            title: '详情', jobCode: '任务', startTime: '开始时间', duration: '耗时', error: '错误',
            eventType: '事件类型', aggregateType: '聚合', state: '状态', attempts: '尝试次数',
            occurredAt: '发生时间', api: '接口', username: '用户', errorMsg: '错误信息', time: '时间'
          },
          tip: {
            jobFailures: '近24小时失败的定时任务次数',
            eventBacklog: '未分发的领域事件数(待分发+失败)',
            backlogAge: '最旧未分发事件的滞留时长',
            oplogErrors: '近24小时非业务的服务端错误(500)数'
          }
        },
        security: {
          title: '安全与高权限监视', activeSupport: '支持会话进行中', support7d: '支持会话(7天)',
          loginFailures: '登录失败(24h)', passwordResets: '密码重置(7天)', supportListTitle: '最近支持会话',
          breakGlass: '应急访问(7天)', breakGlassListTitle: '最近应急访问(绕过SSO)'
        }
      },
      recycleBinHint: {
        title: '删除采用回收站方式：',
        body: '请先「停用」（Keycloak realm 将被禁用、无法登录，但数据保留）。如需彻底删除，请在停用后的行中点击红色垃圾桶图标 —— 输入租户代码确认后，业务数据、KC realm、注册表条目将全部物理删除，且无法恢复。'
      },
      edit: {
        titleCreate: '新建租户',
        titleEdit: '编辑租户',
        intro: '一次操作创建 Keycloak realm + 中央注册表条目。租户代码创建后不可修改。',
        editIntro: '租户代码不可修改，仅可更新显示名称与联系邮箱。',
        label: {
          tenantCode: '租户代码',
          displayName: '显示名称',
          contactEmail: '联系邮箱',
          adminUsername: '管理员用户名'
        },
        placeholder: {
          tenantCode: 'acme',
          displayName: 'Acme 公司',
          contactEmail: 'admin@acme.example',
          adminUsername: 'admin'
        },
        hint: {
          tenantCode: '小写字母、数字、连字符（RFC1035 标签）。将用作 Keycloak realm 名和子域名。',
          contactEmail: '可选 — 用于邀请首位管理员',
          adminUsername: '留空时将从联系邮箱的本地部分自动生成，之后可修改。'
        },
        error: {
          invalidCode: '租户代码必须是小写 RFC1035 标签（小写字母、数字、连字符）',
          missingDisplayName: '请输入显示名称',
          invalidAdminUsername: '管理员用户名须以小写字母开头，且只能包含小写字母、数字、连字符和下划线'
        },
        saving: '保存中...'
      },
      button: {
        new: '新建租户',
        edit: '编辑',
        suspend: '停用',
        resume: '恢复'
      },
      tooltip: {
        suspend: '停用租户（禁用 Keycloak realm，可恢复）',
        resume: '恢复已停用的租户',
        edit: '编辑租户信息',
        builtInLocked: '内置租户（system）不可修改'
      },
      hardDelete: {
        title: '彻底删除租户',
        tooltip: {
          confirm: '彻底删除租户（业务数据、KC realm、注册表条目将全部物理删除）'
        },
        warning: {
          title: '此操作不可恢复',
          intro: '您即将彻底删除「{displayName}」（{tenantCode}）。以下内容将被永久清除：',
          dropBusiness: '与该租户关联的所有业务表数据（用户、角色、部门、任务等）',
          dropRealm: 'Keycloak realm 本身（所有用户 / 会话 / 客户端配置）',
          dropRegistry: '中央注册表条目（core_tenant）',
          noUndo: '无法恢复，只能从备份手动还原。'
        },
        label: {
          typeCode: '为确认，请准确输入租户代码「{tenantCode}」'
        },
        error: {
          mismatch: '租户代码不匹配'
        },
        button: {
          confirm: '彻底删除',
          deleting: '删除中...'
        },
        message: {
          success: '租户「{tenantCode}」已彻底删除',
          failed: '彻底删除租户失败'
        }
      },
      confirm: {
        suspendTitle: '停用租户',
        suspendMessage: '确认停用「{displayName}」（{tenantCode}）？\n\n• Keycloak realm 将被禁用，用户无法登录\n• 可随时通过「恢复」按钮还原',
        suspendConfirm: '停用',
        resumeTitle: '恢复租户',
        resumeMessage: '确认恢复「{displayName}」（{tenantCode}）？\n\nKeycloak realm 将重新启用并接受登录。',
        resumeConfirm: '恢复'
      },
      support: {
        tooltip: {
          start: '开始支持会话（以该租户的 SUPER_ADMIN 权限操作 30 分钟）',
          disabledSuspended: '已停用的租户无法开始支持会话'
        },
        dialog: {
          title: '开始支持会话',
          warning: {
            title: '高权限操作确认',
            body: '您将以 {displayName}（{tenantCode}）的 SUPER_ADMIN 身份操作 30 分钟。\n本次会话中的所有操作都会以「[support] <您的用户名>」记录到审计日志。'
          },
          reasonLabel: '原因（必填）',
          reasonPlaceholder: '例：OS-1234 复现用户报告的问题',
          reasonHint: '将保存到审计日志（core_oplog.request_body），请填写具体内容。',
          ttlNote: '会话将在 30 分钟后自动失效（不可延长）',
          auditNote: '所有操作都会记录到审计日志',
          writeNote: '只读模式尚未实现（也可写入）—— 请谨慎操作',
          starting: '开始中...',
          confirm: '开始支持会话'
        },
        banner: {
          acting: '支持会话进行中：{displayName}（{tenantCode}）',
          note: '所有操作都会记录到审计日志'
        },
        button: {
          terminate: '结束会话'
        },
        message: {
          started: '已开始支持会话（{tenantCode}）',
          startFailed: '开始支持会话失败',
          terminated: '已结束支持会话'
        }
      },
      resendInvite: {
        tooltip: {
          resend: '重新发送管理员入驻邀请（邮件没收到或地址写错）'
        },
        dialog: {
          title: '重新发送管理员邀请',
          body: '为「{displayName}」（{tenantCode}）的管理员重新发送入驻邀请。',
          emailLabel: '发送至',
          emailPlaceholder: 'admin@example.com',
          emailHint: '已预填当前联系邮箱。改成正确地址即可纠正（同时更新用户、Keycloak 和租户联系邮箱）；不改则仅重新发送。',
          tokenNote: '将生成新的邀请链接，之前的链接随即失效。',
          activatedNote: '仅在管理员尚未激活时有效。',
          sending: '发送中…',
          confirm: '重新发送邀请'
        },
        message: {
          success: '邀请已重新发送',
          failed: '重新发送邀请失败'
        }
      },
      message: {
        createSuccess: '租户已创建，邀请邮件已发送',
        createFailed: '租户创建失败',
        loadFailed: '加载租户列表失败',
        suspendSuccess: '租户已停用',
        suspendFailed: '租户停用失败',
        resumeSuccess: '租户已恢复',
        resumeFailed: '租户恢复失败',
        updateSuccess: '租户信息已更新',
        updateFailed: '租户信息更新失败'
      }
    }
  },

  signOut: {
    title: '正在登出...',
    body: '正在清除本地会话并通知身份提供方（Keycloak）。',
    failed: {
      title: '登出失败',
      goLogin: '前往登录'
    }
  },

  invite: {
    title: '设置账号',
    tenantPrefix: '租户:',
    passwordLabel: '密码',
    passwordPlaceholder: '至少 8 个字符',
    passwordConfirmLabel: '确认密码',
    passwordConfirmPlaceholder: '再次输入密码',
    button: {
      submit: '设置密码',
      submitting: '提交中...',
      goLogin: '前往登录'
    },
    message: {
      checking: '正在验证邀请...',
      invalidLink: '邀请链接格式错误',
      notValid: '邀请已失效、过期或已被使用',
      passwordTooShort: '密码至少需要 8 个字符',
      passwordMismatch: '两次输入的密码不一致',
      acceptFailed: '设置密码失败',
      done: '密码已设置，现在可以登录'
    }
  },

  breakGlass: {
    title: '应急密码',
    intro: {
      what: '这是一个仅用于"应急"的密码，专门用于 Keycloak（SSO）不可用时登录系统。',
      howDifferent: '它与您日常使用的 SSO 密码（保存在 Keycloak 中）完全独立，二者不会同步。请不要使用相同的密码。',
      whenUsed: '仅在 KC 故障等 SSO 无法访问时，通过 /login 的密码字段使用。仅超级管理员拥有此密码。'
    },
    status: {
      configured: '当前已配置应急密码',
      notConfigured: '尚未配置 — 强烈建议在需要前先设置一个'
    },
    label: {
      newPassword: '新的应急密码',
      confirmPassword: '请再次输入以确认'
    },
    placeholder: {
      newPassword: '至少 8 个字符，混合字符类型',
      confirmPassword: '再次输入相同的密码'
    },
    hint: {
      storeSafely: '请保存到密码管理器或团队保密库中 — 一旦遗忘没有找回途径。'
    },
    button: {
      save: '保存',
      saving: '保存中...'
    },
    message: {
      saved: '应急密码已更新'
    },
    error: {
      tooShort: '密码至少需要 8 个字符',
      mismatch: '两次输入的密码不一致',
      saveFailed: '保存失败'
    }
  },

  passwordReset: {
    title: '设置您的密码',
    tenantPrefix: '租户:',
    intro: '系统正从 SSO 切换为密码登录，请在下方设置新密码。',
    passwordLabel: '新密码',
    passwordPlaceholder: '至少 8 个字符',
    passwordConfirmLabel: '确认密码',
    passwordConfirmPlaceholder: '再次输入密码',
    button: {
      submit: '设置密码',
      submitting: '提交中...',
      goLogin: '前往登录'
    },
    message: {
      checking: '正在验证链接...',
      invalidLink: '链接格式错误',
      notValid: '链接已失效、过期或已被使用',
      passwordTooShort: '密码至少需要 8 个字符',
      passwordMismatch: '两次输入的密码不一致',
      acceptFailed: '设置密码失败',
      done: '密码已设置，现在可以登录'
    }
  },

  forget: {
    title: '重置密码',
    mobileLabel: '手机号',
    mobilePlaceholder: '请输入已绑定的手机号',
    newPasswordLabel: '新密码',
    newPasswordPlaceholder: '请输入新登录密码',
    confirmPasswordLabel: '确认密码',
    confirmPasswordPlaceholder: '请再次输入密码',
    captchaLabel: '验证码',
    captchaPlaceholder: '短信验证码',
    sendCaptcha: '发送验证码',
    sentCountdown: '已发送 {n}s',
    backToLogin: '返回登录',
    submit: '修改密码',
    submitting: '处理中...',
    imgCaptchaTitle: '发送验证码',
    imgCaptchaPlaceholder: '请输入图片验证码',
    refresh: '刷新',
    loading: '加载中...',
    sendNow: '立即发送',
    sending: '发送中...',
    message: {
      enterMobile: '请输入手机号',
      enterImgCode: '请输入图片验证码',
      smsSent: '短信验证码已发送',
      passwordReset: '密码已修改',
      enterConfirmPassword: '请输入确认密码',
      passwordMismatch: '两次输入的密码不一致'
    }
  },

  password: {
    oldPassword: '原密码',
    password: '新密码',
    confirmPassword: '确认密码',
    updateHint: '密码修改由身份提供商 (Keycloak) 管理。点击后将直接进入其「修改密码」页面，完成后自动返回本应用。',
    updateButton: '修改密码',
    unavailable: '未启用 SSO 时无法在此修改密码。',
    message: {
      inconsistent: '两次输入的密码不一致'
    }
  },

  notFound: {
    title: '404',
    message: '页面不存在',
    backHome: '返回首页'
  },

  router: {
    title: {
      login: '登录',
      forget: '重置密码',
      notFound: '404',
      profile: '个人资料'
    }
  },

  profile: {
    title: '个人资料',
    editHint: '在下方修改你的联系信息。部门、状态、角色由管理员管理。',
    label: {
      deptId: '部门',
      userId: '用户 ID',
      username: '登录ID',
      displayName: '用户名',
      email: '邮箱',
      userNo: '用户编号',
      tenantId: '租户',
      roles: '角色',
      authorities: '权限'
    }
  },

  user: {
    search: {
      placeholder: { keyword: '登录ID / 邮箱 / 用户名' },
      label: { deptId: '部门', roleId: '角色' }
    },
    column: {
      username: '登录ID',
      displayName: '用户名',
      userNo: '编号',
      email: '邮箱',
      deptId: '部门',
      status: '状态',
      actions: '操作'
    },
    badge: {
      tenantAdmin: '租户管理员',
      self: '本人'
    },
    tooltip: {
      edit: '编辑',
      resetPassword: '重置密码',
      toggleStatus: '启用/禁用',
      disable: '停用',
      enable: '启用',
      forceLogout: '强制登出',
      selfManaged: '请在「个人资料」页管理自己的账号'
    },
    confirm: {
      deleteTitle: '删除用户',
      deleteMessage: '删除「{name}」吗？',
      forceLogoutTitle: '强制登出',
      forceLogoutMessage: '将「{name}」强制登出吗？\n（进行中的 access token 将在下次 API 调用时失效）',
      resetTitle: '重置密码',
      resetMessage: '重置「{name}」的密码吗？\n当前密码将立即失效，该用户的所有会话会被登出。'
    },
    message: {
      deleteFailed: '删除失败',
      forceLogoutSuccess: '已强制登出'
    },
    edit: {
      titleEdit: '编辑用户',
      titleCreate: '新增用户',
      label: {
        username: '登录ID',
        password: '密码',
        displayName: '用户名',
        email: '邮箱',
        userNo: '编号',
        deptId: '部门',
        status: '状态',
        roles: '角色',
        rolesSelected: '已选 {selected} / {total}',
        mode: '密码设置方式'
      },
      mode: {
        invite: {
          title: '邀请邮件',
          hint: '用户通过邮件自行设置密码',
          willEmail: '邀请邮件将发送至 {email}'
        },
        direct: {
          title: '管理员设置',
          hint: '管理员指定初始密码'
        }
      },
      placeholder: {
        password: '8 位以上 / 4 种字符类型'
      },
      message: {
        noRoles: '暂无角色',
        saving: '保存中...',
        updateFailed: '更新失败',
        createFailed: '创建失败',
        assignRolesFailed: '角色分配失败',
        loadRolesFailed: '未能读取该用户当前的角色，为避免被回收已阻止保存。请关闭后重新打开重试。'
      }
    },
    resetPassword: {
      title: '密码已重置',
      intro: '已为「{username}」生成一次性临时密码，仅本次显示。',
      emailSent: '新的登录凭证已同时邮件发送至 {email}。',
      emailNotSent: '通知邮件发送失败，请将临时密码直接告知用户本人。',
      tempPassword: '临时密码',
      copy: '复制',
      hint: '※ 用户使用该临时密码登录后，将被要求设置自己的新密码。',
      message: {
        success: '密码已重置',
        failed: '失败',
        copied: '已复制',
        copyFailed: '复制失败'
      }
    }
  },

  role: {
    search: {
      placeholder: { keyword: '名称 / 说明' }
    },
    column: {
      name: '名称',
      description: '说明',
      dataScope: '数据范围',
      status: '状态',
      actions: '操作'
    },
    option: {
      scope: {
        all: '全部',
        deptAndSub: '本部门及下级',
        dept: '本部门',
        self: '仅本人',
        custom: '自定义'
      }
    },
    tooltip: {
      viewOnly: '内置角色仅可查看（点击编辑按钮查看详情）',
      edit: '编辑',
      deleteDisabled: '内置角色不可删除'
    },
    confirm: {
      deleteTitle: '删除角色',
      deleteMessage: '删除「{name}」吗？',
      inUseMessage: '该角色已分配给 {users} 个用户。\n\n强制删除将清除所有用户对该角色的绑定，相关权限/菜单/部门关联同时失效。是否继续？'
    },
    message: {
      deleteBuiltInFailed: '内置角色不可删除',
      deleteFailed: '删除失败'
    },
    edit: {
      titleEdit: '编辑角色',
      titleCreate: '新增角色',
      lockedHint: '内置角色为只读。修改名称、数据范围或权限/菜单/部门分配可能引起认证循环或权限漂移。',
      tab: {
        permissions: '权限',
        menus: '菜单',
        depts: '部门'
      },
      label: {
        name: '名称',
        description: '说明',
        dataScope: '数据范围',
        status: '状态'
      },
      option: {
        scope: {
          all: '全部 (ALL)',
          deptAndSub: '本部门及下级 (DEPT_AND_SUB)',
          deptOnly: '仅本部门 (DEPT)',
          self: '仅本人 (SELF)',
          custom: '自定义 (CUSTOM)'
        }
      },
      message: {
        noPermissions: '暂无权限',
        noMenus: '暂无菜单',
        noDepts: '暂无部门',
        saving: '保存中...',
        updateFailed: '更新失败',
        createFailed: '创建失败',
        loadSelectionsFailed: '未能读取该角色当前的权限 / 菜单 / 部门，为避免被清空已阻止保存。请关闭后重新打开重试。'
      },
      dept: {
        impliedTag: '（包含）',
        impliedTooltip: '勾选父部门时自动包含。若需移除，请先取消上级勾选。'
      },
      invalidDepts: {
        title: '已失效的部门绑定',
        hint: '以下部门已被停用或删除，不再赋予任何数据可见性，且无法在下方树中编辑。建议移除后保存。',
        clear: '全部移除',
        remove: '移除'
      },
      tooltip: {
        locked: '内置角色不可编辑'
      }
    }
  },

  dept: {
    title: '部门管理',
    button: { addRoot: '新增根节点' },
    column: {
      name: '名称',
      code: '编码',
      level: '层级',
      leader: '负责人',
      status: '状态',
      actions: '操作'
    },
    message: {
      noDepts: '暂无部门',
      loadUsersFailed: '获取用户列表失败',
      userDeleted: '(已删除)',
      deleteFailed: '删除失败'
    },
    tooltip: {
      addChild: '新增子节点',
      edit: '编辑'
    },
    confirm: {
      deleteTitle: '删除部门',
      deleteMessage: '删除「{name}」吗？',
      disableInUseMessage: '有 {roles} 个角色把该部门设为自定义数据范围。停用后，这些角色将看不到该部门的数据（仍启用的子部门不受影响）。仍要停用吗？',
      inUseMessage: '该部门有：子部门 {children} 个、所属用户 {users} 个、角色引用 {roles} 个（含其 SCOPE_CUSTOM 范围）。\n\n强制删除将级联软删该部门及所有子部门，所属用户的部门设置会被清空，相关角色的自定义数据范围中也会移除该部门。是否继续？'
    },
    edit: {
      titleEdit: '编辑部门',
      titleCreate: '新增部门',
      label: {
        parentId: '父部门',
        code: '编码',
        name: '名称',
        sortOrder: '排序',
        status: '状态',
        leader: '负责人'
      },
      placeholder: {
        parentId: '根节点留空',
        code: 'HQ / TOKYO',
        name: '总部',
        leader: '未指定'
      },
      hint: {
        rootParent: '留空将成为根部门',
        leaderInfo: '仅作显示用途，不影响权限和数据范围。'
      },
      message: {
        updateFailed: '更新失败',
        createFailed: '创建失败'
      }
    }
  },

  menu: {
    title: '菜单管理',
    button: { addRoot: '新增根节点' },
    column: {
      title: '名称 / 路径',
      type: '类型',
      component: '组件',
      permission: '权限',
      hide: '隐藏',
      actions: '操作'
    },
    message: {
      noMenus: '暂无菜单',
      fetchFailed: '菜单获取失败'
    },
    option: {
      type: {
        directory: '目录',
        menu: '菜单',
        button: '按钮'
      },
      yesNo: { no: '否', yes: '是' }
    },
    tooltip: {
      addChild: '新增子节点',
      edit: '编辑'
    },
    confirm: {
      deleteTitle: '删除菜单',
      deleteMessage: '删除「{code}」吗？'
    },
    edit: {
      titleEdit: '编辑菜单',
      titleCreate: '新增菜单',
      label: {
        code: '编码',
        name: '名称',
        titleI18n: '多语言名称',
        type: '类型',
        sortOrder: '排序',
        parentId: '父菜单',
        path: '路径',
        component: '组件',
        icon: '图标',
        permissionCode: '权限编码',
        hide: '隐藏',
        hideSidebar: '隐藏侧边栏',
        hideFooter: '隐藏底栏',
        pinned: '置顶'
      },
      tip: {
        hide: '从侧边菜单中隐藏该项（路由仍可访问，常用于详情页、动态路由）',
        hideSidebar: '打开该页面时隐藏左侧导航栏（适用于打印预览、全屏向导等）',
        hideFooter: '打开该页面时隐藏底部页脚（版权信息那一条）',
        pinned: '将该菜单固定显示在侧边栏最顶部，与其它菜单用分割线隔开',
        pinnedDisabled: '只有「菜单」类型可以置顶，目录和按钮无法置顶'
      },
      placeholder: {
        code: 'system.user',
        parentId: '根节点留空',
        path: '/system/user',
        component: '/system/User/User',
        permissionCode: 'user:read',
        titleI18nPrimary: '必填（默认语言）',
        titleI18nOptional: '选填'
      },
      error: {
        titleJaRequired: '日文标题为必填项'
      }
    }
  },

  // 权限码 → 显示名（backend I18nPermissionPatcher 在 dev 启动时自动补齐）
  permission: permissions,

  dataTable: {
    emptyState: '暂无数据',
    loading: '加载中...',
    pagination: {
      total: '共 {n} 条',
      perPage: '{n} 条/页'
    }
  },

  picker: {
    icon: {
      selectPlaceholder: '选择图标',
      searchPlaceholder: '搜索图标...',
      noResults: '没有匹配的图标'
    }
  },

  task: {
    title: '任务（数据范围演示）',
    description: '不同角色看到的任务不同。详情：',
    search: {
      label: { keyword: '关键词', status: '状态' },
      placeholder: { keyword: '搜索标题' }
    },
    column: {
      title: '标题',
      deptId: '部门',
      status: '状态',
      priority: '优先级',
      assignee: '负责人',
      creator: '创建者',
      dueDate: '截止日期',
      actions: '操作'
    },
    emptyState: '暂无数据',
    status: { todo: '未开始', doing: '进行中', done: '完成', cancelled: '已取消' },
    priority: { low: '低', medium: '中', high: '高' },
    confirm: {
      deleteTitle: '删除任务',
      deleteMessage: '删除「{title}」吗？'
    },
    message: {
      loadFailed: '加载失败',
      saveFailed: '保存失败',
      saveSuccess: '已保存',
      deleteFailed: '删除失败',
      deleteSuccess: '已删除'
    },
    edit: {
      titleEdit: '编辑任务',
      titleCreate: '新增任务',
      label: { content: '内容' },
      placeholder: {
        title: '任务标题',
        deptSelect: '选择部门',
        optional: '可选',
        unassigned: '未指定',
        dueDate: '选择截止日期'
      }
    },
    option: { statusAll: '全部' }
  },

  oplog: {
    search: {
      label: {
        module: '模块',
        action: '动作',
        user: '用户',
        targetType: '对象类型',
        targetId: '对象 ID',
        result: '结果'
      },
      placeholder: {
        module: '例如：system / pms / iot',
        action: '例如：role.create',
        user: '所有用户',
        targetType: '例如：role / user'
      }
    },
    option: {
      result: { all: '全部', success: '成功', failure: '失败' }
    },
    column: {
      createTime: '时间',
      username: '用户',
      module: '模块',
      action: '动作',
      targetType: '对象',
      clientIp: 'IP',
      success: '结果',
      costMs: 'ms',
      actions: '详情'
    },
    status: { success: '成功', failure: '失败' },
    detail: {
      title: '操作日志详情',
      label: {
        createTime: '时间',
        costMs: '耗时',
        username: '用户',
        userId: '用户 ID',
        module: '模块',
        action: '动作',
        targetType: '对象类型',
        targetId: '对象 ID',
        method: '方法',
        result: '结果',
        uri: 'URI',
        clientIp: '客户端 IP',
        userAgent: 'User-Agent'
      },
      section: {
        errorMsg: '错误信息',
        requestBody: '请求体（密码已自动脱敏）'
      },
      message: { empty: '(无)' },
      button: { close: '关闭' }
    },
    message: {
      fetchFailed: '获取失败'
    }
  }
}
