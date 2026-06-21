import type { Metadata } from 'next';
import { RedeemPage } from '@/features/redeem';

export const metadata: Metadata = {
  title: 'Nexa·AI · 兑换码管理',
  description: '管理后台：兑换码管理——单个/批量生成、按状态/批次/搜索筛选、复制与作废操作。',
};

/** /admin/redeem 路由：兑换码管理页（AdminShell 内嵌）。 */
export default function Page() {
  return <RedeemPage />;
}
