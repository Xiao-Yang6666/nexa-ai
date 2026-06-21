'use client';

import { useEffect, useRef } from 'react';

/**
 * FlowingThreads — 深色门面主视觉「光束网络 + 流光」canvas。
 *
 * 从 06_prototype/final/web-public/home.html 的 canvas 逻辑 1:1 工程化迁移：
 * 发光锚点用细线连成网络，沿线有流光游走；节点缓慢漂移 + 呼吸；鼠标靠近吸引/提亮。
 * 纯 canvas 2D，零外部库；尊重 prefers-reduced-motion（降速不冻结，品牌核心）。
 * 颜色从 tokens.css 的 CSS 变量读取，不在 JS 散落裸色值（保持色值纪律）。
 *
 * @param fixed true=全屏固定背景（home）；false=填充父容器（login 左栏）
 */
export function FlowingThreads({ fixed = false }: { fixed?: boolean }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const reduce =
      window.matchMedia &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const SPEED = reduce ? 0.42 : 1.0;

    const cs = getComputedStyle(document.documentElement);
    const tok = (name: string, fallback: string) =>
      cs.getPropertyValue(name).trim() || fallback;
    const palette = [
      tok('--color-primary-500', '#0E7C86'),
      tok('--hd-cyan', '#2BB7C2'),
      tok('--hd-mint', '#5FD4DE'),
      tok('--hd-lav', '#7AA2F7'),
    ];

    const DPR = Math.min(window.devicePixelRatio || 1, 2);
    let W = 0;
    let H = 0;

    interface Node {
      x: number;
      y: number;
      vx: number;
      vy: number;
      r: number;
      c: string;
      phase: number;
    }
    interface Link {
      a: number;
      b: number;
      t: number;
      speed: number;
    }
    let nodes: Node[] = [];
    let links: Link[] = [];
    const mouse = { x: -9999, y: -9999, active: false };

    const rand = (a: number, b: number) => a + Math.random() * (b - a);

    function build() {
      if (!canvas || !ctx) return;
      const rect = canvas.getBoundingClientRect();
      W = rect.width;
      H = rect.height;
      canvas.width = Math.floor(W * DPR);
      canvas.height = Math.floor(H * DPR);
      ctx.setTransform(DPR, 0, 0, DPR, 0, 0);

      const count = Math.max(16, Math.min(44, Math.round((W * H) / 40000)));
      nodes = [];
      for (let i = 0; i < count; i++) {
        nodes.push({
          x: rand(0, W),
          y: rand(0, H),
          vx: rand(-0.2, 0.2),
          vy: rand(-0.2, 0.2),
          r: rand(1.1, 2.5),
          c: palette[i % palette.length],
          phase: rand(0, Math.PI * 2),
        });
      }
      links = [];
      for (let a = 0; a < nodes.length; a++) {
        const dists: { b: number; d: number }[] = [];
        for (let b = 0; b < nodes.length; b++) {
          if (a === b) continue;
          const dx = nodes[a].x - nodes[b].x;
          const dy = nodes[a].y - nodes[b].y;
          dists.push({ b, d: dx * dx + dy * dy });
        }
        dists.sort((p, q) => p.d - q.d);
        const k = 2 + (a % 2);
        for (let j = 0; j < k && j < dists.length; j++) {
          const bi = dists[j].b;
          if (bi > a) {
            links.push({ a, b: bi, t: rand(0, 1), speed: rand(0.002, 0.005) });
          }
        }
      }
    }

    function hexToRgb(h: string): [number, number, number] {
      let s = h.replace('#', '');
      if (s.length === 3)
        s = s
          .split('')
          .map((x) => x + x)
          .join('');
      const n = parseInt(s, 16);
      return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
    }
    function rgba(hex: string, a: number) {
      const c = hexToRgb(hex);
      return `rgba(${c[0]},${c[1]},${c[2]},${a})`;
    }

    let t = 0;
    let raf = 0;

    function frame() {
      if (!ctx) return;
      t += 1;
      ctx.clearRect(0, 0, W, H);

      for (const n of nodes) {
        n.x += n.vx * SPEED;
        n.y += n.vy * SPEED;
        if (n.x < 0 || n.x > W) n.vx *= -1;
        if (n.y < 0 || n.y > H) n.vy *= -1;
        n.x = Math.max(0, Math.min(W, n.x));
        n.y = Math.max(0, Math.min(H, n.y));
        if (mouse.active) {
          const dx = mouse.x - n.x;
          const dy = mouse.y - n.y;
          const d = Math.sqrt(dx * dx + dy * dy);
          if (d < 190 && d > 0.01) {
            const f = ((190 - d) / 190) * 0.07;
            n.x += (dx / d) * f;
            n.y += (dy / d) * f;
          }
        }
      }

      for (let li = 0; li < links.length; li++) {
        const L = links[li];
        const A = nodes[L.a];
        const B = nodes[L.b];
        const dx = B.x - A.x;
        const dy = B.y - A.y;
        const dist = Math.sqrt(dx * dx + dy * dy);
        if (dist > 340) continue;

        const mx = (A.x + B.x) / 2;
        const my = (A.y + B.y) / 2;
        let near = 0;
        if (mouse.active) {
          const mdx = mouse.x - mx;
          const mdy = mouse.y - my;
          const md = Math.sqrt(mdx * mdx + mdy * mdy);
          near = md < 230 ? (230 - md) / 230 : 0;
        }
        const baseA = 0.085 + near * 0.3;
        ctx.beginPath();
        ctx.moveTo(A.x, A.y);
        ctx.lineTo(B.x, B.y);
        ctx.strokeStyle = rgba(A.c, baseA);
        ctx.lineWidth = 1;
        ctx.stroke();

        L.t += L.speed * SPEED;
        if (L.t > 1) L.t -= 1;
        const px = A.x + dx * L.t;
        const py = A.y + dy * L.t;
        const ux = dx / (dist || 1);
        const uy = dy / (dist || 1);
        const tailLen = Math.min(34, dist * 0.34);
        const tx = px - ux * tailLen;
        const ty = py - uy * tailLen;
        const trail = ctx.createLinearGradient(tx, ty, px, py);
        trail.addColorStop(0, rgba(B.c, 0));
        trail.addColorStop(1, rgba(B.c, 0.55 + near * 0.3));
        ctx.beginPath();
        ctx.moveTo(tx, ty);
        ctx.lineTo(px, py);
        ctx.strokeStyle = trail;
        ctx.lineWidth = 1.6;
        ctx.stroke();

        const pulse = 0.5 + 0.5 * Math.sin(t * 0.05 + li);
        const pr = 1.7 + pulse * 1.3;
        const grad = ctx.createRadialGradient(px, py, 0, px, py, pr * 4.4);
        grad.addColorStop(0, rgba(B.c, 0.95));
        grad.addColorStop(1, rgba(B.c, 0));
        ctx.beginPath();
        ctx.fillStyle = grad;
        ctx.arc(px, py, pr * 4.4, 0, Math.PI * 2);
        ctx.fill();
        ctx.beginPath();
        ctx.fillStyle = rgba(B.c, 0.98);
        ctx.arc(px, py, pr * 0.62, 0, Math.PI * 2);
        ctx.fill();
      }

      for (const nd of nodes) {
        const glow = 2.8 + 1.6 * Math.sin(t * 0.035 + nd.phase);
        const g = ctx.createRadialGradient(
          nd.x,
          nd.y,
          0,
          nd.x,
          nd.y,
          nd.r + glow * 3,
        );
        g.addColorStop(0, rgba(nd.c, 0.58));
        g.addColorStop(1, rgba(nd.c, 0));
        ctx.beginPath();
        ctx.fillStyle = g;
        ctx.arc(nd.x, nd.y, nd.r + glow * 3, 0, Math.PI * 2);
        ctx.fill();
        ctx.beginPath();
        ctx.fillStyle = rgba(nd.c, 0.92);
        ctx.arc(nd.x, nd.y, nd.r, 0, Math.PI * 2);
        ctx.fill();
      }

      raf = requestAnimationFrame(frame);
    }

    function start() {
      if (raf) cancelAnimationFrame(raf);
      raf = requestAnimationFrame(frame);
    }
    function stop() {
      if (raf) {
        cancelAnimationFrame(raf);
        raf = 0;
      }
    }
    function init() {
      build();
      start();
    }

    const onMove = (e: MouseEvent) => {
      if (!canvas) return;
      const r = canvas.getBoundingClientRect();
      mouse.x = e.clientX - r.left;
      mouse.y = e.clientY - r.top;
      mouse.active = true;
    };
    const onOut = () => {
      mouse.active = false;
      mouse.x = -9999;
      mouse.y = -9999;
    };
    const onVis = () => {
      if (document.hidden) stop();
      else start();
    };
    let rt = 0;
    const onResize = () => {
      clearTimeout(rt);
      rt = window.setTimeout(init, 200);
    };

    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseout', onOut);
    document.addEventListener('visibilitychange', onVis);
    window.addEventListener('resize', onResize);

    // 兜底重试拿宽度，不依赖 load 事件（修复「刷新才动」bug）
    function boot() {
      if (!canvas) return;
      if (canvas.getBoundingClientRect().width > 0) init();
      else requestAnimationFrame(boot);
    }
    boot();

    return () => {
      stop();
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseout', onOut);
      document.removeEventListener('visibilitychange', onVis);
      window.removeEventListener('resize', onResize);
      clearTimeout(rt);
    };
  }, []);

  return (
    <canvas
      ref={canvasRef}
      aria-hidden="true"
      style={
        fixed
          ? {
              position: 'fixed',
              inset: 0,
              width: '100vw',
              height: '100vh',
              zIndex: -1,
              pointerEvents: 'none',
              display: 'block',
            }
          : {
              position: 'absolute',
              inset: 0,
              width: '100%',
              height: '100%',
              zIndex: 0,
              pointerEvents: 'none',
              display: 'block',
            }
      }
    />
  );
}
