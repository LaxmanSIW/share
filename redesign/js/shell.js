/* ==========================================================================
   AURORA REDESIGN · Shell + interactions
   Builds the sidebar & topbar on every app page, wires the command palette,
   modals, popovers, tabs, chips, toasts, F1 overlay and the chatbot.
   Prototype: actions that would hit a backend surface as toasts instead.
   ========================================================================== */
(function () {
  'use strict';
  if (document.body.classList.contains('auth')) return; // auth pages have no shell

  const PAGE = document.body.getAttribute('data-page') || '';

  /* ---------- navigation model (mirrors StudioApp.show* API) ---------- */
  const NAV = [
    { label: 'Workspace', items: [
      { id: 'dashboard', t: 'Dashboard', href: 'dashboard.html', ic: 'dashboard' },
      { id: 'dashboard2', t: 'Financial & Logistics', href: 'dashboard2.html', ic: 'trendup' },
    ]},
    { label: 'Sales', items: [
      { id: 'create-bill', t: 'Create Bill', href: 'create-bill.html', ic: 'pluscircle', hide: true },
      { id: 'invoices', t: 'Invoices', href: 'invoices.html', ic: 'invoices', badge: '14' },
      { id: 'transactions', t: 'Transactions', href: 'transactions.html', ic: 'swap', badge: '8' },
      { id: 'reports', t: 'Reports & Ledger', href: 'reports.html', ic: 'reports' },
    ]},
    { label: 'Purchase & Expenses', items: [
      { id: 'purchases', t: 'Purchases', href: 'purchases.html', ic: 'purchases', badge: '5' },
      { id: 'expenses', t: 'Expenses', href: 'expenses.html', ic: 'expenses' },
    ]},
    { label: 'Insights', items: [
      { id: 'financials', t: 'Financials', href: 'financials.html', ic: 'financials' },
      { id: 'stock', t: 'Stock & Profit', href: 'stock.html', ic: 'stock' },
    ]},
    { label: 'Design & Print', items: [
      { id: 'templates', t: 'Templates', href: 'templates.html', ic: 'templates' },
      { id: 'designer', t: 'Template Designer', href: 'designer.html', ic: 'pen' },
      { id: 'label-history', t: 'Label History', href: 'label-history.html', ic: 'labels' },
    ]},
    { label: 'Directory & Catalog', items: [
      { id: 'buyers', t: 'Buyers', href: 'buyers.html', ic: 'users' },
      { id: 'suppliers', t: 'Suppliers', href: 'suppliers.html', ic: 'truck' },
      { id: 'items', t: 'Items', href: 'items.html', ic: 'wallet' },
      { id: 'categories', t: 'Categories', href: 'categories.html', ic: 'folder' },
      { id: 'transports', t: 'Transports', href: 'transports.html', ic: 'truck' },
      { id: 'variables', t: 'Variables', href: 'variables.html', ic: 'code' },
    ]},
    { label: 'System', items: [
      { id: 'settings', t: 'Settings', href: 'settings.html', ic: 'settings' },
    ]},
  ];

  /* pages that "light" another nav item (mirrors updateNavActive mapping) */
  const ALIAS = { 'create-purchase': 'purchases', 'designer': 'designer' };

  /* ---------- sidebar ---------- */
  const collapsedKey = 'aurora.nav.collapsed';
  function buildSidebar() {
    const sb = document.createElement('aside');
    sb.className = 'sidebar';
    let h = '<a class="brand" href="dashboard.html" title="Dashboard">' +
      '<span class="brand-mark">IS</span>' +
      '<span><span class="brand-name">InvoiceStudio</span><br><span class="brand-sub">Bill design & print</span></span></a><nav class="nav">';
    NAV.forEach((g, gi) => {
      h += '<div class="nav-group" data-g="' + gi + '">' +
        '<button class="nav-label" data-group-toggle><span class="txt">' + g.label + '</span><span class="chev">' + icon('chevdown', 'sm') + '</span></button>' +
        '<div class="nav-items">';
      g.items.filter(i => !i.hide).forEach(i => {
        h += '<a class="nav-item" data-nav="' + i.id + '" href="' + i.href + '">' + icon(i.ic, 'sm') +
          '<span class="txt">' + i.t + '</span>' +
          (i.badge ? '<span class="badge">' + i.badge + '</span>' : '') + '</a>';
      });
      h += '</div></div>';
    });
    h += '</nav><div class="side-foot">' +
      '<a class="btn accent block" href="create-bill.html">' + icon('plus', 'sm') + '<span class="txt">New Bill</span></a>' +
      '<div id="userPillSlot"></div>' +
      '</div>';
    sb.innerHTML = h;
    document.querySelector('.app').prepend(sb);

    // collapse groups
    const saved = JSON.parse(localStorage.getItem(collapsedKey) || '[]');
    saved.forEach(gi => { const el = sb.querySelector('[data-g="' + gi + '"]'); if (el) el.classList.add('collapsed'); });
    sb.querySelectorAll('[data-group-toggle]').forEach(btn => {
      btn.addEventListener('click', () => {
        const grp = btn.closest('.nav-group');
        grp.classList.toggle('collapsed');
        const open = [...sb.querySelectorAll('.nav-group')].map(g => g.classList.contains('collapsed') ? null : g.getAttribute('data-g')).filter(Boolean);
        localStorage.setItem(collapsedKey, JSON.stringify(open));
      });
    });

    // active highlighting
    const key = ALIAS[PAGE] || PAGE;
    sb.querySelectorAll('[data-nav]').forEach(a => {
      if (a.getAttribute('data-nav') === key) a.classList.add('active');
    });
    ['catalog'].forEach(() => {});
  }

  /* ---------- user pill + account menu ---------- */
  function buildUserPill() {
    const slot = document.getElementById('userPillSlot');
    if (!slot) return;
    slot.innerHTML =
      '<button class="user-pill block" id="userPillBtn" style="width:100%">' +
      '<span class="avatar">LM</span>' +
      '<span style="text-align:left;min-width:0" class="txt"><span class="u-name" style="display:block">Leelamani Traders</span><span class="u-mail">shree@leelamani.in</span></span></button>';
    document.getElementById('userPillBtn').addEventListener('click', e => {
      openPop(e.currentTarget, [
        { head: 'Signed in as Leelamani Traders' },
        { ic: 'user', t: 'Profile & business details', act: "goto:settings.html" },
        { ic: 'shield', t: 'Firebase account', act: 'toast:Session active · Firebase Identity Toolkit' },
        { sep: true },
        { ic: 'settings', t: 'Settings', act: 'goto:settings.html' },
        { ic: 'logout', t: 'Sign out…', danger: true, act: 'modal:modal-logout' },
      ]);
    });
  }

  /* ---------- topbar ---------- */
  function buildTopbar() {
    const tb = document.createElement('header');
    tb.className = 'topbar';
    const groupOf = (() => {
      for (const g of NAV) for (const i of g.items) if (i.id === PAGE) return g.label;
      return 'Workspace';
    })();
    tb.innerHTML =
      '<button class="icon-btn" id="railToggle" title="Collapse / expand navigation">' + icon('menu') + '</button>' +
      '<nav class="crumb" aria-label="Breadcrumb"><span>' + groupOf + '</span>' + icon('chevright', 'sm') + '<b>' + document.title.split('|')[0].trim() + '</b></nav>' +
      '<button class="search-pill" id="searchPill">' + icon('search', 'sm') + '<span>Search bills, buyers, items…</span><span class="kbd">Ctrl K</span></button>' +
      '<div class="top-actions">' +
      '<button class="icon-btn" id="refreshBtn" title="Reload data (F5)">' + icon('refresh') + '</button>' +
      '<button class="icon-btn" id="keysBtn" title="Keyboard shortcuts (F1)">' + icon('help') + '</button>' +
      '</div>';
    document.querySelector('.main').prepend(tb);

    document.getElementById('railToggle').addEventListener('click', () => {
      document.querySelector('.app').classList.toggle('rail');
    });
    document.getElementById('searchPill').addEventListener('click', openPalette);
    document.getElementById('keysBtn').addEventListener('click', () => openModal('modal-keys'));
    document.getElementById('refreshBtn').addEventListener('click', () => {
      const pill = document.createElement('div');
      pill.className = 'refresh-pill';
      pill.innerHTML = icon('refresh', 'sm') + ' Refreshing…';
      document.body.appendChild(pill);
      setTimeout(() => { pill.remove(); toast('Up to date', 'Background re-warm complete — data epoch unchanged.', 'ok'); }, 1100);
    });
  }

  /* ---------- toasts ---------- */
  let toastZone;
  function ensureToastZone() {
    if (!toastZone) { toastZone = document.createElement('div'); toastZone.className = 'toast-zone'; document.body.appendChild(toastZone); }
    return toastZone;
  }
  window.toast = function (title, msg, kind) {
    const zone = ensureToastZone();
    const el = document.createElement('div');
    el.className = 'toast ' + (kind || '');
    const ic = kind === 'ok' ? 'check' : kind === 'err' ? 'alert' : kind === 'warn' ? 'alert' : 'info';
    el.innerHTML = '<span class="tic">' + icon(ic, 'sm') + '</span><div><b>' + title + '</b>' + (msg ? '<p>' + msg + '</p>' : '') + '</div>' +
      '<button class="icon-btn tx" style="width:26px;height:26px">' + icon('x', 'sm') + '</button>';
    el.querySelector('.tx').onclick = () => el.remove();
    zone.appendChild(el);
    setTimeout(() => { el.classList.add('out'); setTimeout(() => el.remove(), 260); }, 3800);
  };

  /* ---------- modals ---------- */
  window.openModal = function (id) {
    const m = document.getElementById(id);
    if (!m) return;
    m.classList.add('open');
  };
  window.closeModal = function (id) {
    const m = id ? document.getElementById(id) : document.querySelector('.modal-back.open');
    if (m) m.classList.remove('open');
  };

  /* ---------- popovers ---------- */
  let activePop = null, activePopBack = null;
  function ensurePopBack() {
    if (!activePopBack) {
      activePopBack = document.createElement('div');
      activePopBack.className = 'pop-back';
      document.body.appendChild(activePopBack);
      activePopBack.addEventListener('click', closePop);
    }
    return activePopBack;
  }
  function closePop() {
    if (activePop) { activePop.classList.remove('open'); activePop = null; }
    if (activePopBack) activePopBack.classList.remove('open');
  }
  window.openPop = function (anchor, items, opts) {
    const back = ensurePopBack();
    closePop();
    const pop = document.createElement('div');
    pop.className = 'pop' + (opts && opts.mega ? ' mega' : '');
    items.forEach(it => {
      if (it.head) { pop.innerHTML += '<div class="mhead">' + it.head + '</div>'; return; }
      if (it.sep) { pop.innerHTML += '<div class="msep"></div>'; return; }
      const b = document.createElement('button');
      b.className = 'mi' + (it.danger ? ' danger' : '');
      b.innerHTML = (it.ic ? icon(it.ic, 'sm') : '') + '<span>' + it.t + '</span>' + (it.kbd ? '<span class="kbd">' + it.kbd + '</span>' : '');
      b.addEventListener('click', () => { closePop(); runAct(it.act); });
      pop.appendChild(b);
    });
    document.body.appendChild(pop);
    const r = anchor.getBoundingClientRect();
    pop.style.top = (r.bottom + 6) + 'px';
    pop.style.left = Math.min(r.left, window.innerWidth - pop.offsetWidth - 12) + 'px';
    if (r.left + pop.offsetWidth > window.innerWidth) pop.style.left = (window.innerWidth - pop.offsetWidth - 12) + 'px';
    pop.classList.add('open');
    back.classList.add('open');
    activePop = pop;
  };

  function runAct(act) {
    if (!act) return;
    if (act.startsWith('toast:')) { const [t, m, k] = act.slice(6).split('|'); toast(t, m || '', k || ''); }
    else if (act.startsWith('goto:')) location.href = act.slice(5);
    else if (act.startsWith('modal:')) openModal(act.slice(6));
    else if (act.startsWith('pop:')) { const [id] = [act.slice(4)]; openModal(id); }
    else if (act.startsWith('fn:')) { try { (window[act.slice(3)] || function () {})(); } catch (e) {} }
  }
  window.runAct = runAct;

  /* ---------- generic delegation ---------- */
  document.addEventListener('click', e => {
    const mo = e.target.closest('[data-modal-open]');
    if (mo) { e.preventDefault(); openModal(mo.getAttribute('data-modal-open')); return; }
    const mc = e.target.closest('[data-close]');
    if (mc) { const bk = mc.closest('.modal-back, .keys-back, .palette-back'); if (bk) bk.classList.remove('open'); return; }
    const ts = e.target.closest('[data-toast]');
    if (ts) { const v = ts.getAttribute('data-toast').split('|'); toast(v[0], v[1] || '', v[2] || ''); return; }
    const gt = e.target.closest('[data-goto]');
    if (gt && !e.metaKey && !e.ctrlKey) { location.href = gt.getAttribute('data-goto'); return; }
    const pm = e.target.closest('[data-popmenu]');
    if (pm) {
      e.preventDefault();
      const spec = window.POPMENUS && window.POPMENUS[pm.getAttribute('data-popmenu')];
      if (spec) openPop(pm, spec);
      return;
    }
    const chip = e.target.closest('.chip:not(.static), .seg button');
    if (chip) {
      const group = chip.closest('.seg');
      if (group) group.querySelectorAll('button').forEach(b => b.classList.remove('on'));
      if (chip.classList.contains('chip')) { if (chip.hasAttribute('data-single')) { chip.parentElement.querySelectorAll('.chip').forEach(c => c.classList.remove('on')); } }
      chip.classList.toggle('on');
      if (chip.hasAttribute('data-on-click')) runAct(chip.getAttribute('data-on-click'));
      return;
    }
  });

  /* tabs: [data-tab] within [data-tabs] */
  document.addEventListener('click', e => {
    const t = e.target.closest('[data-tab]');
    if (!t) return;
    const wrap = t.closest('[data-tabs]');
    wrap.querySelectorAll('[data-tab]').forEach(x => x.classList.remove('on'));
    t.classList.add('on');
    const name = t.getAttribute('data-tab');
    const panes = document.querySelectorAll(wrap.getAttribute('data-tabs-for') || '[data-pane]');
    panes.forEach(p => p.style.display = p.getAttribute('data-pane') === name ? '' : 'none');
    if (wrap.hasAttribute('data-persist')) localStorage.setItem('aurora.tab.' + wrap.getAttribute('data-tabs'), name);
    window.dispatchEvent(new CustomEvent('aurora:tab', { detail: { wrap, name } }));
  });
  // restore persisted tab
  document.querySelectorAll('[data-tabs][data-persist]').forEach(wrap => {
    const saved = localStorage.getItem('aurora.tab.' + wrap.getAttribute('data-tabs'));
    if (saved) { const t = wrap.querySelector('[data-tab="' + saved + '"]'); if (t) t.click(); }
  });

  /* keyboard: Ctrl+K palette · F1 keys overlay · Esc closes */
  document.addEventListener('keydown', e => {
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); togglePalette(); }
    else if (e.key === 'F1') { e.preventDefault(); const k = document.getElementById('modal-keys'); if (k) k.classList.toggle('open'); }
    else if (e.key === 'Escape') { closeModal(); closePop(); const p = document.getElementById('paletteBack'); if (p) p.classList.remove('open'); }
  });

  /* ---------- command palette ---------- */
  const PALETTE_ITEMS = [
    { g: 'Workspace', t: 'Dashboard', ic: 'dashboard', href: 'dashboard.html', k: 'G D' },
    { g: 'Workspace', t: 'Financial & Logistics (Dashboard 2)', ic: 'trendup', href: 'dashboard2.html' },
    { g: 'Sales', t: 'Create Bill — new document', ic: 'pluscircle', href: 'create-bill.html', k: 'Ctrl N' },
    { g: 'Sales', t: 'Invoices & history', ic: 'invoices', href: 'invoices.html', k: 'Ctrl H' },
    { g: 'Sales', t: 'Transactions & ledger', ic: 'swap', href: 'transactions.html' },
    { g: 'Sales', t: 'Reports & financial ledger', ic: 'reports', href: 'reports.html', k: 'Ctrl R' },
    { g: 'Purchase', t: 'Purchase register', ic: 'purchases', href: 'purchases.html' },
    { g: 'Purchase', t: 'Record purchase bill', ic: 'pluscircle', href: 'create-purchase.html' },
    { g: 'Purchase', t: 'Expense register', ic: 'expenses', href: 'expenses.html' },
    { g: 'Insights', t: 'Financial statements', ic: 'financials', href: 'financials.html' },
    { g: 'Insights', t: 'Stock & profitability', ic: 'stock', href: 'stock.html' },
    { g: 'Design & Print', t: 'Templates gallery', ic: 'templates', href: 'templates.html', k: 'Ctrl T' },
    { g: 'Design & Print', t: 'Template designer', ic: 'pen', href: 'designer.html', k: 'Ctrl D' },
    { g: 'Design & Print', t: 'Label print history', ic: 'labels', href: 'label-history.html' },
    { g: 'Directory', t: 'Buyers & customers', ic: 'users', href: 'buyers.html', k: 'Ctrl B' },
    { g: 'Directory', t: 'Suppliers & sellers', ic: 'truck', href: 'suppliers.html' },
    { g: 'Directory', t: 'Item catalog', ic: 'wallet', href: 'items.html', k: 'Ctrl I' },
    { g: 'Directory', t: 'Categories', ic: 'folder', href: 'categories.html' },
    { g: 'Directory', t: 'Transport agencies', ic: 'truck', href: 'transports.html' },
    { g: 'Directory', t: 'Variables & placeholders', ic: 'code', href: 'variables.html' },
    { g: 'System', t: 'Settings', ic: 'settings', href: 'settings.html', k: 'Ctrl ,' },
    { g: 'System', t: 'Keyboard shortcuts', ic: 'help', act: 'modal:modal-keys', k: 'F1' },
    { g: 'System', t: 'AI assistant', ic: 'chat', act: 'fn:toggleChat' },
    { g: 'System', t: 'Export invoices to CSV', ic: 'download', act: 'toast:Export queued|invoices-2026-09.csv will download shortly.' },
  ];

  function togglePalette(force) {
    let back = document.getElementById('paletteBack');
    if (!back) {
      back = document.createElement('div');
      back.className = 'palette-back'; back.id = 'paletteBack';
      back.innerHTML = '<div class="palette"><input id="plInput" placeholder="Type a page or action…" autocomplete="off">' +
        '<div class="pl-list" id="plList"></div>' +
        '<div class="pl-foot"><span>↑↓ navigate</span><span>↵ open</span><span>esc close</span><span style="margin-left:auto">' + PALETTE_ITEMS.length + ' actions</span></div></div>';
      document.body.appendChild(back);
      back.addEventListener('click', e => { if (e.target === back) back.classList.remove('open'); });
      const input = back.querySelector('#plInput');
      input.addEventListener('input', () => renderPalette(input.value));
      input.addEventListener('keydown', e => {
        const items = [...back.querySelectorAll('.pl-item')];
        let idx = items.findIndex(i => i.classList.contains('sel'));
        if (e.key === 'ArrowDown') { e.preventDefault(); idx = Math.min(idx + 1, items.length - 1); }
        else if (e.key === 'ArrowUp') { e.preventDefault(); idx = Math.max(idx - 1, 0); }
        else if (e.key === 'Enter') { e.preventDefault(); if (items[idx]) items[idx].click(); return; }
        else return;
        items.forEach(i => i.classList.remove('sel'));
        if (items[idx]) { items[idx].classList.add('sel'); items[idx].scrollIntoView({ block: 'nearest' }); }
      });
    }
    const open = force !== undefined ? force : !back.classList.contains('open');
    back.classList.toggle('open', open);
    if (open) { const i = back.querySelector('#plInput'); i.value = ''; renderPalette(''); setTimeout(() => i.focus(), 30); }
  }
  function renderPalette(q) {
    const list = document.getElementById('plList');
    if (!list) return;
    q = (q || '').toLowerCase();
    const items = PALETTE_ITEMS.filter(i => !q || i.t.toLowerCase().includes(q) || i.g.toLowerCase().includes(q));
    let html = '', lastG = '';
    items.forEach((i, n) => {
      if (i.g !== lastG) { html += '<div class="pl-group">' + i.g + '</div>'; lastG = i.g; }
      html += '<a class="pl-item' + (n === 0 ? ' sel' : '') + '" href="' + (i.href || '#') + '" data-act="' + (i.act || '') + '">' + icon(i.ic, 'sm') + '<span>' + i.t + '</span>' + (i.k ? '<span class="kbd">' + i.k + '</span>' : '') + '</a>';
    });
    if (!items.length) html = '<div class="empty"><span class="eic">' + icon('search') + '</span><b>Nothing matches "' + q + '"</b><span>Try a page name or an action like "export".</span></div>';
    list.innerHTML = html;
    list.querySelectorAll('.pl-item').forEach(el => {
      el.addEventListener('click', ev => {
        const act = el.getAttribute('data-act');
        if (act) { ev.preventDefault(); togglePalette(false); runAct(act); }
      });
    });
  }
  window.openPalette = () => togglePalette(true);

  /* ---------- F1 keys overlay content ---------- */
  const KEYS = [
    ['Workspace', [['Dashboard', 'G then D'], ['Dashboard 2', 'G then 2'], ['New bill', 'Ctrl+N'], ['Reload data', 'F5'], ['Command palette', 'Ctrl+K'], ['Shortcuts help', 'F1']]],
    ['Directory', [['Buyers', 'Ctrl+B'], ['Items', 'Ctrl+I'], ['Suppliers', 'Ctrl+U'], ['Variables', 'Ctrl+V'], ['Templates gallery', 'Ctrl+T'], ['Label designer', 'Ctrl+Shift+L']]],
    ['Design & Print', [['Bulk label print', 'Ctrl+Shift+B'], ['Save designer', 'Ctrl+S'], ['Undo / Redo', 'Ctrl+Z / Ctrl+Y'], ['Zoom canvas', 'Ctrl+wheel'], ['Fit canvas', 'Ctrl+0'], ['Designer groups', 'Ctrl+G']]],
    ['Billing', [['Save bill', 'Ctrl+S'], ['Save & print', 'Ctrl+P'], ['Save & PDF', 'Ctrl+E'], ['Mark paid', 'Ctrl+M'], ['Duplicate bill', 'Ctrl+Shift+D'], ['Convert to invoice', 'Ctrl+Shift+C']]],
    ['Data', [['Export CSV (view)', 'Ctrl+Shift+E'], ['Backup JSON', 'Ctrl+Shift+K'], ['Sign out', 'Ctrl+Q']]],
  ];
  function buildKeys() {
    if (document.getElementById('modal-keys')) return;
    const back = document.createElement('div');
    back.className = 'keys-back'; back.id = 'modal-keys';
    let cols = '';
    KEYS.forEach(([g, rows]) => {
      cols += '<div class="keys-group"><h4>' + g + '</h4>';
      rows.forEach(([t, k]) => { cols += '<div class="keys-row"><span>' + t + '</span><span class="kbd">' + k + '</span></div>'; });
      cols += '</div>';
    });
    back.innerHTML = '<div class="keys-sheet"><div class="row" style="margin-bottom:6px"><h3 style="margin:0;font-size:18px;font-weight:800">Keyboard shortcuts</h3><span class="spacer"></span><button class="icon-btn" data-close>' + icon('x') + '</button></div>' +
      '<p class="muted" style="margin:0 0 8px;font-size:13px">Rebindable combos are managed in Settings → Shortcuts. Contextual (designer & print) keys are fixed.</p>' +
      '<div class="keys-cols">' + cols + '</div></div>';
    document.body.appendChild(back);
    back.addEventListener('click', e => { if (e.target === back) back.classList.remove('open'); });
  }

  /* ---------- logout modal ---------- */
  function buildLogout() {
    if (document.getElementById('modal-logout')) return;
    const back = document.createElement('div');
    back.className = 'modal-back'; back.id = 'modal-logout';
    back.innerHTML = '<div class="modal w-sm"><div class="modal-head"><span class="mic danger">' + icon('logout') + '</span><h3>Sign out of InvoiceStudio?</h3></div>' +
      '<div class="modal-body"><p style="margin:0 0 10px">Your session will be cleared on this device. Cached views and unsaved form state are discarded.</p>' +
      '<label class="checkbox"><input type="checkbox" checked><span class="box">' + icon('check', 'sm') + '</span>Remember me on this device</label></div>' +
      '<div class="modal-foot"><span class="spacer"></span><button class="btn ghost" data-close>Cancel</button>' +
      '<a class="btn danger solid" href="index.html">Sign out</a></div></div>';
    document.body.appendChild(back);
    back.addEventListener('click', e => { if (e.target === back) back.classList.remove('open'); });
  }

  /* ---------- chatbot ---------- */
  function buildChatbot() {
    if (document.getElementById('chatFab')) return;
    const fab = document.createElement('button');
    fab.className = 'chat-fab'; fab.id = 'chatFab'; fab.title = 'AI assistant';
    fab.innerHTML = icon('chat', 'lg');
    document.body.appendChild(fab);

    const panel = document.createElement('div');
    panel.className = 'chat-panel'; panel.id = 'chatPanel';
    panel.innerHTML =
      '<div class="chat-head"><span class="dot-on"></span><div style="flex:1;min-width:0"><b>AI Assistant</b><div class="model">glm-4.6 · free-tier · 32k ctx</div></div>' +
      '<button class="icon-btn" title="Background execution logs" data-modal-open="modal-chatlog">' + icon('files', 'sm') + '</button>' +
      '<button class="icon-btn" title="Clear conversation" id="chatNew">' + icon('refresh', 'sm') + '</button>' +
      '<button class="icon-btn" title="Close chat" id="chatClose">' + icon('x', 'sm') + '</button></div>' +
      '<div class="pipeline"><span class="pp on">ROUTER</span><span class="pp" id="ppTool">TOOL-CALL</span><span class="pp" id="ppMcp">MCP-EXEC</span><span class="pp" id="ppTok">TOKENS 2,148 / 32k</span></div>' +
      '<div class="chat-body" id="chatBody">' +
      '<div class="msg bot">Namaste! I can query your books, draft bills and design labels. Ask me anything — or try a suggestion below.<span class="m-time">09:41 · routed locally</span></div>' +
      '<div class="msg user">Top 5 buyers by outstanding<attachment></attachment><span class="m-time">09:42 · 0.8s</span></div>' +
      '<div class="msg tool"><b>TOOL</b> ledger.outstanding(limit=5) → McpToolRegistry.call() · 214ms</div>' +
      '<div class="msg bot"><b>Top 5 buyers by outstanding due</b><br>1. Acme Industries — ₹1,84,220<br>2. Gamma Constructions — ₹96,410<br>3. Beta Engineers — ₹58,075<br>4. Krishna Textiles — ₹41,900<br>5. Sunrise Traders — ₹18,250<br><br>Total receivable: <b>₹3,98,855</b> across 14 open invoices.<span class="m-time">09:42 · glm-4.6 · 412 tok</span></div>' +
      '</div>' +
      '<div class="chat-sugg">' +
      '<button class="chip" data-chat="Which items are low in stock?">Low-stock items</button>' +
      '<button class="chip" data-chat="Draft an invoice for Acme Industries">Draft invoice</button>' +
      '<button class="chip" data-chat="Show GST summary for this month">GST summary</button>' +
      '<button class="chip" data-chat="What changed in my purchases?">Purchase changes</button></div>' +
      '<div class="chat-input"><button class="icon-btn" title="Attach image">' + icon('attach') + '</button>' +
      '<input id="chatText" placeholder="Ask about bills, stock, buyers…">' +
      '<button class="chat-send" id="chatSend" title="Send (Enter)">' + icon('send', 'sm') + '</button></div>';
    document.body.appendChild(panel);

    window.toggleChat = function () {
      const open = panel.classList.toggle('open');
      fab.style.display = open ? 'none' : 'grid';
    };
    fab.addEventListener('click', toggleChat);
    panel.querySelector('#chatClose').addEventListener('click', toggleChat);
    panel.querySelector('#chatNew').addEventListener('click', () => {
      const b = panel.querySelector('#chatBody');
      b.innerHTML = '<div class="msg bot">Started a fresh thread — earlier context is cleared.<span class="m-time">' + new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) + '</span></div>';
      toast('New thread', 'Earlier context cleared.', 'ok');
    });
    panel.querySelectorAll('[data-chat]').forEach(c => c.addEventListener('click', () => { panel.querySelector('#chatText').value = c.getAttribute('data-chat'); sendChat(); }));
    panel.querySelector('#chatSend').addEventListener('click', sendChat);
    panel.querySelector('#chatText').addEventListener('keydown', e => { if (e.key === 'Enter') sendChat(); });

    function sendChat() {
      const inp = panel.querySelector('#chatText');
      const body = panel.querySelector('#chatBody');
      const v = inp.value.trim();
      if (!v) return;
      inp.value = '';
      const t = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
      body.insertAdjacentHTML('beforeend', '<div class="msg user">' + v + '<span class="m-time">' + t + '</span></div>');
      body.insertAdjacentHTML('beforeend', '<div class="msg tool"><b>TOOL</b> router → local match · query planning…</div>');
      body.scrollTop = body.scrollHeight;
      const ppT = panel.querySelector('#ppTool'), ppM = panel.querySelector('#ppMcp');
      ppT.classList.add('on'); ppM.classList.add('on');
      setTimeout(() => {
        body.insertAdjacentHTML('beforeend', '<div class="msg bot">This is a prototype response — the assistant pipeline (router → tool rounds → MCP execution → renderer) is wired exactly like the production shell, but no model is attached here.<span class="m-time">' + t + ' · 38 tok</span></div>');
        body.scrollTop = body.scrollHeight;
        ppT.classList.remove('on'); ppM.classList.remove('on');
      }, 900);
    }

    /* chat log modal */
    if (!document.getElementById('modal-chatlog')) {
      const log = document.createElement('div');
      log.className = 'modal-back'; log.id = 'modal-chatlog';
      log.innerHTML = '<div class="modal w-lg"><div class="modal-head"><span class="mic">' + icon('files') + '</span><div style="flex:1"><h3>Background execution logs</h3><div class="msub">Per-turn pipeline trace · organised by stage</div></div><button class="icon-btn" data-close>' + icon('x') + '</button></div>' +
        '<div class="modal-body" style="font-family:var(--mono);font-size:12px;display:grid;gap:7px">' +
        '<div><span class="badge ok">DISPATCH</span> &nbsp;turn #42 accepted · model glm-4.6 · history=12 msgs</div>' +
        '<div><span class="badge info">ROUTER</span> &nbsp;intent=ledger_query · tools=ledger.outstanding, bills.search</div>' +
        '<div><span class="badge warn">TOOL-CALL</span> ledger.outstanding(limit=5) → round 1 · 214ms · ok</div>' +
        '<div><span class="badge ok">MCP-EXEC</span> McpToolRegistry.call() → DataManager.readOnly · 96ms</div>' +
        '<div><span class="badge neutral">TOKENS</span> in 1,842 · out 306 · total 2,148 / 32,768</div>' +
        '<div><span class="badge ok">DISPATCH</span> turn #41 complete · 1.9s wall</div>' +
        '</div>' +
        '<div class="modal-foot"><span class="spacer"></span><button class="btn ghost" data-close>Close</button></div></div>';
      document.body.appendChild(log);
      log.addEventListener('click', e => { if (e.target === log) log.classList.remove('open'); });
    }
  }

  /* ---------- boot ---------- */
  document.addEventListener('DOMContentLoaded', () => {
    const app = document.createElement('div');
    app.className = 'app';
    const main = document.createElement('div');
    main.className = 'main';
    const content = document.querySelector('.content') || (() => {
      const c = document.createElement('div'); c.className = 'content'; document.body.appendChild(c); return c;
    })();
    content.classList.add('content');
    main.appendChild(content);
    document.body.appendChild(app);
    app.appendChild(main);
    buildSidebar();
    buildTopbar();
    buildUserPill();
    buildKeys();
    buildLogout();
    buildChatbot();
    document.querySelector('.page, .content > *') && content.firstElementChild.classList.add('view-enter');
    window.AuroraInjectIcons(document);
  });
})();
