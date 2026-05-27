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
      language:       'Language'
    },
    'zh-CN': {
      signedInTenant: '当前登录租户',
      switchTenant:   '切换租户',
      tenantStatus:   '租户状态',
      operational:    '运行正常',
      showPassword:   '显示密码',
      hidePassword:   '隐藏密码',
      language:       '语言'
    },
    ja: {
      signedInTenant: '現在のテナント',
      switchTenant:   'テナントを切り替え',
      tenantStatus:   'テナントのステータス',
      operational:    '正常稼働中',
      showPassword:   'パスワードを表示',
      hidePassword:   'パスワードを非表示',
      language:       '言語'
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

    var realmName = (pill.textContent || '').trim();
    if (!realmName) return;

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

    // Display name: use the realm display attr if Keycloak set it,
    // otherwise fall back to the realm id (== the pill text).
    // `data-realm-display` is opt-in — set via realm attribute or
    // a template override. Without it we just show the id twice
    // (which still reads correctly: header + dropdown both name
    // the same tenant).
    var display = pill.getAttribute('data-realm-display') || realmName;

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
        '<div class="am-realm-menu__id">id: ' + escapeHtml(realmName) + '</div>',
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
    // click) navigates to `<origin>/realms/<typed>/account/`, which is
    // the entry point for that realm's authenticated flow (Keycloak
    // redirects to its login screen on its own).
    //
    // Works for both deployment topologies:
    //   - dev / single-host: all realms live under the same Keycloak
    //     host (`localhost:8180/realms/...`), so the path swap is the
    //     correct way to switch
    //   - production / per-subdomain: each tenant sits at a different
    //     subdomain but Keycloak itself is typically still mounted at
    //     `<host>/realms/<name>/` regardless of subdomain. The path
    //     swap navigates to the new realm under the same Keycloak host,
    //     which is the intended target.
    var switchBtn = menu.querySelector('[data-am-switch]');
    if (switchBtn) {
      switchBtn.addEventListener('click', function (e) {
        e.preventDefault();
        e.stopPropagation();
        if (switchBtn.dataset.amSwitchOpen) return;
        switchBtn.dataset.amSwitchOpen = '1';
        switchBtn.classList.add('am-realm-menu__row--input');
        switchBtn.innerHTML =
          '<input class="am-realm-menu__input" type="text" autocomplete="off" spellcheck="false" ' +
            'placeholder="' + escapeAttr(realmName) + '" aria-label="' + escapeAttr(t('switchTenant')) + '">' +
          '<button type="button" class="am-realm-menu__input-go" aria-label="Go">' +
            '<svg viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="M3 8h10M9 4l4 4-4 4" stroke-linecap="round" stroke-linejoin="round"/></svg>' +
          '</button>';
        var input = switchBtn.querySelector('input');
        var goBtn = switchBtn.querySelector('button');
        input.focus();

        function go() {
          var v = input.value.trim().toLowerCase();
          // RFC 1035 label — matches Keycloak's realm-name constraint
          // (same regex `tenant.js` uses on the frontend side).
          if (!/^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/.test(v)) {
            input.classList.add('am-realm-menu__input--err');
            setTimeout(function () { input.classList.remove('am-realm-menu__input--err'); }, 500);
            return;
          }
          var url = new URL(window.location.href);
          var segs = url.pathname.split('/');
          var ri = segs.indexOf('realms');
          if (ri >= 0 && segs[ri + 1]) {
            segs[ri + 1] = v;
            url.pathname = segs.slice(0, ri + 2).join('/') + '/account/';
            url.search = '';
            url.hash = '';
            window.location.href = url.toString();
          }
        }

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
  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  // ─── Boot ───────────────────────────────────────────────────────
  function init() {
    enhanceRealmPill();
    enhancePasswordToggle();
    enhanceCheckboxes();
    enhanceLocalePicker();
    applyEntranceClasses();
    shakeOnError();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
