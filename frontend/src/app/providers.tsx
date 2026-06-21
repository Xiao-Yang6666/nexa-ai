'use client';

import { useState, type ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { installMock } from '@/shared/api';

/**
 * 全局 Provider 壳：React Query + 开发期 mock 安装。
 *
 * mock 在 NEXT_PUBLIC_USE_MOCK=1 时拦截 auth 端点，实现前后端并行解耦。
 * 安装放在 client provider 内，确保只在浏览器执行一次。
 */
if (process.env.NEXT_PUBLIC_USE_MOCK === '1') {
  installMock();
}

export function Providers({ children }: { children: ReactNode }) {
  const [client] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: { retry: 1, refetchOnWindowFocus: false },
          mutations: { retry: 0 },
        },
      }),
  );
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}
