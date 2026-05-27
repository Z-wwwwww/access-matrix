/**
 * 简体中文
 *
 * Mirror of ja_JP.js — see that file for the key-tree documentation.
 */
import permissions from './generated/permissions.zh_CN.json'

export default {
  common: {
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
      deleteSuccessful: '删除成功',
      loading: '加载中',
      processing: '处理中...',
      sending: '发送中...',
      loginSuccessful: '登录成功',
      networkError: '网络错误',
      sessionExpired: '会话已过期',
      operationSuccessful: '操作成功',
      iframeLoadFailed: '无法加载外部页面'
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
      copyright: 'Copyright © 2026 SOZONEXT Co.,Ltd.'
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
    passwordBreakGlass: '密码登录模式（应急）',
    backToSso: '返回 SSO',
    passwordModeHotzone: '2 秒内连击 5 次可解锁密码登录',
    message: {
      enterUsername: '请输入用户名',
      enterPassword: '请输入密码',
      loginFailed: '登录失败',
      ssoFailed: 'SSO 登录失败'
    }
  },

  platform: {
    tenant: {
      column: {
        tenantCode: '租户代码',
        displayName: '显示名称',
        contactEmail: '联系邮箱',
        status: '状态',
        createTime: '创建时间',
        actions: '操作'
      },
      status: { active: '运行中', suspended: '已停用' },
      search: { placeholder: '按代码或名称搜索' },
      softDeleteHint: {
        title: '仅软删除：',
        body: '删除租户会禁用其 Keycloak realm（用户无法登录），但保留业务数据。彻底删除需通过单独的运维流程执行。'
      },
      tooltip: {
        softDelete: '软删除租户',
        builtInLocked: '内置租户（system / demo）不可删除'
      },
      confirm: {
        deleteTitle: '删除租户',
        deleteMessage: '确认软删除「{displayName}」（{tenantCode}）？\n\n• Keycloak realm 将被禁用，用户无法登录\n• 业务数据将保留\n• 重新启用需在 KC 管理控制台手动操作',
        deleteConfirm: '执行软删除'
      },
      button: { new: '新建租户' },
      message: {
        loadFailed: '加载租户列表失败',
        createSuccess: '租户已创建',
        createFailed: '租户创建失败',
        deleteSuccess: '租户已软删除',
        deleteFailed: '删除租户失败'
      },
      edit: {
        titleCreate: '新建租户',
        intro: '一次操作创建 Keycloak realm + 中央注册表条目。租户代码创建后不可修改。',
        label: {
          tenantCode: '租户代码',
          displayName: '显示名称',
          contactEmail: '联系邮箱'
        },
        placeholder: {
          tenantCode: 'acme',
          displayName: 'Acme 公司',
          contactEmail: 'admin@acme.example'
        },
        hint: {
          tenantCode: '小写字母、数字、连字符（RFC1035 标签）。将用作 Keycloak realm 名和子域名。',
          contactEmail: '可选 — 用于邀请首位管理员'
        },
        error: {
          invalidCode: '租户代码必须是小写 RFC1035 标签（小写字母、数字、连字符）',
          missingDisplayName: '请输入显示名称'
        },
        saving: '保存中...'
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
      howDifferent: '它与您日常使用的 SSO 密码（保存在 Keycloak 中）**完全独立**，二者**不会同步**。请不要使用相同的密码。',
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
    openConsoleHint: '密码修改由身份提供商 (Keycloak) 管理，请打开自助账号控制台修改。',
    openConsoleButton: '打开账号控制台',
    consoleUnavailable: '未启用 SSO 时无法在此修改密码。',
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
      label: { deptId: '部门' }
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
    tooltip: {
      editDisabled: '内置用户不可编辑',
      editAdminContactOnly: '内置 admin：仅可编辑联系信息（邮箱、显示名）',
      edit: '编辑',
      resetPassword: '重置密码',
      resetPasswordDisabledSso: 'SSO 模式下不可用 — 用户在 Keycloak 账号控制台自助修改密码',
      statusChangeDisabled: '内置用户状态不可变更',
      toggleStatus: '启用/禁用',
      forceLogout: '强制登出',
      deleteDisabled: '内置用户不可删除'
    },
    confirm: {
      deleteTitle: '删除用户',
      deleteMessage: '删除「{name}」吗？',
      forceLogoutTitle: '强制登出',
      forceLogoutMessage: '将「{name}」强制登出吗？\n（进行中的 access token 将在下次 API 调用时失效）'
    },
    message: {
      deleteFailed: '删除失败',
      forceLogoutSuccess: '已强制登出'
    },
    edit: {
      titleEdit: '编辑用户',
      titleCreate: '新增用户',
      lockedHint: '内置 admin 用户「仅可编辑联系信息」：邮箱和显示名可以修改（应急密码告警需要可达邮箱）。部门、状态、角色保持锁定。',
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
        assignRolesFailed: '角色分配失败'
      }
    },
    resetPassword: {
      title: '重置密码',
      label: {
        user: '用户',
        newPassword: '新密码',
        confirmPassword: '确认密码'
      },
      placeholder: {
        value: '8 位以上 / 4 种字符类型',
        confirm: '再次输入相同密码'
      },
      hint: '※ 公开泄露密码库（HIBP）中的密码将被拒绝。',
      button: { reset: '重置' },
      error: {
        tooShort: '密码至少 8 个字符',
        mismatch: '确认密码不一致'
      },
      message: {
        saving: '保存中...',
        success: '密码已重置',
        failed: '失败'
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
        createFailed: '创建失败'
      },
      dept: {
        impliedTag: '（包含）',
        impliedTooltip: '勾选父部门时自动包含。若需移除，请先取消上级勾选。'
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
