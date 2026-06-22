/* ============================================================================
   Nexa·AI 控制台 —— 共享外壳（顶栏 + 左侧栏 + 内容区骨架）
   ----------------------------------------------------------------------------
   用法：每页 <body> 设 data-page="dashboard|keys|usage|..." 与 data-title / data-crumb
   引入本文件后调用 NexaShell.mount()，把外壳注入 <body> 开头，再把页面主区
   （原 <body> 内的内容）塞进 #nx-main 内的占位插槽。
   纪律：本文件只产出 HTML / 绑定行为，所有颜色靠 tokens.css 的 var()；零裸色值。
   ============================================================================ */
(function () {
  'use strict';

  // ── 线性 stroke 图标库（24x24，stroke:currentColor） ──────────────────────
  var ICONS = {
    gauge: '<path d="M12 14l3-3"/><path d="M3.5 18a9 9 0 1 1 17 0"/><circle cx="12" cy="14" r="1.4"/>',
    key: '<circle cx="7.5" cy="15.5" r="3.5"/><path d="M10 13l8-8"/><path d="M15 5l3 3"/><path d="M17 7l2-2"/>',
    bar: '<path d="M4 20V10"/><path d="M10 20V4"/><path d="M16 20v-7"/><path d="M21 20H3"/>',
    tasks: '<path d="M9 6h11"/><path d="M9 12h11"/><path d="M9 18h11"/><path d="M3.5 6l1 1 2-2"/><path d="M3.5 12l1 1 2-2"/><path d="M3.5 18l1 1 2-2"/>',
    receipt: '<path d="M5 3v18l2-1.4L9 21l2-1.4L13 21l2-1.4L17 21l2-1.4V3l-2 1.4L15 3l-2 1.4L11 3 9 4.4 7 3 5 4.4 5 3z"/><path d="M8 8h8"/><path d="M8 12h8"/>',
    wallet: '<path d="M3 7a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><path d="M16 11h4v4h-4a2 2 0 0 1 0-4z"/>',
    calendar: '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 9h18"/><path d="M8 3v4"/><path d="M16 3v4"/>',
    share: '<circle cx="6" cy="12" r="2.5"/><circle cx="18" cy="6" r="2.5"/><circle cx="18" cy="18" r="2.5"/><path d="M8.2 11l7.6-4"/><path d="M8.2 13l7.6 4"/>',
    settings: '<circle cx="12" cy="12" r="3"/><path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M18.4 5.6l-2.1 2.1M7.7 16.3l-2.1 2.1"/>',
    bell: '<path d="M6 9a6 6 0 0 1 12 0c0 6 2 7 2 7H4s2-1 2-7z"/><path d="M10.5 19a1.7 1.7 0 0 0 3 0"/>',
    chevron: '<path d="M9 6l6 6-6 6"/>'
  };

  function icon(name, cls) {
    return '<svg class="nx-ic ' + (cls || '') + '" viewBox="0 0 24 24" fill="none" ' +
      'stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
      (ICONS[name] || '') + '</svg>';
  }

  // ── 导航树 ────────────────────────────────────────────────────────────────
  var NAV = [
    { group: '概览', items: [
      { id: 'dashboard', label: '仪表盘', href: 'dashboard.html', ic: 'gauge' }
    ]},
    { group: '接入', items: [
      { id: 'keys',  label: 'API 密钥', href: 'keys.html',  ic: 'key' },
      { id: 'model-map', label: '模型映射', href: 'model-map.html', ic: 'share' },
      { id: 'usage', label: '用量统计', href: 'usage.html', ic: 'bar' },
      { id: 'tasks', label: '异步任务', href: 'tasks.html', ic: 'tasks' }
    ]},
    { group: '账户', items: [
      { id: 'billing',  label: '账单与计费', href: 'billing.html',  ic: 'receipt' },
      { id: 'recharge', label: '余额充值',   href: 'recharge.html', ic: 'wallet' }
    ]},
    { group: '增长', items: [
      { id: 'checkin',  label: '每日签到', href: 'checkin.html',  ic: 'calendar' },
      { id: 'referral', label: '分销推广', href: 'referral.html', ic: 'share' }
    ]},
    { group: '设置', items: [
      { id: 'settings', label: '个人设置', href: 'settings.html', ic: 'settings' }
    ]}
  ];

  function buildSidebar(active) {
    var html = '';
    NAV.forEach(function (grp) {
      html += '<div class="nx-navgrp"><div class="nx-navhead">' + grp.group + '</div>';
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
    // crumb: ["控制台","仪表盘"]
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
      '.nx-bal{display:inline-flex;align-items:center;gap:var(--space-2);height:32px;padding:0 var(--space-3);' +
        'border-radius:var(--radius-full);background:var(--color-surface-sunken);border:1px solid var(--color-border);' +
        'font-size:var(--text-body-sm);color:var(--color-text-secondary)}' +
      '.nx-bal b{font-family:var(--font-mono);color:var(--color-text);font-weight:var(--fw-semibold)}' +
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
      '.nx-navhead{font-size:var(--text-overline);text-transform:uppercase;letter-spacing:var(--tracking-wide);' +
        'color:var(--color-text-muted);font-weight:var(--fw-semibold);padding:0 var(--space-3);margin-bottom:var(--space-2)}' +
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
      '.nx-content{max-width:1200px;margin:0 auto;padding:var(--space-6)}' +
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
      // 卡片入场动效
      '.nx-fade{opacity:0;transform:translateY(8px);animation:nxfade var(--dur-2) var(--ease-out) forwards}' +
      '@keyframes nxfade{to{opacity:1;transform:none}}' +
      // 移动端
      '@media (max-width:1024px){' +
        '.nx-burger{display:flex}' +
        '.nx-side{transform:translateX(-100%)}' +
        '.nx-side.nx-open{transform:none;box-shadow:var(--shadow-lg)}' +
        '.nx-shell-main{margin-left:0}' +
        '.nx-scrim.nx-open{display:block}' +
        '.nx-bal{display:none}' +
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
    var crumb = crumbAttr ? crumbAttr.split('|') : (opts.crumb || ['控制台', title]);
    var balance = body.getAttribute('data-balance') || opts.balance || '$128.50';
    var userName = body.getAttribute('data-user') || opts.user || 'morgan.li';

    // 把页面原有内容（页面主区）暂存；保留 <script> 在 body 原位（不搬入主区，避免乱序/重复）
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
        '<a class="nx-logo" href="dashboard.html">' +
          '<span class="nx-logo-sq">N</span><span>Nexa·AI</span></a>' +
        '<div class="nx-top-right">' +
          '<span class="nx-bal">余额 <b>' + balance + '</b></span>' +
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

    // 卡片逐项淡入
    var fades = document.querySelectorAll('.nx-fade');
    fades.forEach(function (el, i) { el.style.animationDelay = (i * 0.06) + 's'; });

    // 暴露 actions 插槽，页面可二次填充
    return { actionsSlot: document.getElementById('nx-pageacts'), mainSlot: document.getElementById('nx-main') };
  }

  // ── KPI 数字 Count Up（var(--dur-3) 节律，简单实现） ──────────────────────
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

  window.NexaShell = { mount: mount, icon: icon, countUp: countUp, countAll: countAll };
})();
