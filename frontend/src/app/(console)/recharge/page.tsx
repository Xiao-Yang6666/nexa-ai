import type { Metadata } from 'next';
import { RechargePage } from '@/features/billing';

export const metadata: Metadata = {
  title: 'Nexa·AI · 余额充值',
  description: '选择充值金额与支付方式，享受充值赠送优惠。',
};

/** /recharge 路由：余额充值页（控制台 ConsoleShell 内嵌）。 */
export default function Page() {
  return <RechargePage />;
}
