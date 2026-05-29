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
      breakGlass: 'ブレークグラス・パスワード',
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
    tenantPlaceholder: 'demo',
    showAdvanced: '詳細（テナント切替）',
    hideAdvanced: '閉じる',
    identifierLabel: 'ユーザー名 / メール / 番号',
    identifierPlaceholder: 'ユーザー名・メール・ユーザー番号',
    passwordLabel: 'パスワード',
    passwordPlaceholder: 'パスワードを入力してください',
    submit: 'ログイン',
    submitting: 'ログイン中...',
    forgotPassword: 'パスワードを忘れた',
    ssoDivider: 'または',
    ssoButton: 'SSO でサインイン',
    ssoOnlyHint: 'シングルサインオン（SSO）でサインインしてください。',
    ssoRedirecting: 'SSO にリダイレクト中...',
    ssoUnreachable: {
      title: 'SSO サーバーに接続できません',
      body: 'Keycloak（SSO）が現在応答していません。メンテナンス中・再起動中、またはネットワーク障害の可能性があります。応急パスワードでログインするか、しばらくしてから再試行してください。',
      useBreakGlass: '応急パスワードで続行',
      retry: 'SSO を再試行',
      retrying: '確認中...'
    },
    tenantRecovered: 'テナント「{stale}」は利用できなくなりました。デフォルトテナントに戻しました。',
    passwordBreakGlass: 'パスワードログインモード（緊急用）',
    backToSso: 'SSO に戻す',
    passwordModeHotzone: 'パスワードログインを解除するには 2 秒以内に 5 回クリック',
    message: {
      enterUsername: 'ユーザー名を入力してください',
      enterPassword: 'パスワードを入力してください',
      loginFailed: 'ログインに失敗しました',
      ssoFailed: 'SSO ログインに失敗しました'
    }
  },

  platform: {
    tenant: {
      column: {
        tenantCode: 'テナントコード',
        displayName: '表示名',
        contactEmail: '連絡先メール',
        status: '状態',
        createTime: '作成日時',
        actions: '操作'
      },
      status: {
        active: '稼働中',
        suspended: '停止'
      },
      search: { placeholder: 'コードまたは名前で検索' },
      recycleBinHint: {
        title: '削除はゴミ箱方式：',
        body: 'まず「停止」してください（KC realm が無効化され、サインイン不可になりますがデータは残ります）。本当に削除する場合は停止後の行に出る赤いゴミ箱アイコンから実行 — テナントコード入力での確認後、業務データ・KC realm・レジストリ行すべて物理削除されます。元に戻せません。'
      },
      edit: {
        titleCreate: '新規テナント',
        titleEdit: 'テナント編集',
        intro: 'Keycloak realm + 中央レジストリ行を 1 操作で作成します。テナントコードは作成後の変更不可です。',
        editIntro: 'テナントコードは変更不可です。表示名・連絡先メールのみ更新できます。',
        label: {
          tenantCode: 'テナントコード',
          displayName: '表示名',
          contactEmail: '連絡先メール',
          adminUsername: '管理者ユーザー名'
        },
        placeholder: {
          tenantCode: 'acme',
          displayName: 'Acme 株式会社',
          contactEmail: 'admin@acme.example',
          adminUsername: 'admin'
        },
        hint: {
          tenantCode: '小文字英数字・ハイフン（RFC1035 ラベル）。Keycloak realm 名・サブドメインに使われます。',
          contactEmail: '最初の管理者を招待する宛先（任意）',
          adminUsername: '未入力時は連絡先メールのローカル部から自動生成。後から変更可'
        },
        error: {
          invalidCode: 'テナントコードは小文字英数字・ハイフン（RFC1035 ラベル）で入力してください',
          missingDisplayName: '表示名を入力してください',
          invalidAdminUsername: '管理者ユーザー名は英小文字始まり、英数字・ハイフン・アンダースコアのみ'
        },
        saving: '送信中...'
      },
      button: {
        new: '新規テナント',
        edit: '編集',
        suspend: '停止',
        resume: '再開'
      },
      tooltip: {
        suspend: 'テナントを一時停止（Keycloak realm 無効化、解除可能）',
        resume: '停止中のテナントを再開',
        edit: 'テナント情報を編集',
        builtInLocked: '組み込みテナント（system / demo）は変更不可'
      },
      hardDelete: {
        title: 'テナントを完全削除',
        tooltip: {
          confirm: 'テナントを完全削除（業務データ・KC realm・レジストリ行すべて物理削除）'
        },
        warning: {
          title: '元に戻せません',
          intro: '「{displayName}」（{tenantCode}）を完全削除しようとしています。次のすべてが恒久的に消えます：',
          dropBusiness: 'このテナントに紐づく全業務テーブル行（ユーザー・ロール・部署・タスク等）',
          dropRealm: 'Keycloak realm 本体（ユーザー / セッション / クライアント設定すべて）',
          dropRegistry: '中央レジストリ行（core_tenant）',
          noUndo: '回復は不可能。バックアップから手動復元のみ。'
        },
        label: {
          typeCode: '確認のため、テナントコード「{tenantCode}」を正確に入力してください'
        },
        error: {
          mismatch: 'テナントコードが一致しません'
        },
        button: {
          confirm: '完全削除する',
          deleting: '削除中...'
        },
        message: {
          success: 'テナント「{tenantCode}」を完全削除しました',
          failed: 'テナント完全削除に失敗'
        }
      },
      confirm: {
        suspendTitle: 'テナント停止',
        suspendMessage: '「{displayName}」（{tenantCode}）を一時停止しますか？\n\n• Keycloak realm が無効化され、サインインできなくなります\n• 「再開」ボタンでいつでも復帰できます',
        suspendConfirm: '停止する',
        resumeTitle: 'テナント再開',
        resumeMessage: '「{displayName}」（{tenantCode}）を再開しますか？\n\nKeycloak realm が再度有効化され、サインインを受け付けます。',
        resumeConfirm: '再開する'
      },
      support: {
        tooltip: {
          start: 'サポートセッションを開始（このテナントの SUPER_ADMIN 権限で 30 分間操作）',
          disabledSuspended: '停止中のテナントはサポートセッションを開始できません'
        },
        dialog: {
          title: 'サポートセッション開始',
          warning: {
            title: '高権限操作の確認',
            body: '{displayName}（{tenantCode}）の SUPER_ADMIN として 30 分間操作します。\nこのセッション中の全ての操作は監査ログに「[support] <あなたのユーザー名>」として記録されます。'
          },
          reasonLabel: '理由（必須）',
          reasonPlaceholder: '例: OS-1234 ユーザー報告の再現確認',
          reasonHint: '監査ログ（core_oplog.request_body）に保存されます。具体的に書いてください。',
          ttlNote: 'セッションは 30 分後に自動失効します（延長不可）',
          auditNote: '全ての操作が監査ログに残ります',
          writeNote: '読み取り専用モードは未実装（書き込みも可能）— 慎重に',
          starting: '開始中...',
          confirm: 'サポートセッションを開始'
        },
        banner: {
          acting: 'サポートセッション中: {displayName}（{tenantCode}）',
          note: '全ての操作は監査ログに記録されます'
        },
        button: {
          terminate: 'セッション終了'
        },
        message: {
          started: 'サポートセッションを開始しました（{tenantCode}）',
          startFailed: 'サポートセッション開始に失敗',
          terminated: 'サポートセッションを終了しました'
        }
      },
      resendInvite: {
        tooltip: {
          resend: '管理者の招待を再送（メール未着・アドレス誤りのとき）'
        },
        dialog: {
          title: '管理者招待の再送',
          body: '「{displayName}」（{tenantCode}）の管理者へオンボーディング招待を再送します。',
          emailLabel: '送信先',
          emailPlaceholder: 'admin@example.com',
          emailHint: '現在の連絡先メールを初期表示しています。誤りを直すには書き換えてください（ユーザー・Keycloak・テナント連絡先を更新）。そのままなら再送のみ。',
          tokenNote: '新しい招待リンクを発行します。以前のリンクは無効になります。',
          activatedNote: '管理者が未アクティベートの場合のみ有効です。',
          sending: '送信中…',
          confirm: '招待を再送'
        },
        message: {
          success: '招待を再送しました',
          failed: '招待の再送に失敗しました'
        }
      },
      message: {
        createSuccess: 'テナントを作成し、招待メールを送信しました',
        createFailed: 'テナント作成に失敗',
        loadFailed: 'テナント一覧取得に失敗',
        suspendSuccess: 'テナントを停止しました',
        suspendFailed: 'テナント停止に失敗',
        resumeSuccess: 'テナントを再開しました',
        resumeFailed: 'テナント再開に失敗',
        updateSuccess: 'テナント情報を更新しました',
        updateFailed: 'テナント情報の更新に失敗'
      }
    }
  },

  signOut: {
    title: 'サインアウト中...',
    body: 'セッションをクリアし、ID プロバイダー（Keycloak）に通知しています。',
    failed: {
      title: 'サインアウトに失敗しました',
      goLogin: 'ログイン画面へ'
    }
  },

  invite: {
    title: 'アカウントの設定',
    tenantPrefix: 'テナント:',
    passwordLabel: 'パスワード',
    passwordPlaceholder: '8 文字以上',
    passwordConfirmLabel: 'パスワード（確認）',
    passwordConfirmPlaceholder: 'もう一度入力してください',
    button: {
      submit: 'パスワードを設定',
      submitting: '送信中...',
      goLogin: 'ログイン画面へ'
    },
    message: {
      checking: '招待を確認しています...',
      invalidLink: '招待リンクが不正です',
      notValid: 'この招待は無効、期限切れ、または既に使用済みです',
      passwordTooShort: 'パスワードは 8 文字以上にしてください',
      passwordMismatch: '2 回入力したパスワードが一致しません',
      acceptFailed: 'パスワード設定に失敗しました',
      done: 'パスワードを設定しました。ログインしてください。'
    }
  },

  breakGlass: {
    title: 'ブレークグラス・パスワード',
    intro: {
      what: 'これは「緊急用パスワード」です。Keycloak（SSO）が利用できなくなったときに、システムへログインするための独立した認証情報です。',
      howDifferent: 'SSO のログインに使う日常のパスワード（Keycloak 管理）とは別物で、互いに同期しません。同じパスワードを使うのは避けてください。',
      whenUsed: 'KC 障害時など、SSO が使えない緊急時のみ /login の従来パスワード欄から使用します。スーパー管理者のみが保持できます。'
    },
    status: {
      configured: '現在、緊急用パスワードが設定されています',
      notConfigured: 'まだ設定されていません — 万一に備えて今すぐ設定することを推奨します'
    },
    label: {
      newPassword: '新しい緊急用パスワード',
      confirmPassword: '確認のためもう一度入力'
    },
    placeholder: {
      newPassword: '8 文字以上、英数字記号混在',
      confirmPassword: '同じパスワードをもう一度'
    },
    hint: {
      storeSafely: 'パスワードマネージャーや組織の保管庫に必ず保存してください。失念した場合の復旧経路はありません。'
    },
    button: {
      save: '保存',
      saving: '保存中...'
    },
    message: {
      saved: '緊急用パスワードを更新しました'
    },
    error: {
      tooShort: 'パスワードは 8 文字以上必要です',
      mismatch: '2 回入力したパスワードが一致しません',
      saveFailed: '保存に失敗しました'
    }
  },

  passwordReset: {
    title: 'パスワード再設定',
    tenantPrefix: 'テナント:',
    intro: 'SSO から従来のパスワードログインへ移行します。以下に新しいパスワードを設定してください。',
    passwordLabel: '新しいパスワード',
    passwordPlaceholder: '8 文字以上',
    passwordConfirmLabel: 'パスワード（確認）',
    passwordConfirmPlaceholder: 'もう一度入力してください',
    button: {
      submit: 'パスワードを設定',
      submitting: '送信中...',
      goLogin: 'ログイン画面へ'
    },
    message: {
      checking: 'リンクを確認しています...',
      invalidLink: 'リンクが不正です',
      notValid: 'このリンクは無効、期限切れ、または既に使用済みです',
      passwordTooShort: 'パスワードは 8 文字以上にしてください',
      passwordMismatch: '2 回入力したパスワードが一致しません',
      acceptFailed: 'パスワード設定に失敗しました',
      done: 'パスワードを設定しました。ログインしてください。'
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
    openConsoleHint: 'パスワードの変更は ID プロバイダ (Keycloak) の自己管理画面で行います。',
    openConsoleButton: 'Account Console を開く',
    consoleUnavailable: 'SSO ログインが有効でない環境ではパスワード変更画面はご利用いただけません。',
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
      editAdminContactOnly: '内蔵 admin：連絡先情報（メール・表示名）のみ編集可能',
      edit: '編集',
      resetPassword: 'パスワードリセット',
      resetPasswordDisabledSso: 'SSO モードでは無効：ユーザーは Keycloak アカウントコンソールで自分のパスワードを変更します',
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
      lockedHint: '内蔵 admin ユーザーは「連絡先のみ編集可能」です。メールアドレスと表示名は変更できます（ブレークグラス通知のため）。部署・状態・ロールは変更できません。',
      label: {
        username: 'ログインID',
        password: 'パスワード',
        displayName: 'ユーザー名',
        email: 'メール',
        userNo: '番号',
        deptId: '部署',
        status: '状態',
        roles: 'ロール',
        rolesSelected: '{selected} / {total} 選択中',
        mode: 'パスワード設定方法'
      },
      mode: {
        invite: {
          title: '招待メール',
          hint: 'ユーザーがメールから自分で設定',
          willEmail: '{email} に招待メールを送信します'
        },
        direct: {
          title: '管理者設定',
          hint: '初期パスワードを管理者が指定'
        }
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
