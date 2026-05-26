/**
 * 한국어
 *
 * Mirror of ja_JP.js — see that file for the key-tree documentation.
 */
import permissions from './generated/permissions.ko_KR.json'

export default {
  common: {
    confirm: {
      forceTitle: '강제 삭제',
      forceMessage: '{detail}\n\n강제 삭제하면 관련된 모든 링크가 해제됩니다. 계속하시겠습니까?'
    },
    button: {
      forceDelete: '강제 삭제',
      search: '검색',
      reset: '재설정',
      save: '저장',
      cancel: '취소',
      confirm: '확인',
      delete: '삭제',
      edit: '편집',
      new: '신규',
      detail: '상세',
      apply: '적용',
      clear: '지우기',
      selectAll: '전체 선택',
      back: '뒤로',
      close: '닫기',
      refresh: '새로 고침',
      export: '내보내기',
      import: '가져오기',
      upload: '업로드',
      download: '다운로드',
      submit: '제출'
    },
    status: {
      active: '활성',
      inactive: '비활성',
      enabled: '활성',
      disabled: '비활성',
      builtIn: '기본 제공'
    },
    message: {
      saveSuccessful: '저장되었습니다',
      deleteSuccessful: '삭제되었습니다',
      loading: '로딩 중',
      processing: '처리 중...',
      sending: '전송 중...',
      loginSuccessful: '로그인되었습니다',
      networkError: '네트워크 오류',
      sessionExpired: '세션이 만료되었습니다',
      operationSuccessful: '작업이 완료되었습니다',
      iframeLoadFailed: '외부 페이지를 불러올 수 없습니다'
    },
    label: {
      keyword: '키워드'
    },
    placeholder: {
      keyword: '키워드',
      pleaseInput: '입력하세요',
      pleaseSelect: '선택하세요',
      search: '검색...',
      deptId: '부서를 선택하세요'
    },
    tooltip: {
      pagePrevious5: '5 페이지 이전',
      pageNext5: '5 페이지 다음'
    },
    datePicker: {
      today: '오늘',
      now: '지금',
      year: '년',
      yearMonth: '년월',
      placeholder: '날짜 선택',
      startPlaceholder: '시작 날짜',
      endPlaceholder: '종료 날짜',
      dateTimePlaceholder: '날짜 및 시간 선택',
      timePlaceholder: '시간 선택',
      confirm: '확인'
    }
  },

  layout: {
    header: {
      profile: '프로필',
      password: '비밀번호 변경',
      logout: '로그아웃',
      userFallback: '사용자'
    },
    sidebar: {
      adminGroup: '관리자 설정',
      favorites: '즐겨찾기',
      favorite: '즐겨찾기에 추가',
      unfavorite: '즐겨찾기에서 제거'
    },
    tabs: {
      tabAction: '탭 작업',
      closeCurrent: '현재 탭 닫기',
      closeOthers: '다른 탭 닫기',
      closeAll: '모든 탭 닫기'
    },
    footer: {
      copyright: 'Copyright © 2026 SOZONEXT Co.,Ltd.'
    }
  },

  login: {
    tenantLabel: '테넌트',
    tenantPlaceholder: 'default',
    showAdvanced: '고급 (테넌트 전환)',
    hideAdvanced: '접기',
    identifierLabel: '사용자명 / 이메일 / 번호',
    identifierPlaceholder: '사용자명 · 이메일 · 사용자 번호',
    passwordLabel: '비밀번호',
    passwordPlaceholder: '비밀번호를 입력하세요',
    submit: '로그인',
    submitting: '로그인 중...',
    forgotPassword: '비밀번호를 잊으셨나요?',
    ssoDivider: '또는',
    ssoButton: 'SSO로 로그인',
    ssoOnlyHint: '싱글 사인온(SSO)으로 로그인하세요.',
    passwordBreakGlass: '비밀번호 로그인 모드(긴급용)',
    backToSso: 'SSO로 돌아가기',
    passwordModeHotzone: '2초 안에 5번 클릭하면 비밀번호 로그인 해제',
    message: {
      enterUsername: '사용자명을 입력하세요',
      enterPassword: '비밀번호를 입력하세요',
      loginFailed: '로그인에 실패했습니다',
      ssoFailed: 'SSO 로그인 실패'
    }
  },

  invite: {
    title: '계정 설정',
    tenantPrefix: '테넌트:',
    passwordLabel: '비밀번호',
    passwordPlaceholder: '8자 이상',
    passwordConfirmLabel: '비밀번호 확인',
    passwordConfirmPlaceholder: '비밀번호를 다시 입력하세요',
    button: {
      submit: '비밀번호 설정',
      submitting: '전송 중...',
      goLogin: '로그인 화면으로'
    },
    message: {
      checking: '초대 확인 중...',
      invalidLink: '초대 링크가 잘못되었습니다',
      notValid: '이 초대는 유효하지 않거나 만료되었거나 이미 사용되었습니다',
      passwordTooShort: '비밀번호는 8자 이상이어야 합니다',
      passwordMismatch: '두 비밀번호가 일치하지 않습니다',
      acceptFailed: '비밀번호 설정 실패',
      done: '비밀번호가 설정되었습니다. 로그인하세요.'
    }
  },

  forget: {
    title: '비밀번호 재설정',
    mobileLabel: '휴대폰 번호',
    mobilePlaceholder: '등록된 휴대폰 번호를 입력하세요',
    newPasswordLabel: '새 비밀번호',
    newPasswordPlaceholder: '새 로그인 비밀번호를 입력하세요',
    confirmPasswordLabel: '비밀번호 확인',
    confirmPasswordPlaceholder: '비밀번호를 다시 입력하세요',
    captchaLabel: '인증 코드',
    captchaPlaceholder: 'SMS 인증 코드',
    sendCaptcha: '코드 전송',
    sentCountdown: '전송됨 {n}s',
    backToLogin: '로그인으로 돌아가기',
    submit: '비밀번호 변경',
    submitting: '처리 중...',
    imgCaptchaTitle: '인증 코드 전송',
    imgCaptchaPlaceholder: '이미지 인증 코드 입력',
    refresh: '새로 고침',
    loading: '로딩 중...',
    sendNow: '지금 전송',
    sending: '전송 중...',
    message: {
      enterMobile: '휴대폰 번호를 입력하세요',
      enterImgCode: '이미지 인증 코드를 입력하세요',
      smsSent: 'SMS 인증 코드를 전송했습니다',
      passwordReset: '비밀번호가 변경되었습니다',
      enterConfirmPassword: '비밀번호 확인을 입력하세요',
      passwordMismatch: '두 비밀번호가 일치하지 않습니다'
    }
  },

  password: {
    oldPassword: '현재 비밀번호',
    password: '새 비밀번호',
    confirmPassword: '비밀번호 확인',
    openConsoleHint: '비밀번호 변경은 ID 제공자(Keycloak)에서 관리합니다. 셀프 계정 콘솔을 여세요.',
    openConsoleButton: 'Account Console 열기',
    consoleUnavailable: 'SSO가 활성화되지 않은 환경에서는 비밀번호 변경을 사용할 수 없습니다.',
    message: {
      inconsistent: '두 비밀번호가 일치하지 않습니다'
    }
  },

  notFound: {
    title: '404',
    message: '페이지를 찾을 수 없습니다',
    backHome: '홈으로'
  },

  router: {
    title: {
      login: '로그인',
      forget: '비밀번호 재설정',
      notFound: '404',
      profile: '프로필'
    }
  },

  profile: {
    title: '프로필',
    label: {
      deptId: '부서',
      userId: '사용자 ID',
      username: '로그인 ID',
      displayName: '사용자명',
      email: '이메일',
      userNo: '사용자 번호',
      tenantId: '테넌트',
      roles: '역할',
      authorities: '권한'
    }
  },

  user: {
    search: {
      placeholder: { keyword: '로그인 ID / 이메일 / 사용자명' },
      label: { deptId: '부서' }
    },
    column: {
      username: '로그인 ID',
      displayName: '사용자명',
      userNo: '번호',
      email: '이메일',
      deptId: '부서',
      status: '상태',
      actions: '작업'
    },
    tooltip: {
      editDisabled: '기본 제공 사용자는 편집할 수 없습니다',
      edit: '편집',
      resetPassword: '비밀번호 재설정',
      statusChangeDisabled: '기본 제공 사용자 상태는 변경할 수 없습니다',
      toggleStatus: '활성/비활성',
      forceLogout: '강제 로그아웃',
      deleteDisabled: '기본 제공 사용자는 삭제할 수 없습니다'
    },
    confirm: {
      deleteTitle: '사용자 삭제',
      deleteMessage: '「{name}」을(를) 삭제하시겠습니까?',
      forceLogoutTitle: '강제 로그아웃',
      forceLogoutMessage: '「{name}」을(를) 강제 로그아웃하시겠습니까?\n(진행 중인 access token은 다음 API 호출 시점에 무효화됩니다)'
    },
    message: {
      deleteFailed: '삭제 실패',
      forceLogoutSuccess: '강제 로그아웃되었습니다'
    },
    edit: {
      titleEdit: '사용자 편집',
      titleCreate: '사용자 신규',
      lockedHint: '기본 제공 admin 사용자는 읽기 전용입니다. 비밀번호 변경만 가능합니다(「비밀번호 재설정」 API 경유).',
      label: {
        username: '로그인 ID',
        password: '비밀번호',
        displayName: '사용자명',
        email: '이메일',
        userNo: '번호',
        deptId: '부서',
        status: '상태',
        roles: '역할',
        rolesSelected: '{selected} / {total} 선택됨',
        mode: '비밀번호 설정 방법'
      },
      mode: {
        invite: {
          title: '초대 메일',
          hint: '사용자가 메일에서 직접 설정',
          willEmail: '초대 메일이 {email} 로 발송됩니다'
        },
        direct: {
          title: '관리자 설정',
          hint: '관리자가 초기 비밀번호 지정'
        }
      },
      placeholder: {
        password: '8자 이상 / 4종 문자 유형'
      },
      message: {
        noRoles: '역할이 없습니다',
        saving: '저장 중...',
        updateFailed: '업데이트 실패',
        createFailed: '생성 실패',
        assignRolesFailed: '역할 할당 실패'
      }
    },
    resetPassword: {
      title: '비밀번호 재설정',
      label: {
        user: '사용자',
        newPassword: '새 비밀번호',
        confirmPassword: '비밀번호 확인'
      },
      placeholder: {
        value: '8자 이상 / 4종 문자 유형',
        confirm: '동일한 비밀번호를 다시 입력'
      },
      hint: '※ 공개 유출 코퍼스(HIBP)에 등록된 비밀번호는 거부됩니다.',
      button: { reset: '재설정' },
      error: {
        tooShort: '비밀번호는 8자 이상이어야 합니다',
        mismatch: '확인 비밀번호가 일치하지 않습니다'
      },
      message: {
        saving: '저장 중...',
        success: '비밀번호가 재설정되었습니다',
        failed: '실패'
      }
    }
  },

  role: {
    search: {
      placeholder: { keyword: '명칭 / 설명' }
    },
    column: {
      name: '명칭',
      description: '설명',
      dataScope: '데이터 범위',
      status: '상태',
      actions: '작업'
    },
    option: {
      scope: {
        all: '전체',
        deptAndSub: '본 부서+하위',
        dept: '본 부서',
        self: '본인만',
        custom: '사용자 정의'
      }
    },
    tooltip: {
      viewOnly: '기본 제공 역할은 조회만 가능합니다(편집 버튼으로 상세 표시)',
      edit: '편집',
      deleteDisabled: '기본 제공 역할은 삭제할 수 없습니다'
    },
    confirm: {
      deleteTitle: '역할 삭제',
      deleteMessage: '「{name}」을(를) 삭제하시겠습니까?',
      inUseMessage: '이 역할은 {users}명의 사용자에게 할당되어 있습니다.\n\n강제 삭제하면 사용자 할당이 모두 해제되고, 권한 / 메뉴 / 부서 연결도 함께 정리됩니다. 계속하시겠습니까?'
    },
    message: {
      deleteBuiltInFailed: '기본 제공 역할은 삭제할 수 없습니다',
      deleteFailed: '삭제 실패'
    },
    edit: {
      titleEdit: '역할 편집',
      titleCreate: '역할 신규',
      lockedHint: '기본 제공 역할은 읽기 전용입니다. 명칭·데이터 범위·권한/메뉴/부서 할당을 변경하면 인증 루프나 권한 드리프트가 발생할 수 있습니다.',
      tab: {
        permissions: '권한',
        menus: '메뉴',
        depts: '부서'
      },
      label: {
        name: '명칭',
        description: '설명',
        dataScope: '데이터 범위',
        status: '상태'
      },
      option: {
        scope: {
          all: '전체 (ALL)',
          deptAndSub: '본 부서 + 하위 (DEPT_AND_SUB)',
          deptOnly: '본 부서만 (DEPT)',
          self: '본인만 (SELF)',
          custom: '사용자 정의 (CUSTOM)'
        }
      },
      message: {
        noPermissions: '권한이 없습니다',
        noMenus: '메뉴가 없습니다',
        noDepts: '부서가 없습니다',
        saving: '저장 중...',
        updateFailed: '업데이트 실패',
        createFailed: '생성 실패'
      },
      dept: {
        impliedTag: '(자동)',
        impliedTooltip: '상위 부서 선택에 의해 자동 포함됩니다. 제외하려면 상위 선택을 해제하세요.'
      },
      tooltip: {
        locked: '기본 제공 역할은 편집할 수 없습니다'
      }
    }
  },

  dept: {
    title: '부서 관리',
    button: { addRoot: '루트 추가' },
    column: {
      name: '명칭',
      code: '코드',
      level: '레벨',
      leader: '책임자',
      status: '상태',
      actions: '작업'
    },
    message: {
      noDepts: '부서가 없습니다',
      loadUsersFailed: '사용자 목록 가져오기 실패',
      userDeleted: '(삭제됨)',
      deleteFailed: '삭제 실패'
    },
    tooltip: {
      addChild: '하위 추가',
      edit: '편집'
    },
    confirm: {
      deleteTitle: '부서 삭제',
      deleteMessage: '「{name}」을(를) 삭제하시겠습니까?',
      inUseMessage: '이 부서에는: 하위 부서 {children}개, 소속 사용자 {users}명, 참조 역할 {roles}개(SCOPE_CUSTOM 데이터 범위)가 있습니다.\n\n강제 삭제하면 이 부서와 모든 하위 부서를 함께 소프트 삭제하며, 소속 사용자의 부서 설정 및 참조 역할의 커스텀 데이터 범위에서도 해제됩니다. 계속하시겠습니까?'
    },
    edit: {
      titleEdit: '부서 편집',
      titleCreate: '부서 신규',
      label: {
        parentId: '상위 부서',
        code: '코드',
        name: '명칭',
        sortOrder: '정렬 순서',
        status: '상태',
        leader: '책임자'
      },
      placeholder: {
        parentId: '루트인 경우 비워둠',
        code: 'HQ / TOKYO',
        name: '본사',
        leader: '미지정'
      },
      hint: {
        rootParent: '비워두면 루트 부서가 됩니다',
        leaderInfo: '표시용 메모입니다. 권한이나 데이터 범위에는 영향을 주지 않습니다.'
      },
      message: {
        updateFailed: '업데이트 실패',
        createFailed: '생성 실패'
      }
    }
  },

  menu: {
    title: '메뉴 관리',
    button: { addRoot: '루트 추가' },
    column: {
      title: '명칭 / 경로',
      type: '유형',
      component: '컴포넌트',
      permission: '권한',
      hide: '숨김',
      actions: '작업'
    },
    message: {
      noMenus: '메뉴가 없습니다',
      fetchFailed: '메뉴 가져오기에 실패했습니다'
    },
    option: {
      type: {
        directory: '디렉터리',
        menu: '메뉴',
        button: '버튼'
      },
      yesNo: { no: '아니오', yes: '예' }
    },
    tooltip: {
      addChild: '하위 추가',
      edit: '편집'
    },
    confirm: {
      deleteTitle: '메뉴 삭제',
      deleteMessage: '「{code}」을(를) 삭제하시겠습니까?'
    },
    edit: {
      titleEdit: '메뉴 편집',
      titleCreate: '메뉴 신규',
      label: {
        code: '코드',
        name: '명칭',
        titleI18n: '다국어 제목',
        type: '유형',
        sortOrder: '정렬 순서',
        parentId: '상위 메뉴',
        path: '경로',
        component: '컴포넌트',
        icon: '아이콘',
        permissionCode: '권한 코드',
        hide: '숨김',
        hideSidebar: '사이드바 숨김',
        hideFooter: '푸터 숨김',
        pinned: '상단 고정'
      },
      tip: {
        hide: '사이드 메뉴 트리에서 이 항목을 숨깁니다 (라우트는 유효, 상세 페이지/동적 라우트용)',
        hideSidebar: '이 페이지를 열 때 왼쪽 사이드바를 숨깁니다 (인쇄 미리보기, 전체 화면 마법사 등)',
        hideFooter: '이 페이지를 열 때 하단 푸터(저작권 표시)를 숨깁니다',
        pinned: '이 메뉴를 사이드바 최상단에 고정 표시합니다. 다른 메뉴와는 구분선으로 분리됩니다',
        pinnedDisabled: '상단 고정은 「메뉴」 유형만 설정할 수 있습니다. 디렉터리와 버튼은 고정할 수 없습니다'
      },
      placeholder: {
        code: 'system.user',
        parentId: '루트인 경우 비워둠',
        path: '/system/user',
        component: '/system/User/User',
        permissionCode: 'user:read',
        titleI18nPrimary: '필수 (기본 로케일)',
        titleI18nOptional: '선택 사항'
      },
      error: {
        titleJaRequired: '일본어 제목은 필수입니다'
      }
    }
  },

  // 권한 코드 → 표시명（backend I18nPermissionPatcher 가 dev 시작 시 자동 채움）
  permission: permissions,

  dataTable: {
    emptyState: '데이터 없음',
    loading: '로딩 중...',
    pagination: {
      total: '총 {n}건',
      perPage: '{n}건/페이지'
    }
  },

  picker: {
    icon: {
      selectPlaceholder: '아이콘 선택',
      searchPlaceholder: '아이콘 검색...',
      noResults: '일치하는 아이콘이 없습니다'
    }
  },

  task: {
    title: '작업 (데이터 범위 데모)',
    description: '역할에 따라 보이는 작업이 다릅니다. 자세히:',
    search: {
      label: { keyword: '키워드', status: '상태' },
      placeholder: { keyword: '제목 검색' }
    },
    column: {
      title: '제목',
      deptId: '부서',
      status: '상태',
      priority: '우선순위',
      assignee: '담당자',
      creator: '작성자',
      dueDate: '마감일',
      actions: '작업'
    },
    emptyState: '데이터가 없습니다',
    status: { todo: '미시작', doing: '진행 중', done: '완료', cancelled: '취소' },
    priority: { low: '낮음', medium: '중간', high: '높음' },
    confirm: {
      deleteTitle: '작업 삭제',
      deleteMessage: '「{title}」을(를) 삭제하시겠습니까?'
    },
    message: {
      loadFailed: '불러오기 실패',
      saveFailed: '저장 실패',
      saveSuccess: '저장되었습니다',
      deleteFailed: '삭제 실패',
      deleteSuccess: '삭제되었습니다'
    },
    edit: {
      titleEdit: '작업 편집',
      titleCreate: '작업 신규',
      label: { content: '내용' },
      placeholder: {
        title: '작업 제목',
        deptSelect: '부서 선택',
        optional: '선택',
        unassigned: '미지정',
        dueDate: '마감일 선택'
      }
    },
    option: { statusAll: '전체' }
  },

  oplog: {
    search: {
      label: {
        module: '모듈',
        action: '동작',
        user: '사용자',
        targetType: '대상 유형',
        targetId: '대상 ID',
        result: '결과'
      },
      placeholder: {
        module: '예: system / pms / iot',
        action: '예: role.create',
        user: '모든 사용자',
        targetType: '예: role / user'
      }
    },
    option: {
      result: { all: '전체', success: '성공', failure: '실패' }
    },
    column: {
      createTime: '시각',
      username: '사용자',
      module: '모듈',
      action: '동작',
      targetType: '대상',
      clientIp: 'IP',
      success: '결과',
      costMs: 'ms',
      actions: '상세'
    },
    status: { success: '성공', failure: '실패' },
    detail: {
      title: '작업 로그 상세',
      label: {
        createTime: '시각',
        costMs: '소요',
        username: '사용자',
        userId: '사용자 ID',
        module: '모듈',
        action: '동작',
        targetType: '대상 유형',
        targetId: '대상 ID',
        method: '메서드',
        result: '결과',
        uri: 'URI',
        clientIp: '클라이언트 IP',
        userAgent: 'User-Agent'
      },
      section: {
        errorMsg: '오류 메시지',
        requestBody: '요청 본문(비밀번호 자동 마스킹됨)'
      },
      message: { empty: '(없음)' },
      button: { close: '닫기' }
    },
    message: {
      fetchFailed: '가져오기 실패'
    }
  }
}
