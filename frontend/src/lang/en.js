/**
 * English
 *
 * Mirror of ja_JP.js — see that file for the key-tree documentation.
 */
import permissions from './generated/permissions.en.json'

export default {
  common: {
    confirm: {
      forceTitle: 'Force delete',
      forceMessage: '{detail}\n\nForce delete will cascade-clean all dependent references. Continue?'
    },
    button: {
      forceDelete: 'Force delete',
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
      breakGlass: 'Break-glass password',
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
    tenantLabel: 'Tenant',
    tenantPlaceholder: 'demo',
    showAdvanced: 'Advanced (switch tenant)',
    hideAdvanced: 'Hide',
    identifierLabel: 'Username / Email / Number',
    identifierPlaceholder: 'Username · Email · User number',
    passwordLabel: 'Password',
    passwordPlaceholder: 'Enter your password',
    submit: 'Log in',
    submitting: 'Logging in...',
    forgotPassword: 'Forgot password?',
    ssoDivider: 'or',
    ssoButton: 'Sign in with SSO',
    ssoOnlyHint: 'Sign in with single sign-on (SSO).',
    ssoRedirecting: 'Redirecting to SSO...',
    ssoUnreachable: {
      title: 'Cannot reach the SSO server',
      body: 'Keycloak (SSO) is not responding. It may be under maintenance, restarting, or a network issue is blocking access. You can use a break-glass password to continue, or retry shortly.',
      useBreakGlass: 'Continue with break-glass password',
      retry: 'Retry SSO',
      retrying: 'Checking...'
    },
    tenantRecovered: "Tenant '{stale}' is no longer available — switched back to the default tenant.",
    passwordBreakGlass: 'Password login mode (break-glass)',
    backToSso: 'Back to SSO',
    passwordModeHotzone: 'Click 5 times within 2 seconds to unlock password login',
    message: {
      enterUsername: 'Please enter your username',
      enterPassword: 'Please enter your password',
      loginFailed: 'Login failed',
      ssoFailed: 'SSO sign-in failed'
    }
  },

  platform: {
    tenant: {
      column: {
        tenantCode: 'Tenant code',
        displayName: 'Display name',
        contactEmail: 'Contact email',
        status: 'Status',
        createTime: 'Created',
        actions: 'Actions'
      },
      status: { active: 'Active', suspended: 'Suspended' },
      search: { placeholder: 'Search by code or name' },
      recycleBinHint: {
        title: 'Deletion is a recycle-bin flow:',
        body: 'Suspend first (the KC realm is disabled and sign-in is blocked, but data is kept). To truly delete, use the red trash icon that appears on a suspended row — after confirming by typing the tenant code, the business data, KC realm and registry row are all permanently removed. This cannot be undone.'
      },
      edit: {
        titleCreate: 'New tenant',
        titleEdit: 'Edit tenant',
        intro: 'Creates the Keycloak realm + central registry row in one action. The tenant code cannot be changed after creation.',
        editIntro: 'The tenant code cannot be changed. Only the display name and contact email can be updated.',
        label: {
          tenantCode: 'Tenant code',
          displayName: 'Display name',
          contactEmail: 'Contact email',
          adminUsername: 'Admin username'
        },
        placeholder: {
          tenantCode: 'acme',
          displayName: 'Acme Corp.',
          contactEmail: 'admin@acme.example',
          adminUsername: 'admin'
        },
        hint: {
          tenantCode: 'Lowercase RFC1035 label (a-z, 0-9, hyphen). Used as the Keycloak realm name and the subdomain.',
          contactEmail: 'Optional — used to invite the first admin',
          adminUsername: 'Auto-generated from the local part of the contact email if left blank. Can be changed later.'
        },
        error: {
          invalidCode: 'Tenant code must be a lowercase RFC1035 label (a-z, 0-9, hyphen)',
          missingDisplayName: 'Display name is required',
          invalidAdminUsername: 'Admin username must start with a lowercase letter and contain only lowercase letters, digits, hyphens and underscores'
        },
        saving: 'Saving...'
      },
      button: {
        new: 'New tenant',
        edit: 'Edit',
        suspend: 'Suspend',
        resume: 'Resume'
      },
      tooltip: {
        suspend: 'Suspend tenant (disables the Keycloak realm, reversible)',
        resume: 'Resume a suspended tenant',
        edit: 'Edit tenant details',
        builtInLocked: 'Built-in tenants (system / demo) cannot be changed'
      },
      hardDelete: {
        title: 'Permanently delete tenant',
        tooltip: {
          confirm: 'Permanently delete tenant (business data, KC realm and registry row all physically removed)'
        },
        warning: {
          title: 'This cannot be undone',
          intro: 'You are about to permanently delete "{displayName}" ({tenantCode}). All of the following will be erased forever:',
          dropBusiness: 'All business table rows tied to this tenant (users, roles, departments, tasks, etc.)',
          dropRealm: 'The Keycloak realm itself (all users / sessions / client configuration)',
          dropRegistry: 'The central registry row (core_tenant)',
          noUndo: 'No recovery is possible — only a manual restore from backup.'
        },
        label: {
          typeCode: 'To confirm, type the tenant code "{tenantCode}" exactly'
        },
        error: {
          mismatch: 'Tenant code does not match'
        },
        button: {
          confirm: 'Permanently delete',
          deleting: 'Deleting...'
        },
        message: {
          success: 'Tenant "{tenantCode}" permanently deleted',
          failed: 'Permanent tenant deletion failed'
        }
      },
      confirm: {
        suspendTitle: 'Suspend tenant',
        suspendMessage: 'Suspend "{displayName}" ({tenantCode})?\n\n• The Keycloak realm will be disabled and sign-in blocked\n• You can restore it anytime with the "Resume" button',
        suspendConfirm: 'Suspend',
        resumeTitle: 'Resume tenant',
        resumeMessage: 'Resume "{displayName}" ({tenantCode})?\n\nThe Keycloak realm will be re-enabled and accept sign-in again.',
        resumeConfirm: 'Resume'
      },
      support: {
        tooltip: {
          start: 'Start a support session (operate with this tenant\'s SUPER_ADMIN rights for 30 minutes)',
          disabledSuspended: 'Cannot start a support session for a suspended tenant'
        },
        dialog: {
          title: 'Start support session',
          warning: {
            title: 'High-privilege action',
            body: 'You will operate as SUPER_ADMIN of {displayName} ({tenantCode}) for 30 minutes.\nEvery action during this session is recorded in the audit log as "[support] <your username>".'
          },
          reasonLabel: 'Reason (required)',
          reasonPlaceholder: 'e.g. OS-1234 reproduce the user-reported issue',
          reasonHint: 'Saved to the audit log (core_oplog.request_body). Please be specific.',
          ttlNote: 'The session expires automatically after 30 minutes (no extension)',
          auditNote: 'Every action is recorded in the audit log',
          writeNote: 'Read-only mode is not implemented (writes are possible) — proceed carefully',
          starting: 'Starting...',
          confirm: 'Start support session'
        },
        banner: {
          acting: 'Support session active: {displayName} ({tenantCode})',
          note: 'Every action is recorded in the audit log'
        },
        button: {
          terminate: 'End session'
        },
        message: {
          started: 'Support session started ({tenantCode})',
          startFailed: 'Failed to start support session',
          terminated: 'Support session ended'
        }
      },
      resendInvite: {
        tooltip: {
          resend: 'Resend the admin onboarding invite (missed email or wrong address)'
        },
        dialog: {
          title: 'Resend admin invite',
          body: 'Resend the onboarding invite for {displayName} ({tenantCode})\'s administrator.',
          emailLabel: 'Send to',
          emailPlaceholder: 'admin@example.com',
          emailHint: 'Prefilled with the current contact email. Edit it to correct a wrong address (updates the user, Keycloak and the tenant contact); leave it unchanged to just resend.',
          tokenNote: 'A fresh invite link is generated; any previous link stops working.',
          activatedNote: 'Only works while the admin has not activated yet.',
          sending: 'Sending...',
          confirm: 'Resend invite'
        },
        message: {
          success: 'Invitation re-sent',
          failed: 'Failed to resend invitation'
        }
      },
      message: {
        createSuccess: 'Tenant created and invitation email sent',
        createFailed: 'Tenant creation failed',
        loadFailed: 'Failed to load tenants',
        suspendSuccess: 'Tenant suspended',
        suspendFailed: 'Tenant suspend failed',
        resumeSuccess: 'Tenant resumed',
        resumeFailed: 'Tenant resume failed',
        updateSuccess: 'Tenant details updated',
        updateFailed: 'Tenant update failed'
      }
    }
  },

  signOut: {
    title: 'Signing out...',
    body: 'Clearing your local session and notifying the identity provider (Keycloak).',
    failed: {
      title: 'Sign-out failed',
      goLogin: 'Go to login'
    }
  },

  invite: {
    title: 'Set up your account',
    tenantPrefix: 'Tenant:',
    passwordLabel: 'Password',
    passwordPlaceholder: 'At least 8 characters',
    passwordConfirmLabel: 'Confirm password',
    passwordConfirmPlaceholder: 'Re-enter your password',
    button: {
      submit: 'Set password',
      submitting: 'Submitting...',
      goLogin: 'Go to login'
    },
    message: {
      checking: 'Validating invite...',
      invalidLink: 'Invite link is malformed',
      notValid: 'This invite is invalid, expired, or already used',
      passwordTooShort: 'Password must be at least 8 characters',
      passwordMismatch: 'Passwords do not match',
      acceptFailed: 'Failed to set password',
      done: 'Password set. You can now sign in.'
    }
  },

  breakGlass: {
    title: 'Break-glass password',
    intro: {
      what: 'This is an emergency-only password used to log in to the system when Keycloak (SSO) is unavailable.',
      howDifferent: 'It is SEPARATE from your daily SSO password (which lives in Keycloak) and the two are NOT synced. Do not reuse the same value.',
      whenUsed: 'Use it via the legacy /login password field only when SSO is unreachable. Only super-admin users have one.'
    },
    status: {
      configured: 'A break-glass password is currently configured.',
      notConfigured: 'Not yet configured — strongly recommended to set one before you need it.'
    },
    label: {
      newPassword: 'New break-glass password',
      confirmPassword: 'Confirm new password'
    },
    placeholder: {
      newPassword: 'At least 8 chars, mixed character types',
      confirmPassword: 'Re-enter the same password'
    },
    hint: {
      storeSafely: 'Save it in your password manager or organisational vault — there is no recovery path if you forget it.'
    },
    button: {
      save: 'Save',
      saving: 'Saving...'
    },
    message: {
      saved: 'Break-glass password updated'
    },
    error: {
      tooShort: 'Password must be at least 8 characters',
      mismatch: 'Passwords do not match',
      saveFailed: 'Save failed'
    }
  },

  passwordReset: {
    title: 'Set your password',
    tenantPrefix: 'Tenant:',
    intro: 'We are switching from SSO to password login. Please set a new password below.',
    passwordLabel: 'New password',
    passwordPlaceholder: 'At least 8 characters',
    passwordConfirmLabel: 'Confirm password',
    passwordConfirmPlaceholder: 'Re-enter your password',
    button: {
      submit: 'Set password',
      submitting: 'Submitting...',
      goLogin: 'Go to login'
    },
    message: {
      checking: 'Validating link...',
      invalidLink: 'Reset link is malformed',
      notValid: 'This link is invalid, expired, or already used',
      passwordTooShort: 'Password must be at least 8 characters',
      passwordMismatch: 'Passwords do not match',
      acceptFailed: 'Failed to set password',
      done: 'Password set. You can now sign in.'
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
    openConsoleHint: 'Password changes are managed by the identity provider (Keycloak). Open the account console to update it.',
    openConsoleButton: 'Open Account Console',
    consoleUnavailable: 'Password change is unavailable when SSO is disabled.',
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
      editAdminContactOnly: 'Built-in admin: only contact details (email, display name) editable',
      edit: 'Edit',
      resetPassword: 'Reset password',
      resetPasswordDisabledSso: 'Disabled in SSO mode — users change their password in the Keycloak account console',
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
      lockedHint: 'Built-in admin user: only contact info is editable. Email and display name can be changed (needed for break-glass alerts). Department, status, and roles stay locked.',
      label: {
        username: 'Login ID',
        password: 'Password',
        displayName: 'Username',
        email: 'Email',
        userNo: 'No.',
        deptId: 'Department',
        status: 'Status',
        roles: 'Roles',
        rolesSelected: '{selected} of {total} selected',
        mode: 'Provision mode'
      },
      mode: {
        invite: {
          title: 'Invite',
          hint: 'User sets their own password via email link',
          willEmail: 'An invite email will be sent to {email}'
        },
        direct: {
          title: 'Direct',
          hint: 'Admin sets the initial password'
        }
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
      placeholder: { keyword: 'Name / description' }
    },
    column: {
      name: 'Name',
      description: 'Description',
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
      deleteMessage: 'Delete "{name}"?',
      inUseMessage: 'This role is assigned to {users} user(s).\n\nForce delete will clear all user bindings and cascade-clean the role\'s permission / menu / department references. Continue?'
    },
    message: {
      deleteBuiltInFailed: 'Built-in role cannot be deleted',
      deleteFailed: 'Delete failed'
    },
    edit: {
      titleEdit: 'Edit role',
      titleCreate: 'New role',
      lockedHint: 'The built-in role is read-only. Changing name, data scope, or permission/menu/department assignments can cause auth loops or permission drift.',
      tab: {
        permissions: 'Permissions',
        menus: 'Menus',
        depts: 'Departments'
      },
      label: {
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
      dept: {
        impliedTag: '(included)',
        impliedTooltip: 'Automatically included via a selected parent department. To remove, uncheck the parent.'
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
      deleteMessage: 'Delete "{name}"?',
      inUseMessage: 'This department has {children} sub-department(s), {users} member(s), and {roles} role(s) referencing it (in their SCOPE_CUSTOM data range).\n\nForce delete will soft-delete this department and the entire subtree, clear affected users\'\' department assignment, and remove the department from the referencing roles\'\' custom data scope. Continue?'
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
