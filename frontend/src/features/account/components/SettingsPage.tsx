'use client';

import { useState, type ReactNode } from 'react';
import { ConsoleShell } from '@/features/console';
import { useSelf, useSaveSetting } from '../model/account.model';
import styles from './SettingsPage.module.css';

type TabId = 'profile' | 'security' | 'notify' | 'api';

const TABS: { id: TabId; label: string; icon: ReactNode }[] = [
  {
    id: 'profile',
    label: '账户资料',
    icon: (
      <>
        <circle cx="12" cy="8" r="4" />
        <path d="M4 21c0-4 4-6 8-6s8 2 8 6" />
      </>
    ),
  },
  {
    id: 'security',
    label: '安全',
    icon: (
      <>
        <path d="M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z" />
        <path d="M9 12l2 2 4-4" />
      </>
    ),
  },
  {
    id: 'notify',
    label: '通知偏好',
    icon: (
      <>
        <path d="M6 9a6 6 0 0 1 12 0c0 6 2 7 2 7H4s2-1 2-7z" />
        <path d="M10.5 19a1.7 1.7 0 0 0 3 0" />
      </>
    ),
  },
  {
    id: 'api',
    label: 'API 偏好',
    icon: (
      <>
        <path d="M8 4l-4 8 4 8" />
        <path d="M16 4l4 8-4 8" />
      </>
    ),
  },
];

function TabIcon({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <svg
      className={`${styles.nxIc} ${className ?? ''}`}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.6}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {children}
    </svg>
  );
}

/** Token 化自定义开关。 */
function Toggle({
  checked,
  onChange,
  label,
}: {
  checked: boolean;
  onChange?: (v: boolean) => void;
  label?: string;
}) {
  return (
    <label className={styles.toggle}>
      <input
        type="checkbox"
        role="switch"
        aria-checked={checked}
        aria-label={label}
        checked={checked}
        onChange={(e) => onChange?.(e.target.checked)}
      />
      <span className={styles.track} />
      <span className={styles.thumb} />
    </label>
  );
}

interface ToggleRow {
  key: string;
  title: string;
  desc: string;
}

const NOTIFY_ITEMS: ToggleRow[] = [
  { key: 'notify_billing', title: '账单与发票', desc: '每月账单生成、扣费成功 / 失败通知' },
  { key: 'notify_balance', title: '余额预警', desc: '余额低于设定阈值时提醒' },
  { key: 'notify_security', title: '安全告警', desc: '异常登录、密钥异常使用' },
  { key: 'notify_weekly', title: '用量周报', desc: '每周用量与成本汇总' },
  { key: 'notify_product', title: '产品更新', desc: '新模型上线、功能发布' },
  { key: 'notify_marketing', title: '营销活动', desc: '优惠、签到与推广活动' },
];

const DEVICES = [
  { type: 'desktop', name: 'MacBook Pro · Chrome', loc: '上海 · 中国', ip: '198.51.100.42', last: '当前设备', current: true },
  { type: 'mobile', name: 'iPhone 15 · Safari', loc: '上海 · 中国', ip: '198.51.100.88', last: '2 小时前', current: false },
  { type: 'desktop', name: 'Windows · Edge', loc: '北京 · 中国', ip: '203.0.113.17', last: '昨天 14:20', current: false },
  { type: 'desktop', name: 'Ubuntu · Firefox', loc: '深圳 · 中国', ip: '203.0.113.91', last: '3 天前', current: false },
];

function DeviceIcon({ kind }: { kind: string }) {
  return (
    <svg className={styles.nxIc} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round">
      {kind === 'mobile' ? (
        <>
          <rect x="7" y="3" width="10" height="18" rx="2" />
          <path d="M11 18h2" />
        </>
      ) : (
        <>
          <rect x="3" y="4" width="18" height="12" rx="2" />
          <path d="M8 20h8M12 16v4" />
        </>
      )}
    </svg>
  );
}

/**
 * SettingsPage — 个人设置（S6 console/settings.html 工程化）。
 *
 * 左子 Tab（账户资料 / 安全 / 通知偏好 / API 偏好）+ 右分组卡表单。
 * 接 GET /api/user/self（含 setting）+ PUT /api/user/self/setting 保存。
 * 客户端零泄露：仅展示本人资料/分组（计费折扣维度），无成本/利润/上游字段。
 */
export function SettingsPage() {
  const { data: account, isLoading, isError, refetch } = useSelf();
  const saveSetting = useSaveSetting();
  const [tab, setTab] = useState<TabId>('profile');

  const setting = (account?.setting ?? {}) as Record<string, unknown>;
  const getBool = (k: string, fallback = false) => (typeof setting[k] === 'boolean' ? (setting[k] as boolean) : fallback);
  const getNum = (k: string, fallback: number) => (typeof setting[k] === 'number' ? (setting[k] as number) : fallback);

  // 通知开关本地态（与 setting 同步，保存时一次性回写）
  const [notifyState, setNotifyState] = useState<Record<string, boolean> | null>(null);
  const notify = (k: string) =>
    notifyState?.[k] ?? getBool(k, k === 'notify_billing' || k === 'notify_balance' || k === 'notify_security');

  function toggleNotify(k: string, v: boolean) {
    setNotifyState((prev) => ({ ...(prev ?? {}), [k]: v }));
  }

  function saveNotify() {
    const merged: Record<string, boolean> = {};
    NOTIFY_ITEMS.forEach((it) => {
      merged[it.key] = notify(it.key);
    });
    saveSetting.mutate({ setting: merged });
  }

  if (isLoading) {
    return (
      <ConsoleShell activeId="settings" title="个人设置" crumb={['控制台', '个人设置']}>
        <div className={styles.skCard} />
        <div className={styles.skCard} />
      </ConsoleShell>
    );
  }

  if (isError || !account) {
    return (
      <ConsoleShell activeId="settings" title="个人设置" crumb={['控制台', '个人设置']}>
        <div className={`${styles.card} ${styles.stateBox}`}>
          <div className={styles.t}>设置加载失败</div>
          <div>网络或服务异常，请稍后重试。</div>
          <button className="btn btn-sec" style={{ marginTop: 'var(--space-4)' }} onClick={() => refetch()}>
            重试
          </button>
        </div>
      </ConsoleShell>
    );
  }

  return (
    <ConsoleShell activeId="settings" title="个人设置" crumb={['控制台', '个人设置']}>
      {/* 闭环导航 */}
      <div className={`${styles.loopnav} nx-fade`}>
        <a href="/keys">
          <svg className={styles.nxIc} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round">
            <path d="M14 7a4 4 0 1 1-3.5 6H7v3H4v-3l2-2a4 4 0 0 1 8-2z" />
          </svg>
          API 密钥
        </a>
        <span className={styles.sep}>·</span>
        <a href="/model-map">
          <svg className={styles.nxIc} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round">
            <circle cx="6" cy="12" r="2.5" />
            <circle cx="18" cy="6" r="2.5" />
            <circle cx="18" cy="18" r="2.5" />
            <path d="M8.3 11l7.4-4M8.3 13l7.4 4" />
          </svg>
          我的模型映射
        </a>
        <span className={styles.sep}>·</span>
        <a href="/recharge">
          <svg className={styles.nxIc} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round">
            <path d="M3 7h15a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
            <path d="M16 12h2" />
            <path d="M3 7l13-3v3" />
          </svg>
          分组与折扣
        </a>
      </div>

      <div className={styles.layout}>
        {/* 左侧子 Tab */}
        <div className={`${styles.tabs} nx-fade`}>
          {TABS.map((t) => (
            <button
              key={t.id}
              className={`${styles.tab} ${tab === t.id ? styles.on : ''}`}
              type="button"
              onClick={() => setTab(t.id)}
            >
              <TabIcon>{t.icon}</TabIcon>
              {t.label}
            </button>
          ))}
        </div>

        {/* 右侧面板 */}
        <div>
          {/* 账户资料 */}
          {tab === 'profile' && (
            <div className={`${styles.card} nx-fade`}>
              <h3>账户资料</h3>
              <p className={styles.cardSub}>这些信息将用于账单与团队协作展示。</p>
              <div className={styles.avatarRow}>
                <div className={styles.avatarLg}>{account.displayName.charAt(0).toUpperCase()}</div>
                <div className={styles.avatarMeta}>
                  <p>支持 JPG / PNG，建议 256×256，不超过 2 MB。</p>
                  <button className="btn btn-sec btn-sm">更换头像</button>
                </div>
              </div>
              <div className={styles.fld}>
                <label className="field-label">昵称</label>
                <input className="input" defaultValue={account.displayName} />
              </div>
              <div className={styles.fld}>
                <label className="field-label">邮箱</label>
                <div className={styles.inputVerified}>
                  <input className="input" defaultValue={account.email} readOnly />
                  <span className={`badge b-suc ${styles.verified}`}>
                    <svg className={styles.nxIc} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round">
                      <path d="M5 13l4 4 10-11" />
                    </svg>
                    已验证
                  </span>
                </div>
                <div className="field-hint">邮箱用于登录与安全通知，如需变更请联系支持。</div>
              </div>
              <div className={styles.fld}>
                <label className="field-label">公司 / 团队</label>
                <input className="input" placeholder="如：Nexa 智能科技有限公司" defaultValue={(setting.company as string) ?? ''} />
              </div>
              <div className={styles.formFoot}>
                <button className="btn btn-primary">保存更改</button>
                <button className="btn btn-ghost">取消</button>
              </div>
            </div>
          )}

          {/* 安全 */}
          {tab === 'security' && (
            <>
              <div className={`${styles.card} nx-fade`}>
                <h3>修改密码</h3>
                <p className={styles.cardSub}>建议使用至少 12 位、含大小写与符号的强密码。</p>
                <div className={styles.fld}>
                  <label className="field-label">当前密码</label>
                  <input className="input" type="password" placeholder="输入当前密码" />
                </div>
                <div className={styles.fld}>
                  <label className="field-label">新密码</label>
                  <input className="input" type="password" placeholder="输入新密码" />
                </div>
                <div className={styles.fld}>
                  <label className="field-label">确认新密码</label>
                  <input className="input" type="password" placeholder="再次输入新密码" />
                </div>
                <button className="btn btn-primary">更新密码</button>
              </div>

              <div className={`${styles.card} nx-fade`}>
                <h3>两步验证</h3>
                <p className={styles.cardSub}>登录时除密码外要求额外验证码，显著提升账户安全。</p>
                <div className={styles.toggleItem} style={{ border: 'none', padding: 0 }}>
                  <div>
                    <div className={styles.tiTitle}>基于 App 的两步验证（TOTP）</div>
                    <div className={styles.tiDesc}>使用 Authenticator 等应用生成动态验证码。</div>
                  </div>
                  <Toggle checked={getBool('totp_enabled')} label="两步验证" />
                </div>
              </div>

              <div className={`${styles.card} nx-fade`}>
                <h3>登录设备</h3>
                <p className={styles.cardSub}>当前已登录的设备，发现异常可随时登出。</p>
                <div className={styles.devices}>
                  {DEVICES.map((d) => (
                    <div key={d.ip} className={styles.device}>
                      <div className={styles.deviceIc}>
                        <DeviceIcon kind={d.type} />
                      </div>
                      <div className={styles.deviceBody}>
                        <div className={styles.dvName}>{d.name}</div>
                        <div className={styles.dvMeta}>
                          {d.loc} · IP <span className="mono-num">{d.ip}</span> · 最后活跃 {d.last}
                        </div>
                      </div>
                      {d.current ? (
                        <span className="badge b-suc">
                          <span className="dot" style={{ background: 'var(--color-success)' }} />
                          当前
                        </span>
                      ) : (
                        <button className="btn btn-sec btn-sm">登出</button>
                      )}
                    </div>
                  ))}
                </div>
              </div>
            </>
          )}

          {/* 通知偏好 */}
          {tab === 'notify' && (
            <>
              <div className={`${styles.card} nx-fade`}>
                <h3>余额预警</h3>
                <p className={styles.cardSub}>余额低于阈值时通知你，避免服务中断。</p>
                <div className={styles.fld}>
                  <label className="field-label">预警阈值（USD）</label>
                  <div className={styles.threshRow}>
                    <input className="input mono-num" defaultValue={getNum('warning_threshold', 20).toFixed(2)} />
                    <span className="muted" style={{ fontSize: 'var(--text-body-sm)' }}>
                      低于此值时发送预警
                    </span>
                  </div>
                </div>
                <button className="btn btn-primary">保存阈值</button>
              </div>

              <div className={`${styles.card} nx-fade`}>
                <h3>邮件通知</h3>
                <p className={styles.cardSub}>选择你希望通过邮件接收的通知类型。</p>
                <div className={styles.toggleList}>
                  {NOTIFY_ITEMS.map((it) => (
                    <div key={it.key} className={styles.toggleItem}>
                      <div>
                        <div className={styles.tiTitle}>{it.title}</div>
                        <div className={styles.tiDesc}>{it.desc}</div>
                      </div>
                      <Toggle checked={notify(it.key)} onChange={(v) => toggleNotify(it.key, v)} label={it.title} />
                    </div>
                  ))}
                </div>
                <div className={styles.formFoot}>
                  <button className="btn btn-primary" disabled={saveSetting.isPending} onClick={saveNotify}>
                    {saveSetting.isPending ? '保存中…' : '保存通知偏好'}
                  </button>
                </div>
              </div>
            </>
          )}

          {/* API 偏好 */}
          {tab === 'api' && (
            <>
              <div className={`${styles.card} nx-fade`}>
                <h3>默认请求设置</h3>
                <p className={styles.cardSub}>新建密钥与调用时的默认行为。</p>
                <div className={styles.fld}>
                  <label className="field-label">默认超时（秒）</label>
                  <input className="input mono-num" defaultValue={getNum('api_default_timeout', 60)} />
                  <div className="field-hint">单次请求最长等待时间，超时返回 504。</div>
                </div>
                <div className={styles.fld}>
                  <label className="field-label">失败自动重试次数</label>
                  <select className="input" style={{ cursor: 'pointer' }} defaultValue={String(getNum('api_retry', 1))}>
                    <option value="0">不重试</option>
                    <option value="1">1 次</option>
                    <option value="2">2 次</option>
                    <option value="3">3 次</option>
                  </select>
                </div>
                <div className={styles.toggleList}>
                  <div className={styles.toggleItem}>
                    <div>
                      <div className={styles.tiTitle}>流式响应优先</div>
                      <div className={styles.tiDesc}>支持的模型默认以 SSE 流式返回。</div>
                    </div>
                    <Toggle checked={getBool('api_stream_first', true)} label="流式响应优先" />
                  </div>
                  <div className={styles.toggleItem}>
                    <div>
                      <div className={styles.tiTitle}>记录请求日志</div>
                      <div className={styles.tiDesc}>保留请求与响应元数据用于排障（7 天）。</div>
                    </div>
                    <Toggle checked={getBool('api_log_request', true)} label="记录请求日志" />
                  </div>
                </div>
                <div className={styles.formFoot}>
                  <button className="btn btn-primary">保存设置</button>
                </div>
              </div>

              <div className={`${styles.card} nx-fade`}>
                <h3>我的分组与折扣</h3>
                <p className={styles.cardSub}>
                  分组只决定计费<b>折扣系数</b>，不限制可用模型；按累计充值自动升级。分组级映射在「我的模型映射」中配置。
                </p>
                <div className={styles.grpInline}>
                  <span className={styles.giBadge}>VIP</span>
                  <div className={styles.giBlock}>
                    <span className={styles.giK}>当前分组</span>
                    <span className={styles.giV}>VIP 会员</span>
                  </div>
                  <div className={styles.giBlock}>
                    <span className={styles.giK}>折扣系数</span>
                    <span className={`${styles.giV} ${styles.disc}`}>×0.85</span>
                  </div>
                  <div className={styles.giBlock}>
                    <span className={styles.giK}>升级门槛</span>
                    <span className={styles.giV}>累计充值 $2,000 → SVIP（×0.75）</span>
                  </div>
                  <div className={styles.giSpacer} />
                  <div className={styles.grpLinks}>
                    <a className="btn btn-sec btn-sm" href="/recharge">
                      升级分组
                    </a>
                    <a className="btn btn-ghost btn-sm" href="/keys">
                      查看密钥分组
                    </a>
                    <a className="btn btn-ghost btn-sm" href="/model-map">
                      分组级映射
                    </a>
                  </div>
                </div>
              </div>
            </>
          )}
        </div>
      </div>
    </ConsoleShell>
  );
}
