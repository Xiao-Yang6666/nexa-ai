/**
 * shared/api/mock — 开发期 mock，前后端并行解耦点。
 *
 * 通过 monkey-patch window.fetch 拦截 openapi 契约里的 auth 端点，
 * 返回符合 ApiResponse 包络 + UserView DTO 的桩数据。
 * 仅在 NEXT_PUBLIC_USE_MOCK=1 且浏览器环境下启用。
 *
 * 设计选择：相较 MSW service worker，这里用轻量 fetch 拦截，
 * 零额外运行时依赖、在 Next App Router 客户端组件下零配置即可工作。
 * 后端就绪后把 NEXT_PUBLIC_USE_MOCK 关掉即走真实接口（client.ts 不变）。
 */
import type { UserView } from './types';
import { MOCK_PRICING } from './mock-pricing';
import {
  CONSOLE_EXACT_ROUTES,
  CONSOLE_REGEX_ROUTES,
  parseBody,
} from './mock-console';

interface MockEnvelope {
  success: boolean;
  message?: string;
  data?: unknown;
}

const MOCK_USER: UserView = {
  id: 10086,
  username: 'demo',
  display_name: 'Demo 用户',
  role: 1,
  status: 1,
  group: 'default',
  quota: 5_000_000,
  used_quota: 128_400,
  request_count: 326,
  aff_code: 'NEXA8',
  aff_count: 2,
  email: 'demo@nexa.ai',
  last_login_at: Math.floor(Date.now() / 1000),
};

function json(body: MockEnvelope, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

type Handler = (init: RequestInit | undefined, body: unknown) => Response | undefined;

const ROUTES: Record<string, Handler> = {
  // POST /api/user/login — 邮箱/用户名 + 密码（openapi F-1002）
  'POST /api/user/login': (_init, body) => {
    const b = (body ?? {}) as { username?: string; password?: string };
    if (!b.username || !b.password) {
      return json({ success: false, message: '用户名或密码不能为空' }, 400);
    }
    if (b.password.length < 6) {
      return json({ success: false, message: '账号或密码错误' }, 200);
    }
    return json({
      success: true,
      message: '登录成功',
      data: { ...MOCK_USER, username: b.username },
    });
  },
  // POST /api/user/register — 邮箱密码注册（openapi F-1001）
  'POST /api/user/register': (_init, body) => {
    const b = (body ?? {}) as { username?: string; password?: string };
    if (!b.username || !b.password) {
      return json({ success: false, message: '用户名和密码必填' }, 400);
    }
    return json({ success: true, message: '注册成功，请登录', data: null });
  },
  // GET /api/pricing — 公开模型价格页（openapi F-2048，PublicView 零泄露）
  'GET /api/pricing': () => {
    return json({ success: true, data: MOCK_PRICING });
  },
};

let installed = false;

/** 安装 mock 拦截器（幂等）。在客户端入口调用。 */
export function installMock(): void {
  if (installed || typeof window === 'undefined') return;
  installed = true;
  const original = window.fetch.bind(window);

  // 合并 auth/pricing 桩与控制台 self-scope 桩为统一精确路由表
  const exact: Record<string, Handler> = { ...ROUTES, ...CONSOLE_EXACT_ROUTES };

  window.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === 'string' ? input : input.toString();
    const method = (init?.method ?? 'GET').toUpperCase();
    const path = url.replace(/^https?:\/\/[^/]+/, '').split('?')[0];
    // 把完整 url 透传给 handler（供带 query 的端点解析筛选参数）
    const initWithUrl = { ...(init ?? {}), __url: url } as RequestInit & { __url: string };

    const parsed = parseBody(init);

    // 1) 精确匹配
    const key = `${method} ${path}`;
    const handler = exact[key];
    if (handler) {
      await new Promise((r) => setTimeout(r, 420));
      const mocked = handler(initWithUrl, parsed);
      if (mocked) return mocked;
    }

    // 2) 正则匹配（带路径参数的端点，如 model_aliases/{id}）
    for (const route of CONSOLE_REGEX_ROUTES) {
      if (route.method !== method) continue;
      const m = path.match(route.pattern);
      if (m) {
        await new Promise((r) => setTimeout(r, 420));
        const mocked = route.handler(initWithUrl, parsed, m.slice(1));
        if (mocked) return mocked;
      }
    }

    return original(input, init);
  };

  // eslint-disable-next-line no-console
  console.info('[nexa] API mock 已启用（NEXT_PUBLIC_USE_MOCK=1）');
}
