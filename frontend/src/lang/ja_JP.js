/**
 * 日本語
 *
 * Top-level namespaces:
 *   - common.*   : shared UI strings (buttons, statuses, toast messages, date picker)
 *   - layout.*   : application shell (header, sidebar, tabs, footer)
 *   - login.*    : login screen
 *   - forget.*   : forgot-password screen
 *   - password.* : password-change dialog
 *   - notFound.* : 404 page
 *   - router.*   : route meta titles (browser tab + AppTabBar)
 */
import permissions from './generated/permissions.ja_JP.json'

export default {
  common: {
    confirm: {
      forceTitle: '強制削除',
      forceMessage: '{detail}\n\n強制削除すると、関連する全リンクをカスケード解除します。続行しますか？'
    },
    button: {
      forceDelete: '強制削除',
      search: '検索',
      reset: 'リセット',
      save: '保存',
      cancel: 'キャンセル',
      confirm: '確認',
      delete: '削除',
      edit: '編集',
      new: '新規',
      detail: '詳細',
      apply: '適用',
      clear: 'クリア',
      selectAll: 'すべて選択',
      back: '戻る',
      close: '閉じる',
      refresh: '更新',
      export: 'エクスポート',
      import: 'インポート',
      upload: 'アップロード',
      download: 'ダウンロード',
      submit: '送信'
    },
    status: {
      active: '有効',
      inactive: '無効',
      enabled: '有効',
      disabled: '無効',
      builtIn: '内蔵'
    },
    message: {
      saveSuccessful: '保存しました',
      deleteSuccessful: '削除しました',
      loading: '読み込み中',
      processing: '処理中...',
      sending: '送信中...',
      loginSuccessful: 'ログインしました',
      networkError: 'ネットワークエラー',
      sessionExpired: 'セッションが切れました',
      operationSuccessful: '操作が完了しました',
      iframeLoadFailed: '外部ページを読み込めません'
    },
    label: {
      keyword: 'キーワード'
    },
    placeholder: {
      keyword: 'キーワード',
      pleaseInput: '入力してください',
      pleaseSelect: '選択してください',
      search: '検索...',
      deptId: '部署を選択してください'
    },
    tooltip: {
      pagePrevious5: '5ページ前へ',
      pageNext5: '5ページ後へ'
    },
    datePicker: {
      today: '今日',
      now: '現在',
      year: '年',
      yearMonth: '年月',
      placeholder: '日付を選択',
      startPlaceholder: '開始日',
      endPlaceholder: '終了日',
      dateTimePlaceholder: '日時を選択',
      timePlaceholder: '時刻を選択',
      confirm: '決定'
    }
  },

  layout: {
    header: {
      profile: 'プロフィール',
      password: 'パスワード変更',
      logout: 'ログアウト',
      userFallback: 'ユーザー'
    },
    sidebar: {
      adminGroup: '管理者設定',
      favorites: 'お気に入り',
      favorite: 'お気に入りに追加',
      unfavorite: 'お気に入りから外す'
    },
    tabs: {
      tabAction: 'タブ操作',
      closeCurrent: 'このタブを閉じる',
      closeOthers: '他のタブを閉じる',
      closeAll: 'すべてのタブを閉じる'
    },
    footer: {
      copyright: 'Copyright © 2026 SOZONEXT Co.,Ltd.'
    }
  },

  login: {
    tenantLabel: 'テナント',
    tenantPlaceholder: 'default',
    showAdvanced: '詳細（テナント切替）',
    hideAdvanced: '閉じる',
    identifierLabel: 'ユーザー名 / メール / 番号',
    identifierPlaceholder: 'ユーザー名・メール・ユーザー番号',
    passwordLabel: 'パスワード',
    passwordPlaceholder: 'パスワードを入力してください',
    submit: 'ログイン',
    submitting: 'ログイン中...',
    forgotPassword: 'パスワードを忘れた',
    message: {
      enterUsername: 'ユーザー名を入力してください',
      enterPassword: 'パスワードを入力してください',
      loginFailed: 'ログインに失敗しました'
    }
  },

  forget: {
    title: 'パスワード再設定',
    mobileLabel: '携帯番号',
    mobilePlaceholder: 'バインド済みの携帯番号を入力',
    newPasswordLabel: '新しいパスワード',
    newPasswordPlaceholder: '新しいログインパスワードを入力',
    confirmPasswordLabel: 'パスワード（確認）',
    confirmPasswordPlaceholder: 'もう一度パスワードを入力',
    captchaLabel: '認証コード',
    captchaPlaceholder: 'SMS 認証コード',
    sendCaptcha: '認証コード送信',
    sentCountdown: '送信済み {n}s',
    backToLogin: 'ログインに戻る',
    submit: 'パスワード変更',
    submitting: '処理中...',
    imgCaptchaTitle: '認証コード送信',
    imgCaptchaPlaceholder: '画像認証コードを入力',
    refresh: '更新',
    loading: '読込中...',
    sendNow: '今すぐ送信',
    sending: '送信中...',
    message: {
      enterMobile: '携帯番号を入力してください',
      enterImgCode: '画像認証コードを入力してください',
      smsSent: 'SMS 認証コードを送信しました',
      passwordReset: 'パスワードを変更しました',
      enterConfirmPassword: 'パスワード（確認）を入力してください',
      passwordMismatch: '2 回入力したパスワードが一致しません'
    }
  },

  password: {
    oldPassword: '旧パスワード',
    password: '新しいパスワード',
    confirmPassword: 'パスワード（確認）',
    message: {
      inconsistent: '2 回入力したパスワードが一致しません'
    }
  },

  notFound: {
    title: '404',
    message: 'ページが見つかりません',
    backHome: 'ホームに戻る'
  },

  router: {
    title: {
      login: 'ログイン',
      forget: 'パスワード再設定',
      notFound: '404',
      profile: 'プロフィール'
    }
  },

  profile: {
    title: 'プロフィール',
    label: {
      deptId: '部署',
      userId: 'ユーザー ID',
      username: 'ログインID',
      displayName: 'ユーザー名',
      email: 'メール',
      userNo: 'ユーザー No.',
      tenantId: 'テナント',
      roles: 'ロール',
      authorities: '権限'
    }
  },

  user: {
    search: {
      placeholder: { keyword: 'ログインID / メール / ユーザー名' },
      label: { deptId: '部署' }
    },
    column: {
      username: 'ログインID',
      displayName: 'ユーザー名',
      userNo: '番号',
      email: 'メール',
      deptId: '部署',
      status: '状態',
      actions: '操作'
    },
    tooltip: {
      editDisabled: '内蔵ユーザーは編集不可',
      edit: '編集',
      resetPassword: 'パスワードリセット',
      statusChangeDisabled: '内蔵ユーザーは状態変更不可',
      toggleStatus: '有効/無効',
      forceLogout: '強制ログアウト',
      deleteDisabled: '内蔵ユーザーは削除不可'
    },
    confirm: {
      deleteTitle: 'ユーザー削除',
      deleteMessage: '「{name}」を削除しますか？',
      forceLogoutTitle: '強制ログアウト',
      forceLogoutMessage: '「{name}」を強制ログアウトしますか？\n（進行中の access token は次回 API 呼び出し時点で無効化されます）'
    },
    message: {
      deleteFailed: '削除失敗',
      forceLogoutSuccess: '強制ログアウトしました'
    },
    edit: {
      titleEdit: 'ユーザー編集',
      titleCreate: 'ユーザー新規',
      lockedHint: '内蔵 admin ユーザーは読み取り専用です。パスワードの変更のみ可能（『パスワードリセット』API 経由）。',
      label: {
        username: 'ログインID',
        password: 'パスワード',
        displayName: 'ユーザー名',
        email: 'メール',
        userNo: '番号',
        deptId: '部署',
        status: '状態',
        roles: 'ロール',
        rolesSelected: '{selected} / {total} 選択中'
      },
      placeholder: {
        password: '8 文字以上 / 4 種類の文字種'
      },
      message: {
        noRoles: 'ロールがありません',
        saving: '保存中...',
        updateFailed: '更新失敗',
        createFailed: '作成失敗',
        assignRolesFailed: 'ロール割り当て失敗'
      }
    },
    resetPassword: {
      title: 'パスワードリセット',
      label: {
        user: 'ユーザー',
        newPassword: '新しいパスワード',
        confirmPassword: '確認用パスワード'
      },
      placeholder: {
        value: '8 文字以上 / 4 種類の文字種',
        confirm: '同じパスワードを再入力'
      },
      hint: '※ 公開侵害コーパス（HIBP）に登録されたパスワードは拒否されます。',
      button: { reset: 'リセット' },
      error: {
        tooShort: 'パスワードは 8 文字以上で入力してください',
        mismatch: '確認用パスワードが一致しません'
      },
      message: {
        saving: '保存中...',
        success: 'パスワードをリセットしました',
        failed: '失敗'
      }
    }
  },

  role: {
    search: {
      placeholder: { keyword: '名称 / 説明' }
    },
    column: {
      name: '名称',
      description: '説明',
      dataScope: 'データスコープ',
      status: '状態',
      actions: '操作'
    },
    option: {
      scope: {
        all: '全部',
        deptAndSub: '本部署+下位',
        dept: '本部署',
        self: '本人のみ',
        custom: 'カスタム'
      }
    },
    tooltip: {
      viewOnly: '内蔵ロールは閲覧のみ（編集ボタンで詳細表示）',
      edit: '編集',
      deleteDisabled: '内蔵ロールは削除不可'
    },
    confirm: {
      deleteTitle: 'ロール削除',
      deleteMessage: '「{name}」を削除しますか？',
      inUseMessage: 'このロールは {users} 人のユーザーに割り当てられています。\n\n強制削除すると、ユーザーへの割り当てを全て解除し、権限／メニュー／部署の関連付けもまとめてクリアします。続行しますか？'
    },
    message: {
      deleteBuiltInFailed: '内蔵ロールは削除できません',
      deleteFailed: '削除失敗'
    },
    edit: {
      titleEdit: 'ロール編集',
      titleCreate: 'ロール新規',
      lockedHint: '内蔵ロールは読み取り専用です。名称・データスコープ・権限/メニュー/部署の割り当てを変更すると、認証ループや権限のドリフトを引き起こす可能性があります。',
      tab: {
        permissions: '権限',
        menus: 'メニュー',
        depts: '部署'
      },
      label: {
        name: '名称',
        description: '説明',
        dataScope: 'データスコープ',
        status: '状態'
      },
      option: {
        scope: {
          all: '全部 (ALL)',
          deptAndSub: '本部署 + 下位 (DEPT_AND_SUB)',
          deptOnly: '本部署のみ (DEPT)',
          self: '本人のみ (SELF)',
          custom: 'カスタム (CUSTOM)'
        }
      },
      message: {
        noPermissions: '権限がありません',
        noMenus: 'メニューがありません',
        noDepts: '部署がありません',
        saving: '保存中...',
        updateFailed: '更新失敗',
        createFailed: '作成失敗'
      },
      dept: {
        impliedTag: '（自動）',
        impliedTooltip: '親部署の選択により自動的に含まれます。外すには上位の選択を解除してください。'
      },
      tooltip: {
        locked: '内蔵ロールは編集不可'
      }
    }
  },

  dept: {
    title: '部署管理',
    button: { addRoot: 'ルート追加' },
    column: {
      name: '名称',
      code: 'コード',
      level: 'レベル',
      leader: 'リーダー',
      status: '状態',
      actions: '操作'
    },
    message: {
      noDepts: '部署がありません',
      loadUsersFailed: 'ユーザー一覧の取得に失敗しました',
      userDeleted: '(削除済)',
      deleteFailed: '削除失敗'
    },
    tooltip: {
      addChild: '子追加',
      edit: '編集'
    },
    confirm: {
      deleteTitle: '部署削除',
      deleteMessage: '「{name}」を削除しますか？',
      inUseMessage: 'この部署には：子部署 {children} 件、所属ユーザー {users} 名、参照ロール {roles} 件（SCOPE_CUSTOM データ範囲）があります。\n\n強制削除すると、この部署と全子部署をまとめてソフト削除し、所属ユーザーの部署設定をクリア、参照ロールのカスタムデータ範囲からも除外します。続行しますか？'
    },
    edit: {
      titleEdit: '部署編集',
      titleCreate: '部署新規',
      label: {
        parentId: '親部署',
        code: 'コード',
        name: '名称',
        sortOrder: '並び順',
        status: '状態',
        leader: 'リーダー'
      },
      placeholder: {
        parentId: 'ルートの場合は空',
        code: 'HQ / TOKYO',
        name: '本社',
        leader: '未指定'
      },
      hint: {
        rootParent: '空にするとルート部署になります',
        leaderInfo: '表示用のメモです。権限・データ範囲には影響しません。'
      },
      message: {
        updateFailed: '更新失敗',
        createFailed: '作成失敗'
      }
    }
  },

  menu: {
    title: 'メニュー管理',
    button: { addRoot: 'ルート追加' },
    column: {
      title: '名称 / パス',
      type: '種類',
      component: 'コンポーネント',
      permission: '権限',
      hide: '非表示',
      actions: '操作'
    },
    message: {
      noMenus: 'メニューがありません',
      fetchFailed: 'メニュー取得に失敗しました'
    },
    option: {
      type: {
        directory: 'ディレクトリ',
        menu: 'メニュー',
        button: 'ボタン'
      },
      yesNo: { no: 'いいえ', yes: 'はい' }
    },
    tooltip: {
      addChild: '子追加',
      edit: '編集'
    },
    confirm: {
      deleteTitle: 'メニュー削除',
      deleteMessage: '「{code}」を削除しますか？'
    },
    edit: {
      titleEdit: 'メニュー編集',
      titleCreate: 'メニュー新規',
      label: {
        code: 'コード',
        name: '名称',
        titleI18n: '多言語タイトル',
        type: '種類',
        sortOrder: '並び順',
        parentId: '親メニュー',
        path: 'パス',
        component: 'コンポーネント',
        icon: 'アイコン',
        permissionCode: '権限コード',
        hide: '非表示',
        hideSidebar: 'サイドバー非表示',
        hideFooter: 'フッター非表示',
        pinned: 'トップ固定'
      },
      tip: {
        hide: 'メニューツリーからこの項目を非表示にします（ルートは有効、詳細ページや動的ルートで使用）',
        hideSidebar: 'このページを開いた時に左側サイドバーを非表示にします（印刷プレビュー・全画面ウィザード等向け）',
        hideFooter: 'このページを開いた時にフッター（著作権情報）を非表示にします',
        pinned: 'このメニューをサイドバー最上部に固定表示します。固定された項目は区切り線で他のメニューと分けて表示されます',
        pinnedDisabled: 'トップ固定はメニュー（種類：メニュー）のみ設定可能です。ディレクトリやボタンは固定できません'
      },
      placeholder: {
        code: 'system.user',
        parentId: 'ルートの場合は空',
        path: '/system/user',
        component: '/system/User/User',
        permissionCode: 'user:read',
        titleI18nPrimary: '必須（既定ロケール）',
        titleI18nOptional: '任意'
      },
      error: {
        titleJaRequired: '日本語タイトルは必須です'
      }
    }
  },

  // 権限コード → 表示名（コード由来、backend の I18nPermissionPatcher が dev 起動時に補完）
  permission: permissions,

  dataTable: {
    emptyState: 'データなし',
    loading: '読み込み中...',
    pagination: {
      total: '全 {n} 件',
      perPage: '{n}件/ページ'
    }
  },

  picker: {
    icon: {
      selectPlaceholder: 'アイコンを選択',
      searchPlaceholder: 'アイコンを検索...',
      noResults: '該当するアイコンがありません'
    }
  },

  task: {
    title: 'タスク（データ範囲デモ）',
    description: 'ロール毎に見えるタスクが変わります。詳細:',
    search: {
      label: { keyword: 'キーワード', status: '状態' },
      placeholder: { keyword: 'タイトル検索' }
    },
    column: {
      title: 'タイトル',
      deptId: '部署',
      status: '状態',
      priority: '優先度',
      assignee: '担当者',
      creator: '作成者',
      dueDate: '期日',
      actions: '操作'
    },
    emptyState: '該当データがありません',
    status: { todo: '未着手', doing: '進行中', done: '完了', cancelled: '取消' },
    priority: { low: '低', medium: '中', high: '高' },
    confirm: {
      deleteTitle: 'タスク削除',
      deleteMessage: '「{title}」を削除しますか？'
    },
    message: {
      loadFailed: '読み込み失敗',
      saveFailed: '保存失敗',
      saveSuccess: '保存しました',
      deleteFailed: '削除失敗',
      deleteSuccess: '削除しました'
    },
    edit: {
      titleEdit: 'タスク編集',
      titleCreate: 'タスク新規',
      label: { content: '内容' },
      placeholder: {
        title: 'タスクタイトル',
        deptSelect: '部署を選択',
        optional: '任意',
        unassigned: '未指定',
        dueDate: '期日を選択'
      }
    },
    option: { statusAll: 'すべて' }
  },

  oplog: {
    search: {
      label: {
        module: 'モジュール',
        action: 'アクション',
        user: 'ユーザー',
        targetType: '対象タイプ',
        targetId: '対象 ID',
        result: '結果'
      },
      placeholder: {
        module: '例: system / pms / iot',
        action: '例: role.create',
        user: '全ユーザー',
        targetType: '例: role / user'
      }
    },
    option: {
      result: { all: 'すべて', success: '成功', failure: '失敗' }
    },
    column: {
      createTime: '時刻',
      username: 'ユーザー',
      module: 'モジュール',
      action: 'アクション',
      targetType: '対象',
      clientIp: 'IP',
      success: '結果',
      costMs: 'ms',
      actions: '詳細'
    },
    status: { success: '成功', failure: '失敗' },
    detail: {
      title: '操作ログ詳細',
      label: {
        createTime: '時刻',
        costMs: '耗時',
        username: 'ユーザー',
        userId: 'ユーザー ID',
        module: 'モジュール',
        action: 'アクション',
        targetType: '対象タイプ',
        targetId: '対象 ID',
        method: 'メソッド',
        result: '結果',
        uri: 'URI',
        clientIp: 'クライアント IP',
        userAgent: 'User-Agent'
      },
      section: {
        errorMsg: 'エラーメッセージ',
        requestBody: 'リクエスト本文（パスワード自動マスク済み）'
      },
      message: { empty: '(なし)' },
      button: { close: '閉じる' }
    },
    message: {
      fetchFailed: '取得失敗'
    }
  }
}
