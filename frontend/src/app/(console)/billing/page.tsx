import type { Metadata } from 'next';
import { BillingPage } from '@/features/billing';

export const metadata: Metadata = {
  title: 'Nexa·AI · 账单与计费',
  description: '余额、本月消费、计费说明与充值记录。',
};

/** /billing 路由：账单与计费页（控制台 ConsoleShell 内嵌）。 */
export default function Page() {
  return <BillingPage />;
}
