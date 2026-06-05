/**
 * 한국어
 *
 * Mirror of ja_JP.js — see that file for the key-tree documentation.
 */
import permissions from './generated/permissions.ko_KR.json'

export default {
  error: {
    dict: {
      codeExists: '사전 코드가 이미 존재합니다',
      codeIsBuiltin: '이 코드는 내장(enum) 사전이라 관리할 수 없습니다',
      typeBuiltinProtected: '내장 사전 유형은 삭제할 수 없습니다',
      typeHasItems: '사전에 항목이 남아 있습니다. 먼저 삭제하세요',
      itemsNotEditable: '내장 사전 항목은 편집할 수 없습니다',
      itemValueExists: '사전 항목 값이 이미 존재합니다',
      itemBranchProtected: '이 값은 코드(enum)로 정의되어 삭제할 수 없습니다. 비활성화하세요',
      itemInUse: '이 값은 기존 데이터가 사용 중이라 삭제할 수 없습니다. 비활성화하세요',
      notFound: '사전 또는 항목을 찾을 수 없습니다',
      invalidValue: '잘못된 사전 값입니다'
    }
  },
  dict: {
    title: '사전 관리',
    description: '코드 내장 사전(status 등)과 런타임 편집 가능한 관리 사전을 한 곳에서 관리합니다.',
    type: {
      columnCode: '코드', columnName: '이름', columnItems: '항목 수', columnActions: '작업',
      new: '사전 추가', edit: '사전 편집', create: '사전 생성',
      builtin: '내장', selectHint: '왼쪽 사전을 선택하면 항목이 표시됩니다', empty: '사전이 없습니다',
      codeFrozen: '코드는 생성 후 변경할 수 없습니다'
    },
    item: {
      columnValue: '값', columnLabel: '라벨', columnSort: '정렬', columnStatus: '상태', columnActions: '작업',
      new: '항목 추가', edit: '항목 편집', create: '항목 생성',
      enabled: '사용', disabled: '미사용', empty: '항목이 없습니다',
      valueFrozen: '값은 생성 후 변경할 수 없습니다(이력 데이터가 참조)'
    },
    label: {
      code: '코드', name: '이름', remark: '비고', value: '값', label: '라벨',
      sort: '정렬', status: '상태', cssClass: '색상'
    },
    message: {
      saveSuccess: '저장했습니다', saveFailed: '저장 실패',
      deleteSuccess: '삭제했습니다', deleteFailed: '삭제 실패', loadFailed: '불러오기 실패'
    },
    confirm: {
      deleteTypeTitle: '사전 삭제', deleteTypeMessage: '사전 "{code}"을(를) 삭제하시겠습니까?',
      deleteItemTitle: '항목 삭제', deleteItemMessage: '항목 "{value}"을(를) 삭제하시겠습니까? 비활성화가 더 안전합니다.'
    }
  },
  job: {
    search: { placeholder: { keyword: '작업명 / 코드로 검색' } },
    triggerType: { cron: '예약', manual: '수동', startup: '시작' },
    runStatus: { running: '실행 중', success: '성공', fail: '실패', skipped: '건너뜀', none: '—' },
    column: { label: '이름', name: '작업', cron: 'Cron 식', status: '상태', nextFire: '다음 실행', lastResult: '최근 결과', actions: '작업' },
    action: { edit: 'Cron 편집', run: '즉시 실행', viewLog: '로그' },
    edit: { title: '작업 설정', label: { cron: 'Cron 식', maxRunSeconds: '최대 실행 초', concurrent: '중복 실행 허용', remark: '비고' }, tip: { concurrent: '이전 실행이 아직 끝나지 않았을 때 다음 트리거를 병렬로 실행할지. 끔(기본): 실행 중이면 이번 트리거를 건너뛰어 같은 작업이 겹치지 않음(분산 락으로 보장). 병렬 실행이 안전한 경우에만 켜기.' }, placeholder: { cron: '예: 0 0 3 * * *', remark: '비고(선택)' } },
    log: { title: '실행 로그', column: { triggerType: '트리거', status: '상태', startTime: '시작', duration: '소요(ms)', node: '노드', triggeredBy: '실행자', error: '오류' }, empty: '실행 기록이 없습니다' },
    confirm: { runTitle: '즉시 실행', runMessage: '"{name}"을(를) 지금 한 번 실행하시겠습니까?' },
    message: { saveSuccess: '저장했습니다', runStarted: '실행을 시작했습니다', enabled: '활성화했습니다', disabled: '중지했습니다', updateFailed: '저장 실패', runFailed: '실행 실패' }
  },
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
      breakGlass: '비상용 비밀번호',
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
      copyright: 'Copyright © 2026 ACME Co.,Ltd.'
    },
    notification: {
      title: '알림',
      empty: '알림이 없습니다',
      markAllRead: '모두 읽음으로 표시',
      actionRequired: '처리 필요'
    }
  },

  login: {
    tenantLabel: '테넌트',
    tenantPlaceholder: 'demo',
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
    ssoRedirecting: 'SSO로 이동 중...',
    ssoUnreachable: {
      title: 'SSO 서버에 연결할 수 없습니다',
      body: 'Keycloak(SSO)이 현재 응답하지 않습니다. 점검 중이거나 재시작 중일 수 있으며, 네트워크 문제일 수도 있습니다. 비상용 비밀번호로 계속 로그인하거나 잠시 후 다시 시도해 주세요.',
      useBreakGlass: '비상용 비밀번호로 계속',
      retry: 'SSO 재시도',
      retrying: '확인 중...'
    },
    tenantRecovered: '테넌트 "{stale}"이(가) 더 이상 존재하지 않아 기본 테넌트로 전환했습니다.',
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

  platform: {
    event: {
      title: '도메인 이벤트',
      column: { occurredAt: '발생 시각', eventType: '이벤트 유형', aggregate: '애그리거트', status: '전송 상태', attempts: '시도', actions: '작업' },
      state: { all: '전체', pending: '대기', dispatched: '전송 완료', failed: '실패' },
      actorType: { human: '사람', ai: 'AI', system: '시스템' },
      search: { eventTypePlaceholder: '이벤트 유형으로 필터', keywordPlaceholder: '애그리거트ID / 유형 / traceId' },
      button: { redriveAll: '실패 일괄 재전송' },
      tooltip: { view: '상세 / payload 보기', redrive: '재전송(대기로 되돌림)', redriveOnlyFailed: '실패 이벤트만 재전송할 수 있습니다' },
      drawer: { title: '이벤트 상세', payload: '페이로드', actor: '수행자', dispatchedAt: '전송 시각', traceId: '추적 ID' },
      confirm: {
        redriveTitle: '이벤트 재전송', redriveMessage: '"{eventType}"을(를) 대기로 되돌려 재전송하시겠습니까?', redriveConfirm: '재전송',
        redriveAllTitle: '실패 이벤트 일괄 재전송', redriveAllMessage: '모든 실패 이벤트를 대기로 되돌려 재전송하시겠습니까?', redriveAllConfirm: '일괄 재전송'
      },
      message: { redriveSuccess: '재전송을 요청했습니다', redriveFailed: '재전송 실패', redriveAllSuccess: '{count}건 재전송했습니다', loadFailed: '이벤트 로드 실패' }
    },
    user: {
      title: '플랫폼 사용자',
      column: { username: '사용자명', displayName: '표시 이름', email: '이메일', role: '역할', status: '상태', createTime: '생성 시각', actions: '작업' },
      role: { admin: '관리자', operator: '운영' },
      action: { disable: '비활성', enable: '활성', reset: '비밀번호 재설정', delete: '삭제' },
      confirm: {
        disableTitle: '사용자 비활성', disableMessage: '"{username}"을(를) 비활성하시겠습니까? Keycloak에서도 비활성되어 로그인할 수 없습니다.',
        resetTitle: '비밀번호 재설정', resetMessage: '"{username}"의 비밀번호를 재설정하시겠습니까? 임시 비밀번호가 발급됩니다.',
        deleteTitle: '사용자 삭제', deleteMessage: '"{username}"을(를) 삭제하시겠습니까? 되돌릴 수 없습니다(Keycloak 사용자도 삭제).'
      },
      secret: { titleNew: '플랫폼 사용자 생성됨', titleReset: '비밀번호 재설정됨' },
      search: { placeholder: '사용자명 / 이메일로 검색' },
      button: { new: '새 사용자' },
      create: {
        title: '플랫폼 사용자 추가',
        intro: 'system 테넌트에 운영 담당자를 추가합니다: Keycloak system realm에 사용자를 만들고 Platform Admin 권한을 부여합니다.',
        usernamePlaceholder: 'ops2',
        usernameHint: '소문자 영숫자 / 하이픈 / 밑줄. 생성 후 변경 불가.',
        displayNamePlaceholder: '운영 담당',
        emailPlaceholder: 'ops2@example.com',
        creating: '생성 중...',
        doneIntro: '"{username}" 생성됨. 아래 임시 비밀번호를 본인에게 안전하게 전달하세요(첫 로그인 시 변경 필요).',
        tempPassword: '임시 비밀번호',
        tempPasswordHint: '이 창을 닫으면 다시 표시되지 않습니다.',
        copy: '복사'
      },
      message: {
        loadFailed: '사용자 목록 로드 실패', required: '사용자명과 표시 이름은 필수입니다',
        createSuccess: '플랫폼 사용자 생성됨', createFailed: '사용자 생성 실패',
        copied: '복사됨', copyFailed: '복사 실패',
        disableSuccess: '비활성화됨', enableSuccess: '활성화됨', resetSuccess: '비밀번호 재설정됨',
        deleteSuccess: '삭제됨', opFailed: '작업 실패'
      }
    },
    tenant: {
      column: {
        tenantCode: '테넌트 코드',
        displayName: '표시명',
        contactEmail: '연락처 이메일',
        status: '상태',
        createTime: '생성 시각',
        actions: '작업'
      },
      status: { active: '활성', suspended: '중단' },
      search: { placeholder: '코드 또는 이름으로 검색' },
      dashboard: {
        total: '전체 테넌트',
        newThisMonth: '이번 달 신규',
        statusTitle: '상태 분포',
        trendTitle: '신규 테넌트 추이 (월별)',
        trendSeries: '신규 테넌트'
      },
      ops: {
        empty: '없음',
        activation: {
          title: '온보딩 / 활성화 퍼널', pending: '미활성', expired: '만료', rate: '활성화율',
          median: '온보딩 소요(중앙값)', listTitle: '활성화 대기 테넌트', expiredBadge: '만료',
          tip: {
            pending: '초대했지만 관리자가 아직 활성화하지 않은 테넌트 수',
            expired: '초대가 만료되어 한 번도 활성화되지 않은 테넌트 수',
            rate: '비내장 테넌트 중 관리자가 로그인에 성공한 비율',
            median: '테넌트 생성부터 관리자 첫 로그인까지 소요 시간의 중앙값'
          }
        },
        engagement: {
          title: '활성도 / 참여', active7d: '활성(7일)', active30d: '활성(30일)', dau: 'DAU', mau: 'MAU',
          silent: '휴면', trendTitle: '로그인 추이(14일)', silentListTitle: '휴면 테넌트(30일 무로그인)', never: '로그인 없음',
          tip: {
            active7d: '최근 7일 내 로그인 성공이 있는 테넌트 수',
            active30d: '최근 30일 내 로그인 성공이 있는 테넌트 수',
            dau: '최근 24시간 활성 사용자 수(중복 제거)',
            mau: '최근 30일 활성 사용자 수(중복 제거)',
            silent: '운영 중이나 최근 30일 로그인이 없는 테넌트 수(이탈 위험)'
          }
        },
        reliability: {
          title: '플랫폼 상태', jobFailures: '작업 실패(24h)', eventBacklog: '이벤트 적체',
          backlogAge: '최장 적체', oplogErrors: 'API 오류(24h)', failuresListTitle: '최근 작업 실패',
          errorsListTitle: '최근 API 오류', statePending: '대기', stateFailed: '실패',
          tip: {
            jobFailures: '최근 24시간 실패한 예약 작업 횟수',
            eventBacklog: '미전송 도메인 이벤트 수(대기+실패)',
            backlogAge: '가장 오래된 미전송 이벤트의 적체 시간',
            oplogErrors: '최근 24시간 예기치 않은 서버 오류(500) 수'
          }
        },
        security: {
          title: '보안 / 권한 모니터링', activeSupport: '지원 세션 진행중', support7d: '지원(7일)',
          loginFailures: '로그인 실패(24h)', passwordResets: 'PW 재설정(7일)', supportListTitle: '최근 지원 세션',
          breakGlass: '비상 접근(7일)', breakGlassListTitle: '최근 비상 접근(SSO 우회)'
        }
      },
      recycleBinHint: {
        title: '삭제는 휴지통 방식입니다:',
        body: '먼저 "중단"하세요(Keycloak realm 이 비활성화되어 로그인할 수 없게 되지만 데이터는 유지됩니다). 정말로 삭제하려면 중단된 행에 나타나는 빨간 휴지통 아이콘을 사용하세요 — 테넌트 코드를 입력해 확인하면 비즈니스 데이터·KC realm·레지스트리 항목이 모두 물리적으로 삭제되며, 되돌릴 수 없습니다.'
      },
      edit: {
        titleCreate: '신규 테넌트',
        titleEdit: '테넌트 편집',
        intro: '한 번의 작업으로 Keycloak realm 과 중앙 레지스트리 항목을 생성합니다. 테넌트 코드는 생성 후 변경할 수 없습니다.',
        editIntro: '테넌트 코드는 변경할 수 없습니다. 표시명과 연락처 이메일만 수정할 수 있습니다.',
        label: {
          tenantCode: '테넌트 코드',
          displayName: '표시명',
          contactEmail: '연락처 이메일',
          adminUsername: '관리자 사용자명'
        },
        placeholder: {
          tenantCode: 'acme',
          displayName: 'Acme 주식회사',
          contactEmail: 'admin@acme.example',
          adminUsername: 'admin'
        },
        hint: {
          tenantCode: '소문자 영숫자·하이픈(RFC1035 레이블). Keycloak realm 이름과 서브도메인으로 사용됩니다.',
          contactEmail: '선택 - 첫 관리자 초대에 사용',
          adminUsername: '비워 두면 연락처 이메일의 로컬 부분에서 자동 생성되며, 나중에 변경할 수 있습니다.'
        },
        error: {
          invalidCode: '테넌트 코드는 소문자 RFC1035 레이블이어야 합니다(소문자 영숫자·하이픈)',
          missingDisplayName: '표시명을 입력해 주세요',
          invalidAdminUsername: '관리자 사용자명은 소문자로 시작하고 소문자 영숫자·하이픈·밑줄만 사용할 수 있습니다'
        },
        saving: '저장 중...'
      },
      button: {
        new: '신규 테넌트',
        edit: '편집',
        suspend: '중단',
        resume: '재개'
      },
      tooltip: {
        suspend: '테넌트 중단(Keycloak realm 비활성화, 되돌릴 수 있음)',
        resume: '중단된 테넌트 재개',
        edit: '테넌트 정보 편집',
        builtInLocked: '기본 제공 테넌트(system / demo)는 변경할 수 없습니다'
      },
      hardDelete: {
        title: '테넌트 완전 삭제',
        tooltip: {
          confirm: '테넌트 완전 삭제(비즈니스 데이터·KC realm·레지스트리 항목 모두 물리적으로 삭제)'
        },
        warning: {
          title: '되돌릴 수 없습니다',
          intro: '"{displayName}"({tenantCode})을(를) 완전히 삭제하려고 합니다. 다음 항목이 모두 영구적으로 사라집니다:',
          dropBusiness: '이 테넌트에 연결된 모든 비즈니스 테이블 데이터(사용자·역할·부서·작업 등)',
          dropRealm: 'Keycloak realm 자체(모든 사용자 / 세션 / 클라이언트 설정)',
          dropRegistry: '중앙 레지스트리 항목(core_tenant)',
          noUndo: '복구할 수 없으며, 백업에서 수동 복원만 가능합니다.'
        },
        label: {
          typeCode: '확인을 위해 테넌트 코드 "{tenantCode}"를 정확히 입력하세요'
        },
        error: {
          mismatch: '테넌트 코드가 일치하지 않습니다'
        },
        button: {
          confirm: '완전 삭제',
          deleting: '삭제 중...'
        },
        message: {
          success: '테넌트 "{tenantCode}"를 완전히 삭제했습니다',
          failed: '테넌트 완전 삭제 실패'
        }
      },
      confirm: {
        suspendTitle: '테넌트 중단',
        suspendMessage: '"{displayName}"({tenantCode})을(를) 중단하시겠습니까?\n\n• Keycloak realm 이 비활성화되어 로그인할 수 없습니다\n• "재개" 버튼으로 언제든지 복구할 수 있습니다',
        suspendConfirm: '중단',
        resumeTitle: '테넌트 재개',
        resumeMessage: '"{displayName}"({tenantCode})을(를) 재개하시겠습니까?\n\nKeycloak realm 이 다시 활성화되어 로그인을 받습니다.',
        resumeConfirm: '재개'
      },
      support: {
        tooltip: {
          start: '지원 세션 시작(이 테넌트의 SUPER_ADMIN 권한으로 30분간 작업)',
          disabledSuspended: '중단된 테넌트는 지원 세션을 시작할 수 없습니다'
        },
        dialog: {
          title: '지원 세션 시작',
          warning: {
            title: '고권한 작업 확인',
            body: '{displayName}({tenantCode})의 SUPER_ADMIN 으로 30분간 작업합니다.\n이 세션 중의 모든 작업은 감사 로그에 "[support] <사용자명>"으로 기록됩니다.'
          },
          reasonLabel: '사유(필수)',
          reasonPlaceholder: '예: OS-1234 사용자 보고 문제 재현 확인',
          reasonHint: '감사 로그(core_oplog.request_body)에 저장됩니다. 구체적으로 작성하세요.',
          ttlNote: '세션은 30분 후 자동으로 만료됩니다(연장 불가)',
          auditNote: '모든 작업이 감사 로그에 기록됩니다',
          writeNote: '읽기 전용 모드는 미구현(쓰기도 가능) — 신중하게 진행하세요',
          starting: '시작 중...',
          confirm: '지원 세션 시작'
        },
        banner: {
          acting: '지원 세션 진행 중: {displayName}({tenantCode})',
          note: '모든 작업이 감사 로그에 기록됩니다'
        },
        button: {
          terminate: '세션 종료'
        },
        message: {
          started: '지원 세션을 시작했습니다({tenantCode})',
          startFailed: '지원 세션 시작 실패',
          terminated: '지원 세션을 종료했습니다'
        }
      },
      resendInvite: {
        tooltip: {
          resend: '관리자 온보딩 초대 재전송 (이메일 미수신 또는 주소 오류)'
        },
        dialog: {
          title: '관리자 초대 재전송',
          body: '"{displayName}"({tenantCode}) 관리자에게 온보딩 초대를 다시 보냅니다.',
          emailLabel: '받는 사람',
          emailPlaceholder: 'admin@example.com',
          emailHint: '현재 연락 이메일이 채워져 있습니다. 잘못된 주소는 수정하세요(사용자·Keycloak·테넌트 연락처를 갱신). 그대로 두면 재전송만 합니다.',
          tokenNote: '새 초대 링크가 발급되며 이전 링크는 무효가 됩니다.',
          activatedNote: '관리자가 아직 활성화하지 않은 경우에만 작동합니다.',
          sending: '전송 중…',
          confirm: '초대 재전송'
        },
        message: {
          success: '초대를 다시 보냈습니다',
          failed: '초대 재전송에 실패했습니다'
        }
      },
      message: {
        createSuccess: '테넌트를 생성하고 초대 이메일을 보냈습니다',
        createFailed: '테넌트 생성 실패',
        loadFailed: '테넌트 목록 로드 실패',
        suspendSuccess: '테넌트를 중단했습니다',
        suspendFailed: '테넌트 중단 실패',
        resumeSuccess: '테넌트를 재개했습니다',
        resumeFailed: '테넌트 재개 실패',
        updateSuccess: '테넌트 정보를 업데이트했습니다',
        updateFailed: '테넌트 정보 업데이트 실패'
      }
    }
  },

  signOut: {
    title: '로그아웃 중...',
    body: '로컬 세션을 정리하고 ID 공급자(Keycloak)에게 알리는 중입니다.',
    failed: {
      title: '로그아웃 실패',
      goLogin: '로그인 화면으로'
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

  breakGlass: {
    title: '비상용 비밀번호',
    intro: {
      what: 'Keycloak(SSO)을 사용할 수 없을 때 시스템에 로그인하기 위한 비상용 전용 비밀번호입니다.',
      howDifferent: '평소 SSO 로그인에 사용하는 비밀번호(Keycloak 관리)와는 별개이며 동기화되지 않습니다. 같은 비밀번호를 사용하지 마십시오.',
      whenUsed: 'KC 장애 등 SSO 를 사용할 수 없을 때 /login 의 비밀번호 필드로만 사용됩니다. 슈퍼 관리자만 보유합니다.'
    },
    status: {
      configured: '비상용 비밀번호가 설정되어 있습니다',
      notConfigured: '아직 설정되지 않았습니다 — 필요할 때를 대비해 미리 설정하실 것을 강력히 권장합니다'
    },
    label: {
      newPassword: '새 비상용 비밀번호',
      confirmPassword: '확인을 위해 다시 입력'
    },
    placeholder: {
      newPassword: '8자 이상, 문자 종류 혼합',
      confirmPassword: '동일한 비밀번호를 다시 입력'
    },
    hint: {
      storeSafely: '비밀번호 관리자나 조직 금고에 저장해 주세요 — 분실 시 복구 경로가 없습니다.'
    },
    button: {
      save: '저장',
      saving: '저장 중...'
    },
    message: {
      saved: '비상용 비밀번호가 업데이트되었습니다'
    },
    error: {
      tooShort: '비밀번호는 8자 이상이어야 합니다',
      mismatch: '두 비밀번호가 일치하지 않습니다',
      saveFailed: '저장 실패'
    }
  },

  passwordReset: {
    title: '비밀번호 설정',
    tenantPrefix: '테넌트:',
    intro: '시스템이 SSO 에서 비밀번호 로그인으로 전환됩니다. 아래에서 새 비밀번호를 설정해 주세요.',
    passwordLabel: '새 비밀번호',
    passwordPlaceholder: '8자 이상',
    passwordConfirmLabel: '비밀번호 확인',
    passwordConfirmPlaceholder: '비밀번호를 다시 입력하세요',
    button: {
      submit: '비밀번호 설정',
      submitting: '전송 중...',
      goLogin: '로그인 화면으로'
    },
    message: {
      checking: '링크 확인 중...',
      invalidLink: '링크가 잘못되었습니다',
      notValid: '이 링크는 유효하지 않거나 만료되었거나 이미 사용되었습니다',
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
      editAdminContactOnly: '기본 제공 admin: 연락처 정보(이메일·표시명)만 편집 가능',
      edit: '편집',
      resetPassword: '비밀번호 재설정',
      resetPasswordDisabledSso: 'SSO 모드에서는 비활성화됩니다 — 사용자는 Keycloak 계정 콘솔에서 직접 비밀번호를 변경합니다',
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
      lockedHint: '기본 제공 admin 사용자는 「연락처 정보만 편집 가능」합니다. 이메일과 표시명은 수정할 수 있습니다(비상용 비밀번호 알림 수신에 필요). 부서·상태·역할은 잠겨 있습니다.',
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
