/* ============================================================================
   Nexa·AI 文档站 —— 共享外壳脚本 docs-shell.js
   ----------------------------------------------------------------------------
   职责：CodeBlock 四语言 Tab 切换 + 复制反馈、ToC scrollspy、ScrollReveal
        段落渐显、移动端 NavTree 抽屉。
   纪律：addEventListener / 事件委托，零内联 onclick；不引第三方库。
   ============================================================================ */
(function () {
  'use strict';

  /* ── CodeBlock：四语言 Tab 切换（同一 .codeblock 内 data-lang 同步） ───────── */
  function initCodeTabs() {
    document.querySelectorAll('.codeblock').forEach(function (block) {
      var tabs = block.querySelectorAll('.cb-tab');
      var panes = block.querySelectorAll('.cb-pane');
      block.addEventListener('click', function (e) {
        var tab = e.target.closest('.cb-tab');
        if (!tab || !block.contains(tab)) return;
        var lang = tab.getAttribute('data-lang');
        tabs.forEach(function (t) {
          var on = t.getAttribute('data-lang') === lang;
          t.classList.toggle('is-active', on);
          t.setAttribute('aria-selected', on ? 'true' : 'false');
        });
        panes.forEach(function (p) {
          p.classList.toggle('is-active', p.getAttribute('data-lang') === lang);
        });
      });
    });
  }

  /* ── CodeBlock：复制按钮（复制当前激活 pane 的纯文本，2s 成功反馈） ───────── */
  function initCopy() {
    document.querySelectorAll('.cb-copy').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var block = btn.closest('.codeblock');
        var pane = block ? block.querySelector('.cb-pane.is-active') : null;
        var code = pane ? pane.querySelector('code') : null;
        var text = code ? code.innerText : '';
        var done = function () {
          btn.classList.add('is-copied');
          var label = btn.querySelector('.cb-copy-label');
          var prev = label ? label.textContent : '';
          if (label) label.textContent = '已复制';
          setTimeout(function () {
            btn.classList.remove('is-copied');
            if (label) label.textContent = prev;
          }, 2000);
        };
        if (navigator.clipboard && navigator.clipboard.writeText) {
          navigator.clipboard.writeText(text).then(done, fallback);
        } else {
          fallback();
        }
        function fallback() {
          var ta = document.createElement('textarea');
          ta.value = text;
          ta.style.position = 'fixed';
          ta.style.opacity = '0';
          document.body.appendChild(ta);
          ta.select();
          try { document.execCommand('copy'); } catch (err) {}
          document.body.removeChild(ta);
          done();
        }
      });
    });
  }

  /* ── ToC scrollspy：当前 h2/h3 段高亮 + 点击平滑滚动 ──────────────────────── */
  function initScrollspy() {
    var toc = document.querySelector('.toc');
    if (!toc) return;
    var links = Array.prototype.slice.call(toc.querySelectorAll('a[href^="#"]'));
    if (!links.length) return;

    var targets = links.map(function (a) {
      var el = document.getElementById(a.getAttribute('href').slice(1));
      return el ? { link: a, el: el } : null;
    }).filter(Boolean);

    links.forEach(function (a) {
      a.addEventListener('click', function (e) {
        var el = document.getElementById(a.getAttribute('href').slice(1));
        if (!el) return;
        e.preventDefault();
        el.scrollIntoView({ behavior: 'smooth', block: 'start' });
        history.replaceState(null, '', a.getAttribute('href'));
      });
    });

    var setActive = function (link) {
      links.forEach(function (l) { l.classList.toggle('is-active', l === link); });
    };

    var spy = function () {
      var pos = window.scrollY + 120;
      var current = targets[0];
      for (var i = 0; i < targets.length; i++) {
        if (targets[i].el.offsetTop <= pos) current = targets[i];
      }
      if (current) setActive(current.link);
    };
    window.addEventListener('scroll', spy, { passive: true });
    spy();
  }

  /* ── ScrollReveal：段落渐显（IntersectionObserver；无则直接显示） ─────────── */
  function initReveal() {
    var els = document.querySelectorAll('.reveal');
    if (!('IntersectionObserver' in window)) {
      els.forEach(function (el) { el.classList.add('is-in'); });
      return;
    }
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (en) {
        if (en.isIntersecting) {
          en.target.classList.add('is-in');
          io.unobserve(en.target);
        }
      });
    }, { rootMargin: '0px 0px -8% 0px', threshold: 0.06 });
    els.forEach(function (el) { io.observe(el); });
  }

  /* ── 移动端 NavTree 抽屉（汉堡唤起，遮罩点击关闭） ───────────────────────── */
  function initDrawer() {
    var burger = document.querySelector('.burger');
    var nav = document.querySelector('.navtree');
    var scrim = document.querySelector('.scrim');
    if (!burger || !nav) return;
    var open = function (on) {
      nav.classList.toggle('is-open', on);
      if (scrim) scrim.classList.toggle('is-on', on);
      burger.setAttribute('aria-expanded', on ? 'true' : 'false');
    };
    burger.addEventListener('click', function () {
      open(!nav.classList.contains('is-open'));
    });
    if (scrim) scrim.addEventListener('click', function () { open(false); });
    nav.addEventListener('click', function (e) {
      if (e.target.closest('a')) open(false);
    });
  }

  function ready(fn) {
    if (document.readyState !== 'loading') fn();
    else document.addEventListener('DOMContentLoaded', fn);
  }
  ready(function () {
    initCodeTabs();
    initCopy();
    initScrollspy();
    initReveal();
    initDrawer();
  });
})();
