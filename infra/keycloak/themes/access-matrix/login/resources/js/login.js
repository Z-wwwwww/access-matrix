/* ============================================================
   Access Matrix login theme — progressive enhancement.

   Loaded by theme.properties (`scripts=js/login.js`). The page is
   fully functional without JS:
     - Keycloak v2 ships its own password show/hide button
     - The realm pill renders as a static badge
     - Form submission is plain POST

   This script layers on the design spec's interactive bits:
     1. Realm pill → click-to-open 280px dropdown menu (signed-in
        tenant card + Switch tenant + Tenant status rows)
     2. Password toggle gets dynamic aria-label (Show/Hide password)
     3. Custom-styled checkbox responds to space/enter when focused
        on its visible proxy span
     4. Staggered fade-up entrance for headings + form rows
        (adds .am-enter classes after first paint so animations
        fire whether the page loaded fresh or via back-button)

   Safe to load on every realm flow — each enhancement checks for
   its anchor element and bails if absent (e.g. update-password
   has no realm pill, login-otp has no Remember-me checkbox).
   ============================================================ */
(function () {
  'use strict';

  // ─── 0a. SPA origin capture ────────────────────────────────────
  // The realm pill's "switch tenant" feature needs to know the URL of
  // the access-matrix front-end so it can land the user there under
  // the new tenant. The KC login page itself only knows KC's own
  // origin, so we infer the SPA origin from document.referrer on the
  // FIRST paint (when the user just arrived from the SPA's SSO redirect).
  //
  // Capture happens once and is stashed to sessionStorage because
  // referrer goes stale on KC's own internal redirects — e.g. after a
  // failed-credentials re-render, document.referrer becomes the
  // previous KC URL instead of the original SPA origin.
  var SPA_ORIGIN_SS_KEY = 'am_spa_origin';
  function captureSpaOrigin() {
    try {
      if (sessionStorage.getItem(SPA_ORIGIN_SS_KEY)) return;
      var ref = document.referrer || '';
      if (!ref) return;
      var u = new URL(ref);
      // Same origin as KC means the referrer is KC itself — useless
      // for finding the SPA. http/https only — no chrome-extension://
      // or similar exotic schemes.
      if (u.origin === window.location.origin) return;
      if (u.protocol !== 'http:' && u.protocol !== 'https:') return;
      sessionStorage.setItem(SPA_ORIGIN_SS_KEY, u.origin);
    } catch (e) { /* sessionStorage blocked / URL malformed — skip */ }
  }

  // ─── 0. i18n ───────────────────────────────────────────────────
  // The dropdown strings are not in any FTL template we control, so
  // we can't pipe them through msg() — keep a tiny dictionary keyed
  // off <html lang>. Falls back to English on any unknown language.
  // If a deployment ever ships more locales, append a row here.
  var I18N = {
    en: {
      signedInTenant: 'Signed-in tenant',
      switchTenant:   'Switch tenant',
      tenantStatus:   'Tenant status',
      operational:    'Operational',
      showPassword:   'Show password',
      hidePassword:   'Hide password',
      language:       'Language',
      signingIn:      'Signing in…',
      capsLockOn:     'Caps Lock is on',
      tenantCode:     'Tenant code',
      tenantInvalid:  'Invalid tenant code',
      tenantNotFound: 'Tenant not found',
      tenantChecking: 'Checking…',
      tenantNoApp:    'Cannot determine app URL — open the app first.'
    },
    'zh-CN': {
      signedInTenant: '当前登录租户',
      switchTenant:   '切换租户',
      tenantStatus:   '租户状态',
      operational:    '运行正常',
      showPassword:   '显示密码',
      hidePassword:   '隐藏密码',
      language:       '语言',
      signingIn:      '登录中…',
      capsLockOn:     '已开启大写锁定',
      tenantCode:     '租户代码',
      tenantInvalid:  '租户代码无效',
      tenantNotFound: '租户不存在',
      tenantChecking: '校验中…',
      tenantNoApp:    '无法确定应用地址，请先打开应用主页'
    },
    ja: {
      signedInTenant: '現在のテナント',
      switchTenant:   'テナントを切り替え',
      tenantStatus:   'テナントのステータス',
      operational:    '正常稼働中',
      showPassword:   'パスワードを表示',
      hidePassword:   'パスワードを非表示',
      language:       '言語',
      signingIn:      'サインイン中…',
      capsLockOn:     'Caps Lock がオンになっています',
      tenantCode:     'テナントコード',
      tenantInvalid:  'テナントコードが無効です',
      tenantNotFound: 'テナントが見つかりません',
      tenantChecking: '確認中…',
      tenantNoApp:    'アプリの URL を特定できません。先にアプリを開いてください'
    }
  };
  function t(key) {
    var lang = (document.documentElement.lang || 'en').toLowerCase();
    // Normalize zh, zh-cn, zh_cn → zh-CN
    if (lang.indexOf('zh') === 0) lang = 'zh-CN';
    else if (lang.indexOf('ja') === 0) lang = 'ja';
    else lang = 'en';
    return (I18N[lang] && I18N[lang][key]) || I18N.en[key];
  }

  // ─── 1. Realm pill dropdown ────────────────────────────────────
  // Stock keycloak.v2 emits the realm name inside
  //   <div id="kc-header-wrapper" class="pf-v5-c-brand">…</div>
  // The CSS already styles this wrapper as a pill (border, status
  // dot, REALM eyebrow). Here we wrap the inert <div> in an
  // accessible <button>, build the dropdown panel as a sibling,
  // and toggle .am-realm-open on the parent for CSS transitions.
  function enhanceRealmPill() {
    var pill = document.getElementById('kc-header-wrapper');
    if (!pill || pill.dataset.amEnhanced) return;
    pill.dataset.amEnhanced = '1';

    // Pill text is whatever Keycloak rendered — `${realm.displayName!
    // realm.name}` in the FTL template. So this is the *human-readable*
    // label (e.g. "Access-matrix · Default"), NOT the realm id slug.
    var pillLabel = (pill.textContent || '').trim();
    if (!pillLabel) return;

    // Realm id slug — pull from the URL path (/realms/<id>/…). Used in
    // the dropdown's "id: …" row and as the switch-tenant placeholder,
    // both of which want the actual realm name, not the display label.
    // Without this, when displayName ≠ realm id the dropdown showed
    // "Display: Access-matrix · Default" stacked on top of "id:
    // Access-matrix · Default" — visually redundant and technically
    // wrong (the user can't type a display name to switch tenants).
    var realmId = (function () {
      var m = window.location.pathname.match(/\/realms\/([^/]+)/);
      return m ? decodeURIComponent(m[1]) : pillLabel.toLowerCase();
    })();

    // Allow the static <div> to receive keyboard focus + announce
    // its role. We keep <div> instead of swapping to <button> so
    // PatternFly's :hover/:focus rules on .pf-v5-c-brand keep
    // working — there's no need for native button semantics now
    // that we wire up the keyboard handlers below.
    pill.setAttribute('role', 'button');
    pill.setAttribute('tabindex', '0');
    pill.setAttribute('aria-haspopup', 'menu');
    pill.setAttribute('aria-expanded', 'false');
    pill.style.cursor = 'pointer';

    // Display: prefer an explicit `data-realm-display` override (a
    // template author can set it for branding), otherwise show the
    // pill label as-is.
    var display = pill.getAttribute('data-realm-display') || pillLabel;

    // Decide whether the technical realm slug deserves its own row
    // in the dropdown. When the slug appears as a WORD in the display
    // label, the row reads as duplicate noise — e.g. display
    // "Access-matrix · Default" + slug "default" doesn't tell the
    // user anything new. In genuinely multi-tenant scenarios the slug
    // earns its place as the tenant's URL / support code.
    //
    // Word-level test (not raw substring) so we don't false-positive
    // on partial overlaps: display "Acme Corporation" + slug
    // "acme-corp" must SHOW because "acmecorp" is merely a prefix of
    // "acmecorporation" — the slug is *not* visible to the user.
    function tokens(s) {
      return String(s).toLowerCase().split(/[^a-z0-9]+/).filter(Boolean);
    }
    var slugAsWord = tokens(realmId).join('');
    var showTenantCode = tokens(display).indexOf(slugAsWord) < 0;

    // Build the dropdown panel. Inline SVGs come straight from
    // the design system's icon set (arrow + globe-style chevron).
    var menu = document.createElement('div');
    menu.className = 'am-realm-menu';
    menu.setAttribute('role', 'menu');
    menu.setAttribute('aria-hidden', 'true');
    // `inert` removes the closed menu from the tab order + click target
    // surface. We don't use CSS `visibility: hidden` for this because the
    // visibility transition would suppress the entrance fade-up (see CSS
    // comment on .am-realm-menu).
    menu.setAttribute('inert', '');
    menu.innerHTML = [
      '<div class="am-realm-menu__head">',
        '<div class="am-realm-menu__eyebrow">' + escapeHtml(t('signedInTenant')) + '</div>',
        '<div class="am-realm-menu__display">' + escapeHtml(display) + '</div>',
        (showTenantCode
          ? '<div class="am-realm-menu__id">' + escapeHtml(t('tenantCode')) + ': ' + escapeHtml(realmId) + '</div>'
          : ''),
      '</div>',
      '<div class="am-realm-menu__divider"></div>',
      // The switch-tenant row is a button (not a static link) — on click
      // it transforms in-place into an inline tenant-name input. There's
      // no global "tenant picker" page in this Keycloak install, so the
      // cheapest useful affordance is: let the user type a realm name,
      // Enter to navigate to that realm's account console.
      '<button type="button" class="am-realm-menu__row am-realm-menu__row--switch" role="menuitem" data-am-switch>',
        '<span>' + escapeHtml(t('switchTenant')) + '</span>',
        '<svg viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="M3 8h10M9 4l4 4-4 4" stroke-linecap="round" stroke-linejoin="round"/></svg>',
      '</button>',
      '<div class="am-realm-menu__row am-realm-menu__row--static" role="menuitem">',
        '<span>' + escapeHtml(t('tenantStatus')) + '</span>',
        '<span class="am-realm-menu__status"><span class="am-realm-menu__dot"></span>' + escapeHtml(t('operational')) + '</span>',
      '</div>'
    ].join('');

    // Position the menu as a sibling of the pill, inside a relative
    // wrapper so absolute positioning anchors to the pill correctly.
    var wrapper = document.createElement('div');
    wrapper.className = 'am-realm-wrap';
    pill.parentNode.insertBefore(wrapper, pill);
    wrapper.appendChild(pill);
    wrapper.appendChild(menu);

    function setOpen(open) {
      wrapper.classList.toggle('am-realm-open', open);
      pill.setAttribute('aria-expanded', open ? 'true' : 'false');
      menu.setAttribute('aria-hidden', open ? 'false' : 'true');
      if (open) menu.removeAttribute('inert');
      else menu.setAttribute('inert', '');
    }

    pill.addEventListener('click', function (e) {
      e.preventDefault();
      setOpen(!wrapper.classList.contains('am-realm-open'));
    });

    pill.addEventListener('keydown', function (e) {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        setOpen(!wrapper.classList.contains('am-realm-open'));
      } else if (e.key === 'Escape') {
        setOpen(false);
      }
    });

    // Click-outside dismiss. mousedown beats click so it fires before
    // a click on the dropdown item might be intercepted by the toggle.
    document.addEventListener('mousedown', function (e) {
      if (!wrapper.contains(e.target)) setOpen(false);
    });

    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') setOpen(false);
    });

    // ─── Switch-tenant inline picker ─────────────────────────────
    // The row starts as a "Switch tenant →" label. On click it swaps
    // its contents for an input + a confirm arrow. Enter (or arrow
    // click) probes the typed realm's OIDC discovery endpoint and,
    // only when it returns 200, navigates the user to the access-matrix
    // SPA under the new tenant — the SPA then re-runs its OIDC flow
    // against the new realm. A 404 / format error shows an inline
    // message under the input and never leaves the page on a known-bad
    // realm.
    //
    // Target URL is derived from the SPA origin captured at first paint
    // (see captureSpaOrigin). Two topologies are handled:
    //   - dev / single-host (localhost): SPA at `localhost:<port>`,
    //     KC at `localhost:8180`. Use `?tenant=<new>` query — the SPA's
    //     tenant.js resolves that with highest priority.
    //   - production / per-subdomain: SPA at `<tenant>.<apex>`. Swap the
    //     first hostname label to the new tenant — the SPA picks tenant
    //     from the subdomain in this mode.
    var switchBtn = menu.querySelector('[data-am-switch]');
    if (switchBtn) {
      switchBtn.addEventListener('click', function (e) {
        e.preventDefault();
        e.stopPropagation();
        if (switchBtn.dataset.amSwitchOpen) return;
        switchBtn.dataset.amSwitchOpen = '1';
        switchBtn.classList.add('am-realm-menu__row--input');
        // Row layout: input + go button on top, message slot (initially
        // empty) wraps to the line below via flex-basis:100% in CSS.
        switchBtn.innerHTML =
          '<input class="am-realm-menu__input" type="text" autocomplete="off" spellcheck="false" ' +
            'placeholder="' + escapeAttr(realmId) + '" aria-label="' + escapeAttr(t('switchTenant')) + '">' +
          '<button type="button" class="am-realm-menu__input-go" aria-label="Go">' +
            '<svg viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="M3 8h10M9 4l4 4-4 4" stroke-linecap="round" stroke-linejoin="round"/></svg>' +
          '</button>' +
          '<div class="am-realm-menu__input-msg" role="status" aria-live="polite"></div>';
        var input = switchBtn.querySelector('input');
        var goBtn = switchBtn.querySelector('button');
        var msg   = switchBtn.querySelector('.am-realm-menu__input-msg');
        input.focus();

        function setMsg(text, kind) {
          msg.textContent = text || '';
          msg.className = 'am-realm-menu__input-msg' +
            (kind ? ' am-realm-menu__input-msg--' + kind : '');
        }
        function clearMsg() { setMsg('', null); }

        var inflight = false;
        function go() {
          if (inflight) return;
          var v = input.value.trim().toLowerCase();
          // RFC 1035 label — matches Keycloak's realm-name constraint
          // (same regex `tenant.js` uses on the frontend side).
          if (!/^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/.test(v)) {
            input.classList.add('am-realm-menu__input--err');
            setTimeout(function () { input.classList.remove('am-realm-menu__input--err'); }, 500);
            setMsg(t('tenantInvalid'), 'err');
            return;
          }
          // Hard block on KC-system realm names regardless of whether the
          // realm exists. Keeps business users from typing `master` and
          // landing in KC's admin surface. The auth-endpoint probe below
          // would also reject these (no access-matrix client), but a local
          // denylist skips the round trip and the error is identical.
          if (BLOCKED_REALMS.has(v)) {
            setMsg(t('tenantNotFound'), 'err');
            input.classList.add('am-realm-menu__input--err');
            setTimeout(function () { input.classList.remove('am-realm-menu__input--err'); }, 500);
            return;
          }
          // SPA URL must be determinable before we probe — both the probe
          // (uses spa origin as redirect_uri) and the eventual navigation
          // need it. Bail with a clear message if we don't have it.
          var spaOrigin;
          try { spaOrigin = sessionStorage.getItem(SPA_ORIGIN_SS_KEY) || ''; } catch (e) { spaOrigin = ''; }
          var target = spaOrigin ? buildSpaLoginUrl(v) : null;
          if (!target || !spaOrigin) {
            setMsg(t('tenantNoApp'), 'err');
            return;
          }

          // Probe the realm's /authorize endpoint with the SPA's client
          // id and redirect_uri. This is stricter than .well-known/
          // openid-configuration: it verifies the realm exists AND has
          // the access-matrix client AND accepts this SPA origin as a
          // valid redirect — the exact preconditions for a successful
          // post-switch login. Any failure (realm not found, client not
          // found, redirect mismatch, network) becomes "tenant not found"
          // because from the user's POV the switch genuinely can't work.
          var probe = window.location.origin + '/realms/' + encodeURIComponent(v) +
                      '/protocol/openid-connect/auth' +
                      '?response_type=code' +
                      '&client_id=' + encodeURIComponent(SPA_CLIENT_ID) +
                      '&redirect_uri=' + encodeURIComponent(spaOrigin + '/sso/callback') +
                      '&scope=openid' +
                      '&state=tenant-switch-probe' +
                      // Dummy but spec-valid PKCE challenge (43 url-safe
                      // chars). KC validates the format, not the value.
                      '&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM' +
                      '&code_challenge_method=S256';
          inflight = true;
          goBtn.disabled = true;
          setMsg(t('tenantChecking'), 'info');
          fetch(probe, { method: 'GET', cache: 'no-store', credentials: 'omit', redirect: 'manual' })
            .then(function (resp) {
              // 200 = KC rendered the login form → realm + client + redirect
              // all OK. opaqueredirect (manual mode on a 302) = same outcome:
              // KC accepted the request enough to redirect us into the auth
              // flow. Anything else (400 / 404 / network) = not switchable.
              if (resp.ok || resp.type === 'opaqueredirect') {
                window.location.href = target;
                return;
              }
              setMsg(t('tenantNotFound'), 'err');
              input.classList.add('am-realm-menu__input--err');
              setTimeout(function () { input.classList.remove('am-realm-menu__input--err'); }, 500);
            })
            .catch(function () {
              setMsg(t('tenantNotFound'), 'err');
            })
            .then(function () {
              inflight = false;
              goBtn.disabled = false;
            });
        }

        // Typing clears any previous error so the user isn't staring at
        // a stale "not found" while editing a new value.
        input.addEventListener('input', clearMsg);
        input.addEventListener('keydown', function (ev) {
          if (ev.key === 'Enter') { ev.preventDefault(); go(); }
        });
        input.addEventListener('click', function (ev) { ev.stopPropagation(); });
        goBtn.addEventListener('click', function (ev) {
          ev.preventDefault();
          ev.stopPropagation();
          go();
        });
      });
    }
  }

  // ─── 2. Password toggle accessibility ──────────────────────────
  // Keycloak v2 emits the eye button as
  //   <button aria-label="Show password" data-password-toggle …>
  // and swaps icons on click via its own bundled JS. The label only
  // updates statically — patch in a live update so screen readers
  // always announce the current action.
  function enhancePasswordToggle() {
    document.querySelectorAll('button[aria-label]').forEach(function (btn) {
      // Heuristic: any button that sits inside a .pf-v5-c-input-group
      // alongside an input[type=password|text] is a password toggle.
      var group = btn.closest('.pf-v5-c-input-group, .pf-c-input-group, .input-group');
      if (!group) return;
      var input = group.querySelector('input[type="password"], input[type="text"]');
      if (!input) return;

      btn.addEventListener('click', function () {
        // Defer so the underlying handler has flipped input.type first.
        setTimeout(function () {
          var hidden = input.type === 'password';
          btn.setAttribute('aria-label', hidden ? t('showPassword') : t('hidePassword'));
        }, 0);
      });
    });
  }

  // ─── 3. Checkbox keyboard support ──────────────────────────────
  // Native <input type=checkbox> already handles space/enter, but
  // when the visible square is a styled <span> sibling (which we use
  // on some flows) keyboard events bounce. This is defensive: it
  // forwards space/enter on a focused visual proxy to the real input.
  function enhanceCheckboxes() {
    document.querySelectorAll('[data-am-checkbox-proxy]').forEach(function (span) {
      span.addEventListener('keydown', function (e) {
        if (e.key === ' ' || e.key === 'Enter') {
          e.preventDefault();
          var target = document.querySelector(span.getAttribute('data-am-checkbox-proxy'));
          if (target) target.click();
        }
      });
    });
  }

  // ─── 4. Staggered entrance animation ───────────────────────────
  // Tag candidate elements with .am-enter / .am-enter-d1…d5 so the
  // CSS keyframe (fadeUp) fires. Doing this in JS instead of static
  // markup keeps the template inheritance from keycloak.v2 intact.
  function applyEntranceClasses() {
    var title = document.querySelector('#kc-page-title, .pf-v5-c-login__main-header h1');
    if (title) title.classList.add('am-enter');

    var subcopy = document.querySelector('#kc-form-wrapper');
    // Sub-copy is rendered via ::before on #kc-form-wrapper; we tag
    // the host element so the pseudo can inherit the animation.
    if (subcopy) subcopy.classList.add('am-enter', 'am-enter-d1');

    // Each form group fades in 70ms after the previous one.
    var groups = document.querySelectorAll('.pf-v5-c-form__group, .pf-c-form__group');
    groups.forEach(function (g, i) {
      g.classList.add('am-enter', 'am-enter-d' + Math.min(i + 2, 5));
    });

    var submit = document.querySelector('#kc-login, input[name="login"]');
    if (submit) submit.classList.add('am-enter', 'am-enter-d5');
  }

  // ─── 4b. Locale picker — replace native select w/ custom pop ──
  // Keycloak 26's keycloak.v2 renders a vanilla <select> as the locale
  // switcher. Native <select> dropdowns are OS-styled (Aqua, Win11
  // Mica…), can't host fade-up animation, and clash with the design
  // language we've built for the realm pop. Strategy: harvest the
  // <option>s as data, hide the native select, render a button trigger
  // + a custom menu mirroring `.am-realm-menu` (same shell, anchored
  // ABOVE the trigger since this lives in the bottom band).
  //
  // Without JS the native select still works (we only display:none it
  // AFTER successfully building the new UI), so this is a true
  // progressive enhancement.
  function enhanceLocalePicker() {
    var sel = document.getElementById('login-select-toggle');
    if (!sel || sel.dataset.amEnhanced) return;

    // Same Keycloak 26 quirk fix: the active option ships as
    // "<X> (<X>)" — strip the doubled clone via backreference regex.
    // See cleanLocaleLabels history (this folded into enhance).
    var dedup = /^(.+) \(\1\)$/;
    var options = [];
    for (var i = 0; i < sel.options.length; i++) {
      var o = sel.options[i];
      var text = o.text.trim();
      var m = text.match(dedup);
      if (m) text = m[1];
      options.push({ value: o.value, text: text, selected: o.selected });
    }
    if (!options.length) return;
    var current = null;
    for (var j = 0; j < options.length; j++) {
      if (options[j].selected) { current = options[j]; break; }
    }
    if (!current) current = options[0];

    var wrapper = sel.parentElement; // .pf-v5-c-form-control
    if (!wrapper) return;

    // Build trigger — visually replaces the native select. Native
    // select kept in DOM (hidden) so form-style fallback still POSTs
    // if our menu's <a href> navigation somehow misfires.
    var trigger = document.createElement('button');
    trigger.type = 'button';
    trigger.className = 'am-locale-trigger';
    trigger.setAttribute('aria-haspopup', 'menu');
    trigger.setAttribute('aria-expanded', 'false');
    trigger.innerHTML =
      '<span class="am-locale-trigger__label">' + escapeHtml(current.text) + '</span>' +
      '<svg class="am-locale-trigger__chevron" viewBox="0 0 16 16" width="10" height="10" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">' +
        '<path d="M4 6l4 4 4-4" stroke-linecap="round" stroke-linejoin="round"/>' +
      '</svg>';

    // Build the pop menu
    var menu = document.createElement('div');
    menu.className = 'am-locale-menu';
    menu.setAttribute('role', 'menu');
    menu.setAttribute('aria-hidden', 'true');
    menu.setAttribute('inert', '');

    var rowsHTML = options.map(function (opt) {
      var indicator = opt.selected
        ? '<svg viewBox="0 0 16 16" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M3 8.5l3.5 3.5L13 5" stroke-linecap="round" stroke-linejoin="round"/></svg>'
        : '';
      var cls = 'am-locale-menu__row' + (opt.selected ? ' am-locale-menu__row--current' : '');
      return '<a class="' + cls + '" role="menuitem" href="' + escapeAttr(opt.value) + '">' +
               '<span>' + escapeHtml(opt.text) + '</span>' +
               indicator +
             '</a>';
    }).join('');

    menu.innerHTML =
      '<div class="am-locale-menu__head">' +
        '<div class="am-locale-menu__eyebrow">' + escapeHtml(t('language')) + '</div>' +
      '</div>' +
      '<div class="am-locale-menu__divider"></div>' +
      rowsHTML;

    // Now atomically swap: hide native select, append trigger + menu
    sel.style.display = 'none';
    wrapper.appendChild(trigger);
    wrapper.appendChild(menu);
    sel.dataset.amEnhanced = '1';

    function setOpen(open) {
      wrapper.classList.toggle('am-locale-open', open);
      trigger.setAttribute('aria-expanded', open ? 'true' : 'false');
      menu.setAttribute('aria-hidden', open ? 'false' : 'true');
      if (open) menu.removeAttribute('inert');
      else menu.setAttribute('inert', '');
    }

    trigger.addEventListener('click', function (e) {
      e.preventDefault();
      setOpen(!wrapper.classList.contains('am-locale-open'));
    });
    trigger.addEventListener('keydown', function (e) {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        setOpen(!wrapper.classList.contains('am-locale-open'));
      } else if (e.key === 'Escape') {
        setOpen(false);
      }
    });
    document.addEventListener('mousedown', function (e) {
      if (!wrapper.contains(e.target)) setOpen(false);
    });
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') setOpen(false);
    });
  }

  function escapeAttr(s) {
    return String(s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  // ─── 4c. Relocate inline field error to the top of the form ────
  // Keycloak v26 keycloak.v2 emits the "Invalid username or password"
  // message as a helper-text node nested inside the USERNAME field's
  // form-group. Because the helper-text container is absolutely
  // positioned to top:0/right:0 (so the "Forgot password?" link can
  // sit on the PASSWORD label row), an error helper lands next to
  // the EMAIL OR USERNAME label — visually disconnected from the
  // failure mode it describes.
  //
  // Solution: physically move the error helper-text node to be a
  // sibling BEFORE the first form-group (the username row), so it
  // sits at the top of the form. This matches the GitHub / Slack /
  // Notion convention for credentials errors: the message is the
  // first thing the user sees on re-render, before they touch the
  // inputs again. It also keeps the message in the aria-live read
  // order that screen readers expect for form-level errors.
  //
  // The CSS class `.am-error-relocated` resets its inherited
  // absolute positioning and renders it as a soft red-tinted pill
  // (see access-matrix.css ".am-error-relocated" block).
  function relocateFieldError() {
    var helpers = document.querySelectorAll(
      '.pf-v5-c-form__group .pf-v5-c-form__helper-text, ' +
      '.pf-c-form__group .pf-c-form__helper-text'
    );
    // Anchor: first form-group in the form (username on the standard
    // login flow; on identity-first single-field flows it's still the
    // first/only input row, which is still the right slot).
    var groups = document.querySelectorAll('.pf-v5-c-form__group, .pf-c-form__group');
    var anchor = groups[0] || null;
    if (!anchor) return;

    helpers.forEach(function (h) {
      // Skip the "Forgot password?" helper (plain link, no error item)
      // and skip already-relocated nodes if init runs twice.
      if (h.classList.contains('am-error-relocated')) return;
      var isError =
        h.querySelector('.pf-m-error') ||
        h.querySelector('[id^="input-error"]');
      if (!isError) return;
      h.classList.add('am-error-relocated');
      // Form-level live region. role=alert + aria-atomic makes screen
      // readers announce the entire pill content on each re-render.
      // Keycloak rebuilds the page on every failed submit so the node
      // is fresh each time — exactly the case role=alert was designed
      // for. We also tag aria-live=polite explicitly because Safari's
      // VoiceOver has been observed to skip the implicit live region
      // on role=alert when the page loads with the node already in
      // the DOM (vs. dynamic insertion after load).
      h.setAttribute('role', 'alert');
      h.setAttribute('aria-live', 'polite');
      h.setAttribute('aria-atomic', 'true');
      anchor.parentNode.insertBefore(h, anchor);
    });
  }

  // ─── 4d. Submit-button loading state ───────────────────────────
  // Login flows POST natively and the browser navigates on response,
  // so the visible loading window is the round-trip duration of the
  // Keycloak auth check — typically 100–500ms but can be longer on
  // remote / cold-start deployments. Without a state change the button
  // sits frozen and users sometimes double-click (which Keycloak then
  // races and one of the submits dies mid-flight). Swap the button
  // contents to spinner + "Signing in…" + disabled the instant the
  // form is submitted; if the navigation succeeds the page reloads
  // anyway, if it doesn't the user sees a clear in-progress state.
  function enhanceSubmitLoading() {
    var form = document.getElementById('kc-form-login');
    var btn = document.getElementById('kc-login') || document.querySelector('input[name="login"]');
    if (!form || !btn) return;

    form.addEventListener('submit', function () {
      // Apply state SYNCHRONOUSLY. Earlier draft used setTimeout(0)
      // out of caution, but on programmatic .submit() (and even on
      // button click) the browser starts navigation as soon as the
      // synchronous handler returns — a queued macrotask never gets
      // to paint. Disabling/swapping in the same tick is safe: form
      // data is captured before the submit event fires, so a disabled
      // button doesn't suppress submission. The result paints once,
      // visible for the duration of the auth round-trip.
      btn.disabled = true;
      btn.classList.add('am-loading');
      var labelText = escapeHtml(t('signingIn'));
      if (btn.tagName === 'BUTTON') {
        btn.innerHTML =
          '<span class="am-spinner" aria-hidden="true"></span>' +
          '<span>' + labelText + '</span>';
      } else {
        btn.value = t('signingIn');
      }
    });
  }

  // ─── 4e. Caps Lock indicator on the password field ─────────────
  // When the credentials submit fails, the #1 silent cause is Caps
  // Lock. Industry-standard remedy (Stripe / GitHub / 1Password) is
  // to surface the modifier state inline when the password input is
  // focused. KeyboardEvent.getModifierState('CapsLock') is supported
  // in every evergreen browser; we listen on keydown AND keyup so the
  // hint also clears the moment the user toggles Caps Lock off, not
  // only on the next keystroke.
  //
  // Limitation: there's no API to read CapsLock state on focus
  // without a key event — focus alone doesn't carry modifier info.
  // So if the user has Caps Lock on BEFORE touching the field, the
  // hint appears as soon as they press their first key. Good enough:
  // the very first password keystroke is what would otherwise produce
  // the wrong value, so this catches the failure at the source.
  function enhanceCapsLockHint() {
    var pwd = document.getElementById('password');
    if (!pwd) return;
    var group = pwd.closest('.pf-v5-c-form__group, .pf-c-form__group');
    if (!group) return;

    var hint = document.createElement('div');
    hint.className = 'am-capslock-hint';
    hint.setAttribute('role', 'status');
    hint.setAttribute('aria-live', 'polite');
    hint.hidden = true;
    hint.innerHTML =
      '<svg class="am-capslock-hint__icon" viewBox="0 0 16 16" width="12" height="12" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true">' +
        '<path d="M8 2.5L3 7.5h2.5v3.5h5V7.5H13L8 2.5z" stroke-linejoin="round"/>' +
        '<path d="M5 13h6" stroke-linecap="round"/>' +
      '</svg>' +
      '<span>' + escapeHtml(t('capsLockOn')) + '</span>';
    group.appendChild(hint);

    function update(e) {
      var on = e && e.getModifierState ? e.getModifierState('CapsLock') : false;
      hint.hidden = !on;
    }
    pwd.addEventListener('keydown', update);
    pwd.addEventListener('keyup', update);
    pwd.addEventListener('blur', function () { hint.hidden = true; });
  }

  // ─── 5. Form shake on error ────────────────────────────────────
  // When Keycloak server-side validation rejects the submission it
  // re-renders the page with a .pf-v5-c-alert.pf-m-danger banner.
  // Detect that on load and shake the form wrapper once.
  function shakeOnError() {
    var hasError =
      document.querySelector('.pf-v5-c-alert.pf-m-danger, .pf-c-alert.pf-m-danger, .alert-error, #input-error, #input-error-username, #input-error-password');
    if (!hasError) return;
    var form = document.querySelector('#kc-form-login, #kc-form, .pf-v5-c-login__main');
    if (!form) return;
    form.classList.add('am-shake');
    // Strip the class after the animation ends so it can re-fire
    // if a future flow re-triggers the same error in-place.
    setTimeout(function () { form.classList.remove('am-shake'); }, 500);
  }

  // ─── Helpers ────────────────────────────────────────────────────

  // Reserved subdomain labels — must stay in sync with the SPA's
  // tenant.js RESERVED_SUBDOMAINS. If the SPA origin's first hostname
  // label is one of these, we treat the deploy as single-host (not
  // per-tenant subdomain) and fall back to the ?tenant= query string.
  var SPA_RESERVED_SUBS = ['www','app','admin','api','static','cdn','auth','kc','sso','docs'];
  var RFC1035_LABEL = /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/;

  // Realms that are NEVER valid business tenants — KC's own admin
  // surface plus the same reserved labels we block as subdomains.
  // Catches the obvious "user typed `master` and got dumped into KC's
  // admin realm" footgun without a network round-trip. Anything not
  // listed here still has to pass the auth-endpoint probe below.
  var BLOCKED_REALMS = new Set(['master','admin','www','app','api','static','cdn','auth','kc','sso','docs']);

  // Client id of the access-matrix SPA, registered in every business
  // tenant's realm. Hardcoded because this file IS the access-matrix
  // theme — it ships alongside the realm JSONs in infra/keycloak/ and
  // the two are versioned together. The probe in go() uses this id to
  // distinguish "real business tenant" from "some other realm that
  // happens to exist in KC" (e.g. master, KC's admin realm).
  var SPA_CLIENT_ID = 'access-matrix-backend';

  function buildSpaLoginUrl(newTenant) {
    var origin;
    try { origin = sessionStorage.getItem(SPA_ORIGIN_SS_KEY) || ''; } catch (e) { origin = ''; }
    if (!origin) return null;
    var u;
    try { u = new URL(origin); } catch (e) { return null; }
    var host = u.hostname;
    // Per-subdomain routing: hostname has 3+ labels (so first label is
    // genuinely a subdomain, not the apex) and the first label is a
    // valid RFC 1035 word that isn't infrastructure. Swap it.
    var isIpv4    = /^\d{1,3}(?:\.\d{1,3}){3}$/.test(host);
    var isIpv6    = host.indexOf(':') >= 0 || host.charAt(0) === '[';
    var isLocal   = host === 'localhost';
    if (!isIpv4 && !isIpv6 && !isLocal) {
      var parts = host.split('.');
      if (parts.length >= 3) {
        var first = parts[0].toLowerCase();
        if (SPA_RESERVED_SUBS.indexOf(first) < 0 && RFC1035_LABEL.test(first)) {
          parts[0] = newTenant;
          var port = u.port ? ':' + u.port : '';
          return u.protocol + '//' + parts.join('.') + port + '/login';
        }
      }
    }
    // Single-host / dev fallback: ?tenant=<new> on the SPA's /login.
    return u.origin + '/login?tenant=' + encodeURIComponent(newTenant);
  }

  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  // ─── Boot ───────────────────────────────────────────────────────
  function init() {
    // Snapshot referrer BEFORE anything else can navigate or alter it.
    captureSpaOrigin();
    enhanceRealmPill();
    enhancePasswordToggle();
    enhanceCheckboxes();
    enhanceLocalePicker();
    // Relocate BEFORE entrance classes so the moved node picks up the
    // staggered fade-up alongside the form rows it now sits among.
    relocateFieldError();
    enhanceCapsLockHint();
    enhanceSubmitLoading();
    applyEntranceClasses();
    shakeOnError();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
