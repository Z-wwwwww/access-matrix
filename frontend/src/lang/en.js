/**
 * English
 *
 * Mirror of ja_JP.js — see that file for the key-tree documentation.
 */
import general from './general/en'
import permissions from './generated/permissions.en.json'

export default {
  general,

  common: {
    button: {
      search: 'Search',
      reset: 'Reset',
      save: 'Save',
      cancel: 'Cancel',
      confirm: 'Confirm',
      delete: 'Delete',
      edit: 'Edit',
      new: 'New',
      detail: 'Detail',
      apply: 'Apply',
      clear: 'Clear',
      selectAll: 'Select all',
      back: 'Back',
      close: 'Close',
      refresh: 'Refresh',
      export: 'Export',
      import: 'Import',
      upload: 'Upload',
      download: 'Download',
      submit: 'Submit'
    },
    status: {
      active: 'Active',
      inactive: 'Inactive',
      enabled: 'Enabled',
      disabled: 'Disabled',
      builtIn: 'Built-in'
    },
    message: {
      saveSuccessful: 'Saved successfully',
      deleteSuccessful: 'Deleted successfully',
      loading: 'Loading',
      processing: 'Processing...',
      sending: 'Sending...',
      loginSuccessful: 'Logged in',
      networkError: 'Network error',
      sessionExpired: 'Session expired',
      operationSuccessful: 'Operation successful',
      iframeLoadFailed: 'Failed to load the external page'
    },
    label: {
      keyword: 'Keyword'
    },
    placeholder: {
      keyword: 'Keyword',
      pleaseInput: 'Please enter',
      pleaseSelect: 'Please select',
      search: 'Search...',
      deptId: 'Please select a department'
    },
    tooltip: {
      pagePrevious5: '5 pages back',
      pageNext5: '5 pages forward'
    },
    datePicker: {
      today: 'Today',
      now: 'Now',
      year: 'Year',
      yearMonth: 'Year-Month',
      placeholder: 'Select date',
      startPlaceholder: 'Start date',
      endPlaceholder: 'End date',
      dateTimePlaceholder: 'Select date and time',
      timePlaceholder: 'Select time',
      confirm: 'OK'
    }
  },

  layout: {
    header: {
      profile: 'Profile',
      password: 'Change password',
      logout: 'Log out',
      userFallback: 'User'
    },
    sidebar: {
      adminGroup: 'Admin settings',
      favorites: 'Favorites',
      favorite: 'Add to favorites',
      unfavorite: 'Remove from favorites'
    },
    tabs: {
      tabAction: 'Tab actions',
      closeCurrent: 'Close this tab',
      closeOthers: 'Close other tabs',
      closeAll: 'Close all tabs'
    },
    footer: {
      copyright: 'Copyright © 2026 SOZONEXT Co.,Ltd.'
    }
  },

  login: {
    identifierLabel: 'Username / Email / Number',
    identifierPlaceholder: 'Username · Email · User number',
    passwordLabel: 'Password',
    passwordPlaceholder: 'Enter your password',
    submit: 'Log in',
    submitting: 'Logging in...',
    forgotPassword: 'Forgot password?',
    message: {
      enterUsername: 'Please enter your username',
      enterPassword: 'Please enter your password',
      loginFailed: 'Login failed'
    }
  },

  forget: {
    title: 'Reset password',
    mobileLabel: 'Mobile number',
    mobilePlaceholder: 'Enter your registered mobile number',
    newPasswordLabel: 'New password',
    newPasswordPlaceholder: 'Enter a new login password',
    confirmPasswordLabel: 'Confirm password',
    confirmPasswordPlaceholder: 'Re-enter the password',
    captchaLabel: 'Verification code',
    captchaPlaceholder: 'SMS verification code',
    sendCaptcha: 'Send code',
    sentCountdown: 'Sent ({n}s)',
    backToLogin: 'Back to login',
    submit: 'Change password',
    submitting: 'Processing...',
    imgCaptchaTitle: 'Send verification code',
    imgCaptchaPlaceholder: 'Enter the image code',
    refresh: 'Refresh',
    loading: 'Loading...',
    sendNow: 'Send now',
    sending: 'Sending...',
    message: {
      enterMobile: 'Please enter your mobile number',
      enterImgCode: 'Please enter the image code',
      smsSent: 'SMS code sent',
      passwordReset: 'Password changed',
      enterConfirmPassword: 'Please confirm the password',
      passwordMismatch: 'Passwords do not match'
    }
  },

  password: {
    oldPassword: 'Current password',
    password: 'New password',
    confirmPassword: 'Confirm password',
    message: {
      inconsistent: 'Passwords do not match'
    }
  },

  notFound: {
    title: '404',
    message: 'Page not found',
    backHome: 'Back to home'
  },

  router: {
    title: {
      login: 'Log in',
      forget: 'Reset password',
      notFound: '404',
      profile: 'Profile'
    }
  },

  profile: {
    title: 'Profile',
    label: {
      deptId: 'Department',
      userId: 'User ID',
      username: 'Login ID',
      displayName: 'Username',
      email: 'Email',
      userNo: 'User No.',
      tenantId: 'Tenant',
      roles: 'Roles',
      authorities: 'Authorities'
    }
  },

  user: {
    search: {
      placeholder: { keyword: 'Login ID / Email / Username' },
      label: { deptId: 'Department' }
    },
    column: {
      username: 'Login ID',
      displayName: 'Username',
      userNo: 'No.',
      email: 'Email',
      deptId: 'Department',
      status: 'Status',
      actions: 'Actions'
    },
    tooltip: {
      editDisabled: 'Built-in user is not editable',
      edit: 'Edit',
      resetPassword: 'Reset password',
      statusChangeDisabled: 'Built-in user status cannot be changed',
      toggleStatus: 'Enable / Disable',
      forceLogout: 'Force logout',
      deleteDisabled: 'Built-in user cannot be deleted'
    },
    confirm: {
      deleteTitle: 'Delete user',
      deleteMessage: 'Delete "{name}"?',
      forceLogoutTitle: 'Force logout',
      forceLogoutMessage: 'Force logout "{name}"?\n(Any in-flight access token will be invalidated on the next API call.)'
    },
    message: {
      deleteFailed: 'Delete failed',
      forceLogoutSuccess: 'User force-logged out'
    },
    edit: {
      titleEdit: 'Edit user',
      titleCreate: 'New user',
      lockedHint: 'The built-in admin user is read-only. Only password reset is allowed (via the "Reset password" API).',
      label: {
        username: 'Login ID',
        password: 'Password',
        displayName: 'Username',
        email: 'Email',
        userNo: 'No.',
        deptId: 'Department',
        status: 'Status',
        roles: 'Roles',
        rolesSelected: '{selected} of {total} selected'
      },
      placeholder: {
        password: '8+ chars / 4 character types'
      },
      message: {
        noRoles: 'No roles',
        saving: 'Saving...',
        updateFailed: 'Update failed',
        createFailed: 'Create failed',
        assignRolesFailed: 'Role assignment failed'
      }
    },
    resetPassword: {
      title: 'Reset password',
      label: {
        user: 'User',
        newPassword: 'New password',
        confirmPassword: 'Confirm password'
      },
      placeholder: {
        value: '8+ chars / 4 character types',
        confirm: 'Re-enter the same password'
      },
      hint: '※ Passwords on the public breach corpus (HIBP) will be rejected.',
      button: { reset: 'Reset' },
      error: {
        tooShort: 'Password must be at least 8 characters',
        mismatch: 'Confirmation password does not match'
      },
      message: {
        saving: 'Saving...',
        success: 'Password reset',
        failed: 'Failed'
      }
    }
  },

  role: {
    search: {
      placeholder: { keyword: 'Code / Name' }
    },
    column: {
      code: 'Code',
      name: 'Name',
      dataScope: 'Data scope',
      status: 'Status',
      actions: 'Actions'
    },
    option: {
      scope: {
        all: 'All',
        deptAndSub: 'Department + sub',
        dept: 'Department',
        self: 'Self only',
        custom: 'Custom'
      }
    },
    tooltip: {
      viewOnly: 'Built-in role is view-only (click Edit to inspect)',
      edit: 'Edit',
      deleteDisabled: 'Built-in role cannot be deleted'
    },
    confirm: {
      deleteTitle: 'Delete role',
      deleteMessage: 'Delete "{code}"?'
    },
    message: {
      deleteBuiltInFailed: 'Built-in role cannot be deleted',
      deleteFailed: 'Delete failed'
    },
    edit: {
      titleEdit: 'Edit role',
      titleCreate: 'New role',
      lockedHint: 'The built-in role is read-only. Changing code, data scope, or permission/menu/department assignments can cause auth loops or permission drift.',
      tab: {
        basic: 'Basic',
        permissions: 'Permissions',
        menus: 'Menus',
        depts: 'Departments'
      },
      label: {
        code: 'Code',
        name: 'Name',
        description: 'Description',
        dataScope: 'Data scope',
        status: 'Status'
      },
      option: {
        scope: {
          all: 'All (ALL)',
          deptAndSub: 'Department + sub (DEPT_AND_SUB)',
          deptOnly: 'Department only (DEPT)',
          self: 'Self only (SELF)',
          custom: 'Custom (CUSTOM)'
        }
      },
      message: {
        noPermissions: 'No permissions',
        noMenus: 'No menus',
        noDepts: 'No departments',
        saving: 'Saving...',
        updateFailed: 'Update failed',
        createFailed: 'Create failed'
      },
      tooltip: {
        locked: 'Built-in role is not editable'
      }
    }
  },

  dept: {
    title: 'Departments',
    button: { addRoot: 'Add root' },
    column: {
      name: 'Name',
      code: 'Code',
      level: 'Level',
      leader: 'Leader',
      status: 'Status',
      actions: 'Actions'
    },
    message: {
      noDepts: 'No departments',
      loadUsersFailed: 'Failed to load users',
      userDeleted: '(deleted)',
      deleteFailed: 'Delete failed'
    },
    tooltip: {
      addChild: 'Add child',
      edit: 'Edit'
    },
    confirm: {
      deleteTitle: 'Delete department',
      deleteMessage: 'Delete "{name}"?\n(Rejected if it has sub-departments or members.)'
    },
    edit: {
      titleEdit: 'Edit department',
      titleCreate: 'New department',
      label: {
        parentId: 'Parent department',
        code: 'Code',
        name: 'Name',
        sortOrder: 'Sort order',
        status: 'Status',
        leader: 'Leader'
      },
      placeholder: {
        parentId: 'Empty for root',
        code: 'HQ / TOKYO',
        name: 'Head office',
        leader: 'Unspecified'
      },
      hint: {
        rootParent: 'Leave empty to make it a root department',
        leaderInfo: 'Display memo only. Does not affect permissions or data scope.'
      },
      message: {
        updateFailed: 'Update failed',
        createFailed: 'Create failed'
      }
    }
  },

  menu: {
    title: 'Menus',
    button: { addRoot: 'Add root' },
    column: {
      title: 'Name / Path',
      type: 'Type',
      component: 'Component',
      permission: 'Permission',
      hide: 'Hidden',
      actions: 'Actions'
    },
    message: {
      noMenus: 'No menus',
      fetchFailed: 'Failed to load menus'
    },
    option: {
      type: {
        directory: 'Directory',
        menu: 'Menu',
        button: 'Button'
      },
      yesNo: { no: 'No', yes: 'Yes' }
    },
    tooltip: {
      addChild: 'Add child',
      edit: 'Edit'
    },
    confirm: {
      deleteTitle: 'Delete menu',
      deleteMessage: 'Delete "{code}"?'
    },
    edit: {
      titleEdit: 'Edit menu',
      titleCreate: 'New menu',
      label: {
        code: 'Code',
        name: 'Name',
        titleI18n: 'Title (per language)',
        type: 'Type',
        sortOrder: 'Sort order',
        parentId: 'Parent menu',
        path: 'Path',
        component: 'Component',
        icon: 'Icon',
        permissionCode: 'Permission code',
        hide: 'Hidden',
        hideSidebar: 'Hide from sidebar',
        hideFooter: 'Hide from footer',
        pinned: 'Pin to top'
      },
      tip: {
        hide: 'Hide this entry from the sidebar menu tree (route still accessible — useful for detail pages and dynamic routes)',
        hideSidebar: 'Hide the left sidebar when this page is open (for print previews, full-screen wizards, etc.)',
        hideFooter: 'Hide the bottom footer (copyright bar) when this page is open',
        pinned: 'Pin this menu to the very top of the sidebar, separated from the rest by a divider',
        pinnedDisabled: 'Only items of type "Menu" can be pinned. Directories and buttons cannot be pinned'
      },
      placeholder: {
        code: 'system.user',
        parentId: 'Empty for root',
        path: '/system/user',
        component: '/system/User/User',
        permissionCode: 'user:read',
        titleI18nPrimary: 'Required (default locale)',
        titleI18nOptional: 'Optional'
      },
      error: {
        titleJaRequired: 'Japanese title is required (default locale)'
      }
    }
  },

  // Permission code → display name (auto-patched by backend I18nPermissionPatcher in dev)
  permission: permissions,

  dataTable: {
    emptyState: 'No data',
    loading: 'Loading...',
    pagination: {
      total: 'Total {n} items',
      perPage: '{n} / page'
    }
  },

  picker: {
    icon: {
      selectPlaceholder: 'Pick an icon',
      searchPlaceholder: 'Search icons...',
      noResults: 'No matching icons'
    }
  },

  task: {
    title: 'Tasks (data-scope demo)',
    description: 'Visible tasks vary by role. Details:',
    search: {
      label: { keyword: 'Keyword', status: 'Status' },
      placeholder: { keyword: 'Search by title' }
    },
    column: {
      title: 'Title',
      deptId: 'Department',
      status: 'Status',
      priority: 'Priority',
      assignee: 'Assignee',
      creator: 'Creator',
      dueDate: 'Due date',
      actions: 'Actions'
    },
    emptyState: 'No matching data',
    status: { todo: 'To do', doing: 'In progress', done: 'Done', cancelled: 'Cancelled' },
    priority: { low: 'Low', medium: 'Medium', high: 'High' },
    confirm: {
      deleteTitle: 'Delete task',
      deleteMessage: 'Delete "{title}"?'
    },
    message: {
      loadFailed: 'Load failed',
      saveFailed: 'Save failed',
      saveSuccess: 'Saved',
      deleteFailed: 'Delete failed',
      deleteSuccess: 'Deleted'
    },
    edit: {
      titleEdit: 'Edit task',
      titleCreate: 'New task',
      label: { content: 'Content' },
      placeholder: {
        title: 'Task title',
        deptSelect: 'Select a department',
        optional: 'Optional',
        unassigned: 'Unassigned',
        dueDate: 'Pick a due date'
      }
    },
    option: { statusAll: 'All' }
  },

  oplog: {
    search: {
      label: {
        module: 'Module',
        action: 'Action',
        user: 'User',
        targetType: 'Target type',
        targetId: 'Target ID',
        result: 'Result'
      },
      placeholder: {
        module: 'e.g. system / pms / iot',
        action: 'e.g. role.create',
        user: 'All users',
        targetType: 'e.g. role / user'
      }
    },
    option: {
      result: { all: 'All', success: 'Success', failure: 'Failure' }
    },
    column: {
      createTime: 'Time',
      username: 'User',
      module: 'Module',
      action: 'Action',
      targetType: 'Target',
      clientIp: 'IP',
      success: 'Result',
      costMs: 'ms',
      actions: 'Detail'
    },
    status: { success: 'Success', failure: 'Failure' },
    detail: {
      title: 'Operation log',
      label: {
        createTime: 'Time',
        costMs: 'Cost',
        username: 'User',
        userId: 'User ID',
        module: 'Module',
        action: 'Action',
        targetType: 'Target type',
        targetId: 'Target ID',
        method: 'Method',
        result: 'Result',
        uri: 'URI',
        clientIp: 'Client IP',
        userAgent: 'User-Agent'
      },
      section: {
        errorMsg: 'Error message',
        requestBody: 'Request body (passwords auto-masked)'
      },
      message: { empty: '(none)' },
      button: { close: 'Close' }
    },
    message: {
      fetchFailed: 'Fetch failed'
    }
  }
}
