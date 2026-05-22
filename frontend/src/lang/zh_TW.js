/**
 * 繁體中文
 *
 * Mirror of ja_JP.js — see that file for the key-tree documentation.
 */
import general from './general/zh_TW'
import permissions from './generated/permissions.zh_TW.json'

export default {
  general,

  common: {
    button: {
      search: '搜尋',
      reset: '重設',
      save: '儲存',
      cancel: '取消',
      confirm: '確認',
      delete: '刪除',
      edit: '編輯',
      new: '新增',
      detail: '詳情',
      apply: '套用',
      clear: '清空',
      selectAll: '全選',
      back: '返回',
      close: '關閉',
      refresh: '重新整理',
      export: '匯出',
      import: '匯入',
      upload: '上傳',
      download: '下載',
      submit: '送出'
    },
    status: {
      active: '啟用',
      inactive: '停用',
      enabled: '啟用',
      disabled: '停用',
      builtIn: '內建'
    },
    message: {
      saveSuccessful: '儲存成功',
      deleteSuccessful: '刪除成功',
      loading: '載入中',
      processing: '處理中...',
      sending: '傳送中...',
      loginSuccessful: '登入成功',
      networkError: '網路錯誤',
      sessionExpired: '工作階段已過期',
      operationSuccessful: '操作成功',
      iframeLoadFailed: '無法載入外部頁面'
    },
    label: {
      keyword: '關鍵字'
    },
    placeholder: {
      keyword: '關鍵字',
      pleaseInput: '請輸入',
      pleaseSelect: '請選擇',
      search: '搜尋...',
      deptId: '請選擇部門'
    },
    tooltip: {
      pagePrevious5: '向前 5 頁',
      pageNext5: '向後 5 頁'
    },
    datePicker: {
      today: '今天',
      now: '現在',
      year: '年',
      yearMonth: '年月',
      placeholder: '選擇日期',
      startPlaceholder: '開始日期',
      endPlaceholder: '結束日期',
      dateTimePlaceholder: '選擇日期時間',
      timePlaceholder: '選擇時間',
      confirm: '確定'
    }
  },

  layout: {
    header: {
      profile: '個人資料',
      password: '修改密碼',
      logout: '登出',
      userFallback: '使用者'
    },
    sidebar: {
      adminGroup: '管理員設定',
      favorites: '我的最愛',
      favorite: '加入我的最愛',
      unfavorite: '從我的最愛移除'
    },
    tabs: {
      tabAction: '分頁操作',
      closeCurrent: '關閉目前分頁',
      closeOthers: '關閉其他分頁',
      closeAll: '關閉所有分頁'
    },
    footer: {
      copyright: 'Copyright © 2026 SOZONEXT Co.,Ltd.'
    }
  },

  login: {
    identifierLabel: '使用者名稱 / 信箱 / 編號',
    identifierPlaceholder: '使用者名稱 · 信箱 · 使用者編號',
    passwordLabel: '密碼',
    passwordPlaceholder: '請輸入密碼',
    submit: '登入',
    submitting: '登入中...',
    forgotPassword: '忘記密碼？',
    message: {
      enterUsername: '請輸入使用者名稱',
      enterPassword: '請輸入密碼',
      loginFailed: '登入失敗'
    }
  },

  forget: {
    title: '重設密碼',
    mobileLabel: '手機號碼',
    mobilePlaceholder: '請輸入已綁定的手機號碼',
    newPasswordLabel: '新密碼',
    newPasswordPlaceholder: '請輸入新登入密碼',
    confirmPasswordLabel: '確認密碼',
    confirmPasswordPlaceholder: '請再次輸入密碼',
    captchaLabel: '驗證碼',
    captchaPlaceholder: '簡訊驗證碼',
    sendCaptcha: '發送驗證碼',
    sentCountdown: '已發送 {n}s',
    backToLogin: '返回登入',
    submit: '變更密碼',
    submitting: '處理中...',
    imgCaptchaTitle: '發送驗證碼',
    imgCaptchaPlaceholder: '請輸入圖形驗證碼',
    refresh: '重新整理',
    loading: '載入中...',
    sendNow: '立即發送',
    sending: '發送中...',
    message: {
      enterMobile: '請輸入手機號碼',
      enterImgCode: '請輸入圖形驗證碼',
      smsSent: '簡訊驗證碼已發送',
      passwordReset: '密碼已變更',
      enterConfirmPassword: '請輸入確認密碼',
      passwordMismatch: '兩次輸入的密碼不一致'
    }
  },

  password: {
    oldPassword: '舊密碼',
    password: '新密碼',
    confirmPassword: '確認密碼',
    message: {
      inconsistent: '兩次輸入的密碼不一致'
    }
  },

  notFound: {
    title: '404',
    message: '找不到頁面',
    backHome: '回到首頁'
  },

  router: {
    title: {
      login: '登入',
      forget: '重設密碼',
      notFound: '404',
      profile: '個人資料'
    }
  },

  profile: {
    title: '個人資料',
    label: {
      deptId: '部門',
      userId: '使用者 ID',
      username: '登入 ID',
      displayName: '使用者名稱',
      email: '信箱',
      userNo: '使用者編號',
      tenantId: '租戶',
      roles: '角色',
      authorities: '權限'
    }
  },

  user: {
    search: {
      placeholder: { keyword: '登入 ID / 信箱 / 使用者名稱' },
      label: { deptId: '部門' }
    },
    column: {
      username: '登入 ID',
      displayName: '使用者名稱',
      userNo: '編號',
      email: '信箱',
      deptId: '部門',
      status: '狀態',
      actions: '操作'
    },
    tooltip: {
      editDisabled: '內建使用者不可編輯',
      edit: '編輯',
      resetPassword: '重設密碼',
      statusChangeDisabled: '內建使用者狀態不可變更',
      toggleStatus: '啟用/停用',
      forceLogout: '強制登出',
      deleteDisabled: '內建使用者不可刪除'
    },
    confirm: {
      deleteTitle: '刪除使用者',
      deleteMessage: '刪除「{name}」嗎？',
      forceLogoutTitle: '強制登出',
      forceLogoutMessage: '將「{name}」強制登出嗎？\n（進行中的 access token 將在下次 API 呼叫時失效）'
    },
    message: {
      deleteFailed: '刪除失敗',
      forceLogoutSuccess: '已強制登出'
    },
    edit: {
      titleEdit: '編輯使用者',
      titleCreate: '新增使用者',
      lockedHint: '內建 admin 使用者為唯讀。僅可修改密碼（透過「重設密碼」API）。',
      label: {
        username: '登入 ID',
        password: '密碼',
        displayName: '使用者名稱',
        email: '信箱',
        userNo: '編號',
        deptId: '部門',
        status: '狀態',
        roles: '角色'
      },
      placeholder: {
        password: '8 位以上 / 4 種字元類型'
      },
      message: {
        noRoles: '尚無角色',
        saving: '儲存中...',
        updateFailed: '更新失敗',
        createFailed: '建立失敗',
        assignRolesFailed: '角色指派失敗'
      }
    },
    resetPassword: {
      title: '重設密碼',
      label: {
        user: '使用者',
        newPassword: '新密碼',
        confirmPassword: '確認密碼'
      },
      placeholder: {
        value: '8 位以上 / 4 種字元類型',
        confirm: '再次輸入相同密碼'
      },
      hint: '※ 已公開洩漏的密碼庫（HIBP）中的密碼將被拒絕。',
      button: { reset: '重設' },
      error: {
        tooShort: '密碼至少 8 個字元',
        mismatch: '確認密碼不一致'
      },
      message: {
        saving: '儲存中...',
        success: '密碼已重設',
        failed: '失敗'
      }
    }
  },

  role: {
    search: {
      placeholder: { keyword: '代碼 / 名稱' }
    },
    column: {
      code: '代碼',
      name: '名稱',
      dataScope: '資料範圍',
      status: '狀態',
      actions: '操作'
    },
    option: {
      scope: {
        all: '全部',
        deptAndSub: '本部門及下級',
        dept: '本部門',
        self: '僅本人',
        custom: '自訂'
      }
    },
    tooltip: {
      viewOnly: '內建角色僅可檢視（點擊編輯按鈕查看詳情）',
      edit: '編輯',
      deleteDisabled: '內建角色不可刪除'
    },
    confirm: {
      deleteTitle: '刪除角色',
      deleteMessage: '刪除「{code}」嗎？'
    },
    message: {
      deleteBuiltInFailed: '內建角色不可刪除',
      deleteFailed: '刪除失敗'
    },
    edit: {
      titleEdit: '編輯角色',
      titleCreate: '新增角色',
      lockedHint: '內建角色為唯讀。修改代碼、資料範圍或權限/選單/部門分配可能引起認證循環或權限漂移。',
      tab: {
        basic: '基本',
        permissions: '權限',
        menus: '選單',
        depts: '部門'
      },
      label: {
        code: '代碼',
        name: '名稱',
        description: '說明',
        dataScope: '資料範圍',
        status: '狀態'
      },
      option: {
        scope: {
          all: '全部 (ALL)',
          deptAndSub: '本部門及下級 (DEPT_AND_SUB)',
          deptOnly: '僅本部門 (DEPT)',
          self: '僅本人 (SELF)',
          custom: '自訂 (CUSTOM)'
        }
      },
      message: {
        noPermissions: '尚無權限',
        noMenus: '尚無選單',
        noDepts: '尚無部門',
        saving: '儲存中...',
        updateFailed: '更新失敗',
        createFailed: '建立失敗'
      },
      tooltip: {
        locked: '內建角色不可編輯'
      }
    }
  },

  dept: {
    title: '部門管理',
    button: { addRoot: '新增根節點' },
    column: {
      name: '名稱',
      code: '代碼',
      level: '層級',
      leader: '負責人',
      status: '狀態',
      actions: '操作'
    },
    message: {
      noDepts: '尚無部門',
      loadUsersFailed: '取得使用者列表失敗',
      userDeleted: '(已刪除)',
      deleteFailed: '刪除失敗'
    },
    tooltip: {
      addChild: '新增子節點',
      edit: '編輯'
    },
    confirm: {
      deleteTitle: '刪除部門',
      deleteMessage: '刪除「{name}」嗎？\n（存在子部門或所屬使用者時將被拒絕）'
    },
    edit: {
      titleEdit: '編輯部門',
      titleCreate: '新增部門',
      label: {
        parentId: '父部門',
        code: '代碼',
        name: '名稱',
        sortOrder: '排序',
        status: '狀態',
        leader: '負責人'
      },
      placeholder: {
        parentId: '根節點留空',
        code: 'HQ / TOKYO',
        name: '總部',
        leader: '未指定'
      },
      hint: {
        rootParent: '留空將成為根部門',
        leaderInfo: '僅作顯示用途，不影響權限與資料範圍。'
      },
      message: {
        updateFailed: '更新失敗',
        createFailed: '建立失敗'
      }
    }
  },

  menu: {
    title: '選單管理',
    button: { addRoot: '新增根節點' },
    column: {
      title: '名稱 / 路徑',
      type: '類型',
      component: '元件',
      permission: '權限',
      hide: '隱藏',
      actions: '操作'
    },
    message: {
      noMenus: '尚無選單',
      fetchFailed: '取得選單失敗'
    },
    option: {
      type: {
        directory: '目錄',
        menu: '選單',
        button: '按鈕'
      },
      yesNo: { no: '否', yes: '是' }
    },
    tooltip: {
      addChild: '新增子節點',
      edit: '編輯'
    },
    confirm: {
      deleteTitle: '刪除選單',
      deleteMessage: '刪除「{code}」嗎？'
    },
    edit: {
      titleEdit: '編輯選單',
      titleCreate: '新增選單',
      label: {
        code: '代碼',
        name: '名稱',
        type: '類型',
        sortOrder: '排序',
        parentId: '父選單',
        path: '路徑',
        component: '元件',
        icon: '圖示',
        permissionCode: '權限代碼',
        hide: '隱藏',
        hideSidebar: '隱藏側邊欄',
        hideFooter: '隱藏底欄',
        pinned: '置頂'
      },
      tip: {
        hide: '從側邊選單中隱藏此項（路由仍可存取，常用於詳情頁、動態路由）',
        hideSidebar: '開啟此頁面時隱藏左側導覽列（適用於列印預覽、全螢幕精靈等）',
        hideFooter: '開啟此頁面時隱藏底部頁尾（版權資訊）',
        pinned: '將此選單固定顯示在側邊欄最頂部，與其他選單用分隔線隔開',
        pinnedDisabled: '只有「選單」類型可以置頂，目錄和按鈕無法置頂'
      },
      placeholder: {
        code: 'system.user',
        parentId: '根節點留空',
        path: '/system/user',
        component: '/system/User/User',
        permissionCode: 'user:read'
      }
    }
  },

  // 權限碼 → 顯示名（backend I18nPermissionPatcher 於 dev 啟動時自動補齊）
  permission: permissions,

  dataTable: {
    emptyState: '暫無資料',
    loading: '載入中...',
    pagination: {
      total: '共 {n} 筆',
      perPage: '{n} 筆/頁'
    }
  },

  picker: {
    icon: {
      selectPlaceholder: '選擇圖示',
      searchPlaceholder: '搜尋圖示...',
      noResults: '沒有符合的圖示'
    }
  },

  task: {
    title: '任務（資料範圍展示）',
    description: '不同角色看到的任務不同。詳情：',
    search: {
      label: { keyword: '關鍵字', status: '狀態' },
      placeholder: { keyword: '搜尋標題' }
    },
    column: {
      title: '標題',
      deptId: '部門',
      status: '狀態',
      priority: '優先順序',
      assignee: '負責人',
      creator: '建立者',
      dueDate: '截止日期',
      actions: '操作'
    },
    emptyState: '暫無資料',
    status: { todo: '未開始', doing: '進行中', done: '完成', cancelled: '已取消' },
    priority: { low: '低', medium: '中', high: '高' },
    confirm: {
      deleteTitle: '刪除任務',
      deleteMessage: '刪除「{title}」嗎？'
    },
    message: {
      loadFailed: '載入失敗',
      saveFailed: '儲存失敗',
      saveSuccess: '已儲存',
      deleteFailed: '刪除失敗',
      deleteSuccess: '已刪除'
    },
    edit: {
      titleEdit: '編輯任務',
      titleCreate: '新增任務',
      label: { content: '內容' },
      placeholder: {
        title: '任務標題',
        deptSelect: '選擇部門',
        optional: '選填',
        unassigned: '未指定',
        dueDate: '選擇截止日期'
      }
    },
    option: { statusAll: '全部' }
  },

  oplog: {
    search: {
      label: {
        module: '模組',
        action: '動作',
        user: '使用者',
        targetType: '對象類型',
        targetId: '對象 ID',
        result: '結果'
      },
      placeholder: {
        module: '例如：system / pms / iot',
        action: '例如：role.create',
        user: '所有使用者',
        targetType: '例如：role / user'
      }
    },
    option: {
      result: { all: '全部', success: '成功', failure: '失敗' }
    },
    column: {
      createTime: '時間',
      username: '使用者',
      module: '模組',
      action: '動作',
      targetType: '對象',
      clientIp: 'IP',
      success: '結果',
      costMs: 'ms',
      actions: '詳情'
    },
    status: { success: '成功', failure: '失敗' },
    detail: {
      title: '操作紀錄詳情',
      label: {
        createTime: '時間',
        costMs: '耗時',
        username: '使用者',
        userId: '使用者 ID',
        module: '模組',
        action: '動作',
        targetType: '對象類型',
        targetId: '對象 ID',
        method: '方法',
        result: '結果',
        uri: 'URI',
        clientIp: '客戶端 IP',
        userAgent: 'User-Agent'
      },
      section: {
        errorMsg: '錯誤訊息',
        requestBody: '請求本文（密碼已自動遮罩）'
      },
      message: { empty: '(無)' },
      button: { close: '關閉' }
    },
    message: {
      fetchFailed: '取得失敗'
    }
  }
}
