/* ============================================================================
   Nexa·AI 管理后台 —— 共享外壳（基于 console-shell 扩展）
   ----------------------------------------------------------------------------
   顶栏 + 左侧栏视觉与机制与 console-shell.js 完全一致；区别：
     · 顶栏右侧多「角色切换 ▾」下拉，当前显示「管理视图」。
     · 侧栏 = 用户区精简组（仪表盘回 console） + overline 分隔 + 管理区分组，
       管理区每组头带小徽章「管理」(b-info)。
   用法：每页 <body> 设 data-page / data-title / data-crumb，引入后 NexaShell.mount()。
   纪律：只产 HTML / 绑行为，所有颜色靠 tokens.css 的 var()；零裸色值；零 emoji；图标内联线性 SVG。
   ============================================================================ */
(function () {
  'use strict';

  // ── 线性 stroke 图标库（24x24，stroke:currentColor） ──────────────────────
  var ICONS = {
    gauge: '<path d="M12 14l3-3"/><path d="M3.5 18a9 9 0 1 1 17 0"/><circle cx="12" cy="14" r="1.4"/>',
    grid: '<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>',
    server: '<rect x="3" y="4" width="18" height="7" rx="2"/><rect x="3" y="13" width="18" height="7" rx="2"/><path d="M7 7.5h.01"/><path d="M7 16.5h.01"/>',
    users: '<circle cx="9" cy="8" r="3.2"/><path d="M3.5 19a5.5 5.5 0 0 1 11 0"/><path d="M16 6.2a3 3 0 0 1 0 5.6"/><path d="M17.5 19a5.2 5.2 0 0 0-3-4.7"/>',
    cube: '<path d="M12 3l8 4.5v9L12 21l-8-4.5v-9z"/><path d="M4 7.5l8 4.5 8-4.5"/><path d="M12 12v9"/>',
    layers: '<path d="M12 3l9 5-9 5-9-5z"/><path d="M3 13l9 5 9-5"/>',
    tasks: '<path d="M9 6h11"/><path d="M9 12h11"/><path d="M9 18h11"/><path d="M3.5 6l1 1 2-2"/><path d="M3.5 12l1 1 2-2"/><path d="M3.5 18l1 1 2-2"/>',
    calc: '<rect x="4" y="3" width="16" height="18" rx="2"/><path d="M8 7h8"/><path d="M8 12h.01M12 12h.01M16 12h.01M8 16h.01M12 16h.01M16 16h.01"/>',
    ticket: '<path d="M3 8a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v2a2 2 0 0 0 0 4v2a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-2a2 2 0 0 0 0-4z"/><path d="M14 6v12"/>',
    file: '<path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5"/><path d="M8 13h8M8 17h5"/>',
    pulse: '<path d="M3 12h4l2-6 4 12 2-6h6"/>',
    settings: '<circle cx="12" cy="12" r="3"/><path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M18.4 5.6l-2.1 2.1M7.7 16.3l-2.1 2.1"/>',
    bell: '<path d="M6 9a6 6 0 0 1 12 0c0 6 2 7 2 7H4s2-1 2-7z"/><path d="M10.5 19a1.7 1.7 0 0 0 3 0"/>',
    swap: '<path d="M7 4v13"/><path d="M4 7l3-3 3 3"/><path d="M17 20V7"/><path d="M20 17l-3 3-3-3"/>',
    chevron: '<path d="M9 6l6 6-6 6"/>'
  };

  function icon(name, cls) {
    return '<svg class="nx-ic ' + (cls || '') + '" viewBox="0 0 24 24" fill="none" ' +
      'stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
      (ICONS[name] || '') + '</svg>';
  }

  // ── 导航树：用户区精简组 + 管理区分组（管理组带「管理」徽章 admin:true） ────────
  var NAV = [
    { group: '用户区', items: [
      { id: 'dashboard', label: '仪表盘', href: '../console/dashboard.html', ic: 'gauge' }
    ]},
    { group: '管理总览', admin: true, items: [
      { id: 'admin-dashboard', label: '全局概览', href: 'admin-dashboard.html', ic: 'grid' }
    ]},
    { group: '资源管理', admin: true, items: [
      { id: 'channels', label: '渠道管理',    href: 'channels.html',      ic: 'server' },
      { id: 'users',    label: '用户管理',    href: 'users.html',         ic: 'users' },
      { id: 'models',   label: '模型/供应商', href: 'models-admin.html',  ic: 'cube' },
      { id: 'groups',   label: '预填分组',    href: 'groups.html',        ic: 'layers' }
    ]},
    { group: '运营', admin: true, items: [
      { id: 'tasks',   label: '任务监控', href: 'tasks-monitor.html', ic: 'tasks' },
      { id: 'billing', label: '计费规则', href: 'billing-rules.html', ic: 'calc' },
      { id: 'profit',  label: '利润分析', href: 'profit.html',        ic: 'pulse' },
      { id: 'redeem',  label: '兑换码',   href: 'redeem.html',        ic: 'ticket' }
    ]},
    { group: '系统', admin: true, items: [
      { id: 'logs', label: '日志审计', href: 'logs.html',         ic: 'file' },
      { id: 'ops',  label: '运维监控', href: 'ops.html',          ic: 'pulse' },
      { id: 'sys',  label: '系统设置', href: 'sys-settings.html', ic: 'settings' }
    ]}
  ];

  function buildSidebar(active) {
    var html = '';
    NAV.forEach(function (grp) {
      var badge = grp.admin
        ? '<span class="badge b-info nx-navbadge">管理</span>' : '';
      html += '<div class="nx-navgrp' + (grp.admin ? ' nx-navgrp-admin' : '') + '">' +
        '<div class="nx-navhead">' + grp.group + badge + '</div>';
      grp.items.forEach(function (it) {
        var on = it.id === active ? ' nx-on' : '';
        html += '<a class="nx-navlink' + on + '" href="' + it.href + '">' +
          '<span class="nx-navbar" aria-hidden="true"></span>' +
          icon(it.ic, 'nx-navic') +
          '<span>' + it.label + '</span></a>';
      });
      html += '</div>';
    });
    return html;
  }

  function buildCrumb(crumb) {
    var parts = [];
    crumb.forEach(function (c, i) {
      if (i > 0) parts.push('<span class="nx-crumb-sep">' + icon('chevron') + '</span>');
      parts.push('<span class="nx-crumb-item">' + c + '</span>');
    });
    return parts.join('');
  }

  function shellStyles() {
    return '<style id="nx-shell-style">' +
      '.nx-ic{width:18px;height:18px;flex:none}' +
      // 顶栏
      '.nx-top{position:fixed;top:0;left:0;right:0;height:56px;z-index:40;display:flex;align-items:center;' +
        'gap:var(--space-4);padding:0 var(--space-5);background:var(--color-bg-elevated);' +
        'border-bottom:1px solid var(--color-border)}' +
      '.nx-burger{display:none;background:transparent;border:none;color:var(--color-text-secondary);cursor:pointer;' +
        'width:36px;height:36px;align-items:center;justify-content:center;border-radius:var(--radius-sm)}' +
      '.nx-burger:hover{background:var(--color-surface-sunken);color:var(--color-text)}' +
      '.nx-logo{display:flex;align-items:center;gap:var(--space-3);font-family:var(--font-brand);' +
        'font-weight:var(--fw-bold);font-size:var(--text-h5);color:var(--color-text);letter-spacing:var(--tracking-snug)}' +
      '.nx-logo-sq{width:28px;height:28px;border-radius:var(--radius-sm);display:flex;align-items:center;justify-content:center;' +
        'background:linear-gradient(135deg,var(--color-primary-500),var(--color-primary-700));color:var(--color-primary-fg);' +
        'font-weight:var(--fw-bold);font-size:var(--text-body)}' +
      '.nx-top-right{margin-left:auto;display:flex;align-items:center;gap:var(--space-4)}' +
      // 角色切换下拉（管理台特有）
      '.nx-role{position:relative}' +
      '.nx-role-btn{display:inline-flex;align-items:center;gap:var(--space-2);height:32px;padding:0 var(--space-3);' +
        'border-radius:var(--radius-full);background:color-mix(in oklch,var(--color-primary-500) 14%,transparent);' +
        'border:1px solid color-mix(in oklch,var(--color-primary-500) 36%,transparent);' +
        'color:var(--color-primary-700);font-family:inherit;font-size:var(--text-body-sm);font-weight:var(--fw-medium);cursor:pointer}' +
      '.nx-role-btn .nx-ic{width:15px;height:15px}' +
      '.nx-role-menu{display:none;position:absolute;top:40px;right:0;min-width:172px;z-index:50;' +
        'background:var(--color-bg-elevated);border:1px solid var(--color-border);border-radius:var(--radius-md);' +
        'box-shadow:var(--shadow-lg);padding:var(--space-2)}' +
      '.nx-role-menu.nx-open{display:block}' +
      '.nx-role-opt{display:flex;align-items:center;gap:var(--space-2);height:36px;padding:0 var(--space-3);' +
        'border-radius:var(--radius-sm);color:var(--color-text-secondary);font-size:var(--text-body-sm);cursor:pointer}' +
      '.nx-role-opt:hover{background:var(--color-surface-sunken);color:var(--color-text)}' +
      '.nx-role-opt.nx-cur{color:var(--color-primary-700);background:color-mix(in oklch,var(--color-primary-500) 10%,transparent)}' +
      '.nx-iconbtn{position:relative;background:transparent;border:none;color:var(--color-text-secondary);cursor:pointer;' +
        'width:36px;height:36px;display:flex;align-items:center;justify-content:center;border-radius:var(--radius-sm)}' +
      '.nx-iconbtn:hover{background:var(--color-surface-sunken);color:var(--color-text)}' +
      '.nx-iconbtn .nx-dotmk{position:absolute;top:7px;right:8px;width:7px;height:7px;border-radius:50%;' +
        'background:var(--color-danger);border:2px solid var(--color-bg-elevated)}' +
      '.nx-user{display:flex;align-items:center;gap:var(--space-2);cursor:pointer;padding:var(--space-1) var(--space-2);' +
        'border-radius:var(--radius-sm)}' +
      '.nx-user:hover{background:var(--color-surface-sunken)}' +
      '.nx-avatar{width:30px;height:30px;border-radius:50%;background:color-mix(in oklch,var(--color-primary-500) 22%,transparent);' +
        'color:var(--color-primary-700);display:flex;align-items:center;justify-content:center;' +
        'font-weight:var(--fw-semibold);font-size:var(--text-body-sm)}' +
      '.nx-user-name{font-size:var(--text-body-sm);color:var(--color-text);font-weight:var(--fw-medium)}' +
      // 侧栏
      '.nx-side{position:fixed;top:56px;left:0;bottom:0;width:240px;z-index:30;overflow-y:auto;' +
        'background:var(--color-bg-elevated);border-right:1px solid var(--color-border);' +
        'padding:var(--space-4) var(--space-3);transition:transform var(--dur-2) var(--ease-out)}' +
      '.nx-navgrp{margin-bottom:var(--space-5)}' +
      '.nx-navgrp-admin{padding-top:var(--space-4);border-top:1px solid var(--color-border)}' +
      '.nx-navgrp-admin + .nx-navgrp-admin{padding-top:0;border-top:none}' +
      '.nx-navhead{display:flex;align-items:center;gap:var(--space-2);font-size:var(--text-overline);text-transform:uppercase;' +
        'letter-spacing:var(--tracking-wide);color:var(--color-text-muted);font-weight:var(--fw-semibold);' +
        'padding:0 var(--space-3);margin-bottom:var(--space-2)}' +
      '.nx-navbadge{text-transform:none;letter-spacing:0;font-size:var(--text-overline);padding:1px 7px;font-weight:var(--fw-semibold)}' +
      '.nx-navlink{position:relative;display:flex;align-items:center;gap:var(--space-3);height:40px;padding:0 var(--space-3);' +
        'border-radius:var(--radius-sm);color:var(--color-text-secondary);font-size:var(--text-body);' +
        'font-weight:var(--fw-medium);transition:background var(--dur-1),color var(--dur-1)}' +
      '.nx-navlink:hover{background:var(--color-surface-sunken);color:var(--color-text)}' +
      '.nx-navbar{position:absolute;left:0;top:8px;bottom:8px;width:3px;border-radius:var(--radius-full);background:transparent}' +
      '.nx-navic{color:var(--color-text-muted)}' +
      '.nx-navlink.nx-on{background:color-mix(in oklch,var(--color-primary-500) 12%,transparent);color:var(--color-primary-700)}' +
      '.nx-navlink.nx-on .nx-navbar{background:var(--color-primary-500)}' +
      '.nx-navlink.nx-on .nx-navic{color:var(--color-primary-700)}' +
      // 内容区
      '.nx-shell-main{margin-left:240px;padding-top:56px;min-height:100vh;background:var(--color-bg)}' +
      '.nx-content{max-width:1320px;margin:0 auto;padding:var(--space-6)}' +
      '.nx-pagehead{display:flex;align-items:flex-end;justify-content:space-between;gap:var(--space-4);' +
        'margin-bottom:var(--space-6);flex-wrap:wrap}' +
      '.nx-crumb{display:flex;align-items:center;gap:var(--space-1);font-size:var(--text-body-sm);' +
        'color:var(--color-text-muted);margin-bottom:var(--space-2)}' +
      '.nx-crumb-sep{display:inline-flex;color:var(--color-text-muted)}.nx-crumb-sep .nx-ic{width:14px;height:14px}' +
      '.nx-crumb-item:last-child{color:var(--color-text-secondary)}' +
      '.nx-pagetitle{font-family:var(--font-brand);font-size:var(--text-h2);font-weight:var(--fw-bold);' +
        'color:var(--color-text);margin:0;letter-spacing:var(--tracking-snug)}' +
      '.nx-pageacts{display:flex;align-items:center;gap:var(--space-3)}' +
      // 遮罩（移动抽屉）
      '.nx-scrim{display:none;position:fixed;inset:0;z-index:25;background:color-mix(in oklch,var(--color-surface-sunken) 70%,transparent)}' +
      // 卡片入场动效（admin 更克制：仅淡入，无位移弹性收尾仍保留极轻）
      '.nx-fade{opacity:0;animation:nxfade var(--dur-2) var(--ease-out) forwards}' +
      '@keyframes nxfade{to{opacity:1}}' +
      // 移动端
      '@media (max-width:1024px){' +
        '.nx-burger{display:flex}' +
        '.nx-side{transform:translateX(-100%)}' +
        '.nx-side.nx-open{transform:none;box-shadow:var(--shadow-lg)}' +
        '.nx-shell-main{margin-left:0}' +
        '.nx-scrim.nx-open{display:block}' +
        '.nx-role-btn span:not(.nx-ic){display:none}' +
      '}' +
      '@media (prefers-reduced-motion:reduce){.nx-fade{animation-duration:.01s}}' +
    '</style>';
  }

  function mount(opts) {
    opts = opts || {};
    var body = document.body;
    var active = body.getAttribute('data-page') || opts.page || '';
    var title = body.getAttribute('data-title') || opts.title || '';
    var crumbAttr = body.getAttribute('data-crumb');
    var crumb = crumbAttr ? crumbAttr.split('|') : (opts.crumb || ['管理后台', title]);
    var userName = body.getAttribute('data-user') || opts.user || 'admin.root';
    var role = body.getAttribute('data-role') || opts.role || '管理视图';

    // 把页面原有内容暂存；保留 <script> 在 body 原位（不搬入主区，避免乱序/重复）
    var pageContent = '';
    var keep = [];
    var children = Array.prototype.slice.call(body.childNodes);
    children.forEach(function (n) {
      if (n.nodeType === 1 && n.tagName === 'SCRIPT') { keep.push(n); return; }
      pageContent += n.outerHTML !== undefined ? n.outerHTML : (n.textContent || '');
      body.removeChild(n);
    });

    if (!document.getElementById('nx-shell-style')) {
      document.head.insertAdjacentHTML('beforeend', shellStyles());
    }

    var actionsHTML = body.getAttribute('data-actions') || opts.actionsHTML || '';

    var top =
      '<header class="nx-top">' +
        '<button class="nx-burger" id="nx-burger" aria-label="菜单">' +
          icon('tasks') + '</button>' +
        '<a class="nx-logo" href="admin-dashboard.html">' +
          '<span class="nx-logo-sq">N</span><span>Nexa·AI</span></a>' +
        '<div class="nx-top-right">' +
          '<div class="nx-role" id="nx-role">' +
            '<button class="nx-role-btn" id="nx-role-btn" type="button" aria-haspopup="true">' +
              icon('swap') + '<span>' + role + '</span>' + icon('chevron') +
            '</button>' +
            '<div class="nx-role-menu" id="nx-role-menu" role="menu">' +
              '<div class="nx-role-opt nx-cur" role="menuitem">' + icon('swap') + '<span>管理视图</span></div>' +
              '<a class="nx-role-opt" role="menuitem" href="../console/dashboard.html">' +
                icon('gauge') + '<span>用户视图</span></a>' +
            '</div>' +
          '</div>' +
          '<button class="nx-iconbtn" aria-label="通知">' + icon('bell') +
            '<span class="nx-dotmk"></span></button>' +
          '<div class="nx-user">' +
            '<span class="nx-avatar">' + userName.charAt(0).toUpperCase() + '</span>' +
            '<span class="nx-user-name">' + userName + '</span>' +
            icon('chevron') +
          '</div>' +
        '</div>' +
      '</header>';

    var side =
      '<aside class="nx-side" id="nx-side">' + buildSidebar(active) + '</aside>' +
      '<div class="nx-scrim" id="nx-scrim"></div>';

    var main =
      '<div class="nx-shell-main">' +
        '<div class="nx-content">' +
          '<div class="nx-pagehead">' +
            '<div>' +
              '<div class="nx-crumb">' + buildCrumb(crumb) + '</div>' +
              '<h1 class="nx-pagetitle">' + title + '</h1>' +
            '</div>' +
            '<div class="nx-pageacts" id="nx-pageacts">' + actionsHTML + '</div>' +
          '</div>' +
          '<div id="nx-main">' + pageContent + '</div>' +
        '</div>' +
      '</div>';

    body.insertAdjacentHTML('afterbegin', top + side + main);

    // 移动抽屉
    var sideEl = document.getElementById('nx-side');
    var scrim = document.getElementById('nx-scrim');
    var burger = document.getElementById('nx-burger');
    function toggle(open) {
      sideEl.classList.toggle('nx-open', open);
      scrim.classList.toggle('nx-open', open);
    }
    if (burger) burger.addEventListener('click', function () { toggle(!sideEl.classList.contains('nx-open')); });
    if (scrim) scrim.addEventListener('click', function () { toggle(false); });

    // 角色切换下拉
    var roleBtn = document.getElementById('nx-role-btn');
    var roleMenu = document.getElementById('nx-role-menu');
    if (roleBtn && roleMenu) {
      roleBtn.addEventListener('click', function (e) {
        e.stopPropagation();
        roleMenu.classList.toggle('nx-open');
      });
      document.addEventListener('click', function () { roleMenu.classList.remove('nx-open'); });
    }

    // 卡片逐项淡入（极轻 stagger）
    var fades = document.querySelectorAll('.nx-fade');
    fades.forEach(function (el, i) { el.style.animationDelay = (i * 0.05) + 's'; });

    return { actionsSlot: document.getElementById('nx-pageacts'), mainSlot: document.getElementById('nx-main') };
  }

  // ── KPI 数字 Count Up ──────────────────────────────────────────────────────
  function countUp(el, opts) {
    opts = opts || {};
    var target = parseFloat(el.getAttribute('data-count') || el.textContent.replace(/[^0-9.\-]/g, '')) || 0;
    var dec = parseInt(el.getAttribute('data-dec') || opts.dec || 0, 10);
    var prefix = el.getAttribute('data-prefix') || opts.prefix || '';
    var suffix = el.getAttribute('data-suffix') || opts.suffix || '';
    var dur = 550, t0 = null;
    function fmt(v) {
      var s = dec > 0 ? v.toFixed(dec) : Math.round(v).toString();
      if (!dec) s = s.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
      return prefix + s + suffix;
    }
    function step(ts) {
      if (!t0) t0 = ts;
      var p = Math.min((ts - t0) / dur, 1);
      var e = 1 - Math.pow(1 - p, 3);
      el.textContent = fmt(target * e);
      if (p < 1) requestAnimationFrame(step);
      else el.textContent = fmt(target);
    }
    requestAnimationFrame(step);
  }

  function countAll(sel) {
    document.querySelectorAll(sel || '[data-count]').forEach(function (el) { countUp(el); });
  }

  // ── 工具：读取 CSS 变量真实值（供 SVG fill/stroke，避免裸色值） ──────────────
  function cssvar(n) { return getComputedStyle(document.documentElement).getPropertyValue(n).trim(); }

  window.NexaShell = { mount: mount, icon: icon, countUp: countUp, countAll: countAll, cssvar: cssvar };
})();
