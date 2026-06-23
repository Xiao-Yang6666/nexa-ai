'use client';

import { useEffect, useState } from 'react';
import { AppShell } from '@/features/shell';
import { Button } from '@/shared/ui';
import { ApiError } from '@/shared/api';
import { useSaveOption, useSysSettings, usdToQuotaStr, validateSysSettings } from '../model/system.model';
import styles from './SysSettingsPage.module.css';

/* ── 内联 SVG 图标 ── */
const IC = {
  shield: (
    <>
      <path d="M12 3l7 3v5c0 4.5-3 8-7 10-4-2-7-5.5-7-10V6z" />
      <path d="M9.5 12l1.8 1.8L15 10" />
    </>
  ),
  basic: (
    <>
      <rect x="3" y="3" width="18" height="18" rx="2" />
      <path d="M3 9h18" />
    </>
  ),
  auth: (
    <>
      <circle cx="9" cy="8" r="3.2" />
      <path d="M3.5 19a5.5 5.5 0 0 1 11 0" />
      <path d="M17 8v6M14 11h6" />
    </>
  ),
  billing: (
    <>
      <rect x="4" y="3" width="16" height="18" rx="2" />
      <path d="M8 7h8M8 12h.01M12 12h.01M16 12h.01" />
    </>
  ),
  mail: (
    <>
      <rect x="3" y="5" width="18" height="14" rx="2" />
      <path d="M4 7l8 6 8-6" />
    </>
  ),
  security: (
    <>
      <rect x="5" y="11" width="14" height="9" rx="2" />
      <path d="M8 11V8a4 4 0 0 1 8 0v3" />
    </>
  ),
  advanced: (
    <>
      <circle cx="12" cy="12" r="3" />
      <path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1" />
    </>
  ),
};

function Ic({ name, className }: { name: keyof typeof IC; className?: string }) {
  return (
    <svg
      className={className}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.6}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {IC[name]}
    </svg>
  );
}

/* ── Tab 定义 ── */
type PaneId = 'basic' | 'auth' | 'billing' | 'mail' | 'security' | 'advanced';

const TABS: { id: PaneId; icon: keyof typeof IC; label: string }[] = [
  { id: 'basic', icon: 'basic', label: '基础设置' },
  { id: 'auth', icon: 'auth', label: '注册与登录' },
  { id: 'billing', icon: 'billing', label: '计费与配额' },
  { id: 'mail', icon: 'mail', label: '邮件与通知' },
  { id: 'security', icon: 'security', label: '安全' },
  { id: 'advanced', icon: 'advanced', label: '高级' },
];

/* ── 开关行组件 ── */
function SwRow({
  title,
  desc,
  defaultOn,
  checked,
  onChange,
}: {
  title: string;
  desc: string;
  defaultOn?: boolean;
  /** 受控值（绑定真实选项时传入） */
  checked?: boolean;
  onChange?: (v: boolean) => void;
}) {
  const controlled = checked !== undefined;
  return (
    <div className={styles.swRow}>
      <div className={styles.swInfo}>
        <div className={styles.t}>{title}</div>
        <div className={styles.d}>{desc}</div>
      </div>
      <label className="switch">
        {controlled ? (
          <input type="checkbox" checked={checked} onChange={(e) => onChange?.(e.target.checked)} />
        ) : (
          <input type="checkbox" defaultChecked={defaultOn} />
        )}
        <span className="track" />
        <span className="thumb" />
      </label>
    </div>
  );
}

/* ── 页面组件 ── */
export function SysSettingsPage() {
  const [activePane, setActivePane] = useState<PaneId>('basic');
  const [saveHint, setSaveHint] = useState('所有改动将全站生效');

  const { data, isLoading, isError, error } = useSysSettings();
  const saveOption = useSaveOption();

  // 受控表单态（仅契约可确认键），加载完成后用真实选项回填
  const [systemName, setSystemName] = useState('');
  const [registerEnabled, setRegisterEnabled] = useState(false);
  const [emailVerification, setEmailVerification] = useState(false);
  const [githubOAuth, setGithubOAuth] = useState(false);
  const [newUserQuotaUsd, setNewUserQuotaUsd] = useState('');

  // R3-01 系统配置面板受控态（键名契约见 system.model）
  const [siteDescription, setSiteDescription] = useState('');
  const [inviteOnly, setInviteOnly] = useState(false);
  const [billingCurrency, setBillingCurrency] = useState('USD');
  const [defaultRpm, setDefaultRpm] = useState('');
  const [defaultTpm, setDefaultTpm] = useState('');
  const [smtpHost, setSmtpHost] = useState('');
  const [smtpPort, setSmtpPort] = useState('');
  const [smtpUsername, setSmtpUsername] = useState('');
  const [smtpPassword, setSmtpPassword] = useState('');
  const [smtpPasswordSet, setSmtpPasswordSet] = useState(false);
  const [smtpTls, setSmtpTls] = useState(false);
  const [force2fa, setForce2fa] = useState(false);
  const [lockoutThreshold, setLockoutThreshold] = useState('');
  const [sessionTtlMinutes, setSessionTtlMinutes] = useState('');
  const [ipWhitelist, setIpWhitelist] = useState('');
  const [maintenanceMode, setMaintenanceMode] = useState(false);
  const [debugLog, setDebugLog] = useState(false);
  const [requestTimeoutSec, setRequestTimeoutSec] = useState('');
  const [logRetentionDays, setLogRetentionDays] = useState('');

  useEffect(() => {
    if (!data) return;
    setSystemName(data.systemName);
    setRegisterEnabled(data.registerEnabled);
    setEmailVerification(data.emailVerification);
    setGithubOAuth(data.githubOAuth);
    setNewUserQuotaUsd(data.newUserQuotaUsd);

    setSiteDescription(data.siteDescription);
    setInviteOnly(data.inviteOnly);
    setBillingCurrency(data.billingCurrency);
    setDefaultRpm(data.defaultRpm);
    setDefaultTpm(data.defaultTpm);
    setSmtpHost(data.smtpHost);
    setSmtpPort(data.smtpPort);
    setSmtpUsername(data.smtpUsername);
    setSmtpPassword('');
    setSmtpPasswordSet(data.smtpPasswordSet);
    setSmtpTls(data.smtpTls);
    setForce2fa(data.force2fa);
    setLockoutThreshold(data.lockoutThreshold);
    setSessionTtlMinutes(data.sessionTtlMinutes);
    setIpWhitelist(data.ipWhitelist);
    setMaintenanceMode(data.maintenanceMode);
    setDebugLog(data.debugLog);
    setRequestTimeoutSec(data.requestTimeoutSec);
    setLogRetentionDays(data.logRetentionDays);
  }, [data]);

  const handleSave = async () => {
    // 前端校验（与后端 OptionRegistry 对齐），不通过则中止保存。
    const vmErr = validateSysSettings({
      systemName,
      registerEnabled,
      emailVerification,
      githubOAuth,
      themeFrontend: data?.themeFrontend ?? 'default',
      newUserQuotaUsd,
      siteDescription,
      inviteOnly,
      billingCurrency,
      defaultRpm,
      defaultTpm,
      smtpHost,
      smtpPort,
      smtpUsername,
      smtpPasswordSet,
      smtpTls,
      force2fa,
      lockoutThreshold,
      sessionTtlMinutes,
      ipWhitelist,
      maintenanceMode,
      debugLog,
      requestTimeoutSec,
      logRetentionDays,
    });
    if (vmErr) {
      setSaveHint(`保存失败：${vmErr}`);
      setTimeout(() => setSaveHint('所有改动将全站生效'), 2400);
      return;
    }

    setSaveHint('保存中…');
    try {
      // 逐键 PUT /api/option/。空字符串数值键跳过（视为未设置，不下发脏值）。
      const puts: Promise<unknown>[] = [
        saveOption.mutateAsync({ key: 'SystemName', value: systemName }),
        saveOption.mutateAsync({ key: 'RegisterEnabled', value: String(registerEnabled) }),
        saveOption.mutateAsync({ key: 'EmailVerificationEnabled', value: String(emailVerification) }),
        saveOption.mutateAsync({ key: 'GitHubOAuthEnabled', value: String(githubOAuth) }),
        saveOption.mutateAsync({ key: 'QuotaForNewUser', value: usdToQuotaStr(newUserQuotaUsd) }),
        // R3-01 键名契约
        saveOption.mutateAsync({ key: 'site.description', value: siteDescription }),
        saveOption.mutateAsync({ key: 'register.invite_only', value: String(inviteOnly) }),
        saveOption.mutateAsync({ key: 'billing.currency', value: billingCurrency }),
        saveOption.mutateAsync({ key: 'smtp.host', value: smtpHost }),
        saveOption.mutateAsync({ key: 'smtp.username', value: smtpUsername }),
        saveOption.mutateAsync({ key: 'smtp.tls', value: String(smtpTls) }),
        saveOption.mutateAsync({ key: 'security.force_2fa', value: String(force2fa) }),
        saveOption.mutateAsync({ key: 'security.ip_whitelist', value: ipWhitelist }),
        saveOption.mutateAsync({ key: 'advanced.maintenance_mode', value: String(maintenanceMode) }),
        saveOption.mutateAsync({ key: 'advanced.debug_log', value: String(debugLog) }),
      ];
      const pushIf = (key: string, value: string) => {
        if (value !== '') puts.push(saveOption.mutateAsync({ key, value }));
      };
      pushIf('ratelimit.default_rpm', defaultRpm);
      pushIf('ratelimit.default_tpm', defaultTpm);
      pushIf('smtp.port', smtpPort);
      pushIf('security.lockout_threshold', lockoutThreshold);
      pushIf('security.session_ttl_minutes', sessionTtlMinutes);
      pushIf('advanced.request_timeout_sec', requestTimeoutSec);
      pushIf('advanced.log_retention_days', logRetentionDays);
      // 密码仅在用户输入了新值时下发（敏感键，读取时已被剔除，不回显明文）。
      if (smtpPassword !== '') {
        puts.push(saveOption.mutateAsync({ key: 'smtp.passwordSecret', value: smtpPassword }));
      }
      await Promise.all(puts);
      setSaveHint('设置已保存');
    } catch (e) {
      setSaveHint(e instanceof ApiError ? `保存失败：${e.message}` : '保存失败，请重试');
    }
    setTimeout(() => setSaveHint('所有改动将全站生效'), 1600);
  };

  return (
    <AppShell
      activeId="sys"
      title="系统设置"
      crumb={['管理后台', '系统', '系统设置']}
    >
      {/* 超管横幅 */}
      <section className={`${styles.banner} nx-fade`}>
        <Ic name="shield" className={styles.nxIc} />
        <span className={styles.txt}>
          本页为 <b>超管专属</b> 区域，所有改动将全站生效，请谨慎操作。
        </span>
        <span className="badge b-info" style={{ marginLeft: 'auto' }}>超管专属</span>
      </section>

      {isError ? (
        <section className={`${styles.banner} nx-fade`}>
          <span className={styles.txt}>
            加载系统选项失败：{error instanceof ApiError ? error.message : '请稍后重试'}
            {error instanceof ApiError && error.status === 403 ? '（需 root 权限）' : ''}
          </span>
        </section>
      ) : null}

      <div className={styles.layout}>
        {/* 左侧子 Tab */}
        <nav className={`${styles.nav} nx-fade`}>
          {TABS.map((tab) => (
            <button
              key={tab.id}
              className={activePane === tab.id ? styles.on : undefined}
              onClick={() => setActivePane(tab.id)}
              type="button"
            >
              <Ic name={tab.icon} className={styles.nxIc} />
              {tab.label}
            </button>
          ))}
        </nav>

        {/* 右侧内容 */}
        <div>
          {/* 基础设置 */}
          <div className={`${styles.pane} ${activePane === 'basic' ? styles.on : ''}`}>
            <div className={styles.card}>
              <h3>站点信息</h3>
              <p className={styles.desc}>站点对外展示的基本信息。</p>
              <div className={styles.field}>
                <label className="field-label">站点名称</label>
                <input
                  className="input"
                  value={systemName}
                  onChange={(e) => setSystemName(e.target.value)}
                  placeholder={isLoading ? '加载中…' : 'SystemName'}
                />
              </div>
              <div className={styles.field}>
                <label className="field-label">站点描述</label>
                <input
                  className="input"
                  value={siteDescription}
                  maxLength={500}
                  onChange={(e) => setSiteDescription(e.target.value)}
                  placeholder={isLoading ? '加载中…' : '统一多模型 API 网关与计费平台'}
                />
              </div>
              <div className={styles.field}>
                <label className="field-label">站点 Logo</label>
                <div className={styles.logoBox}>
                  <span className={styles.logoSq}>N</span>
                  <Button variant="sec" size="sm">上传新 Logo</Button>
                </div>
                <div className="field-hint">推荐 PNG / SVG，建议 256x256 以内</div>
              </div>
              <div className={styles.field}>
                <label className="field-label">站点公告</label>
                <textarea
                  className={`input ${styles.textarea}`}
                  defaultValue="系统将于本周日 02:00–04:00 进行例行维护。"
                />
              </div>
            </div>
          </div>

          {/* 注册与登录 */}
          <div className={`${styles.pane} ${activePane === 'auth' ? styles.on : ''}`}>
            <div className={styles.card}>
              <h3>注册与登录</h3>
              <p className={styles.desc}>控制新用户注册与登录方式。</p>
              <SwRow title="开放注册" desc="关闭后仅管理员可创建账号" checked={registerEnabled} onChange={setRegisterEnabled} />
              <SwRow title="邮箱验证" desc="注册后需验证邮箱才能使用" checked={emailVerification} onChange={setEmailVerification} />
              <SwRow title="GitHub OAuth 登录" desc="允许使用 GitHub 账号登录" checked={githubOAuth} onChange={setGithubOAuth} />
              {/* 邀请码注册开关 → register.invite_only */}
              <SwRow title="邀请码注册" desc="注册必须填写有效邀请码" checked={inviteOnly} onChange={setInviteOnly} />
            </div>
          </div>

          {/* 计费与配额 */}
          <div className={`${styles.pane} ${activePane === 'billing' ? styles.on : ''}`}>
            <div className={styles.card}>
              <h3>计费与配额</h3>
              <p className={styles.desc}>新用户默认额度与限流策略。</p>
              <div className={styles.field2}>
                <div className={styles.field}>
                  <label className="field-label">默认新用户额度（美元）</label>
                  <input
                    className="input mono-num"
                    value={newUserQuotaUsd}
                    onChange={(e) => setNewUserQuotaUsd(e.target.value)}
                    placeholder={isLoading ? '加载中…' : '5.00'}
                  />
                </div>
                {/* 计费货币 → billing.currency（白名单 CNY/USD） */}
                <div className={styles.field}>
                  <label className="field-label">计费货币</label>
                  <select
                    className="input"
                    value={billingCurrency}
                    onChange={(e) => setBillingCurrency(e.target.value)}
                  >
                    <option value="USD">USD（美元）</option>
                    <option value="CNY">CNY（人民币）</option>
                  </select>
                </div>
              </div>
              {/* 默认 RPM/TPM → ratelimit.default_rpm / ratelimit.default_tpm */}
              <div className={styles.field2}>
                <div className={styles.field}>
                  <label className="field-label">默认 RPM 限制</label>
                  <input
                    className="input mono-num"
                    value={defaultRpm}
                    onChange={(e) => setDefaultRpm(e.target.value)}
                    placeholder={isLoading ? '加载中…' : '60'}
                  />
                </div>
                <div className={styles.field}>
                  <label className="field-label">默认 TPM 限制</label>
                  <input
                    className="input mono-num"
                    value={defaultTpm}
                    onChange={(e) => setDefaultTpm(e.target.value)}
                    placeholder={isLoading ? '加载中…' : '40000'}
                  />
                </div>
              </div>
              <SwRow title="额度耗尽自动停用" desc="余额为零时拒绝新请求" defaultOn />
            </div>
          </div>

          {/* 邮件与通知 → smtp.* 键名契约 */}
          <div className={`${styles.pane} ${activePane === 'mail' ? styles.on : ''}`}>
            <div className={styles.card}>
              <h3>SMTP 配置</h3>
              <p className={styles.desc}>用于发送验证邮件与系统通知。</p>
              <div className={styles.field2}>
                <div className={styles.field}>
                  <label className="field-label">SMTP 服务器</label>
                  <input
                    className="input mono-num"
                    value={smtpHost}
                    onChange={(e) => setSmtpHost(e.target.value)}
                    placeholder={isLoading ? '加载中…' : 'smtp.mailgun.org'}
                  />
                </div>
                <div className={styles.field}>
                  <label className="field-label">端口</label>
                  <input
                    className="input mono-num"
                    value={smtpPort}
                    onChange={(e) => setSmtpPort(e.target.value)}
                    placeholder={isLoading ? '加载中…' : '587'}
                  />
                </div>
              </div>
              <div className={styles.field2}>
                <div className={styles.field}>
                  <label className="field-label">发件账号</label>
                  <input
                    className="input mono-num"
                    value={smtpUsername}
                    onChange={(e) => setSmtpUsername(e.target.value)}
                    placeholder={isLoading ? '加载中…' : 'noreply@nexa.ai'}
                  />
                </div>
                <div className={styles.field}>
                  <label className="field-label">发件密码</label>
                  <input
                    className="input mono-num"
                    type="password"
                    value={smtpPassword}
                    onChange={(e) => setSmtpPassword(e.target.value)}
                    placeholder={smtpPasswordSet ? '已设置（留空则不修改）' : '未设置'}
                  />
                </div>
              </div>
              <SwRow title="启用 TLS" desc="使用加密连接发送邮件" checked={smtpTls} onChange={setSmtpTls} />
              <div className={styles.field} style={{ marginTop: 'var(--space-4)' }}>
                <Button variant="sec" size="sm">发送测试邮件</Button>
              </div>
            </div>
          </div>

          {/* 安全 → security.* 键名契约 */}
          <div className={`${styles.pane} ${activePane === 'security' ? styles.on : ''}`}>
            <div className={styles.card}>
              <h3>安全策略</h3>
              <p className={styles.desc}>账户与访问安全相关设置。</p>
              <SwRow title="强制双因素认证（2FA）" desc="管理员账户必须启用 2FA" checked={force2fa} onChange={setForce2fa} />
              <div className={styles.field2} style={{ marginTop: 'var(--space-4)' }}>
                <div className={styles.field}>
                  <label className="field-label">账号锁定阈值（失败次数，0 = 不锁定）</label>
                  <input
                    className="input mono-num"
                    value={lockoutThreshold}
                    onChange={(e) => setLockoutThreshold(e.target.value)}
                    placeholder={isLoading ? '加载中…' : '5'}
                  />
                </div>
                <div className={styles.field}>
                  <label className="field-label">会话有效期（分钟）</label>
                  <input
                    className="input mono-num"
                    value={sessionTtlMinutes}
                    onChange={(e) => setSessionTtlMinutes(e.target.value)}
                    placeholder={isLoading ? '加载中…' : '4320'}
                  />
                </div>
              </div>
              <div className={styles.field}>
                <label className="field-label">IP 白名单（管理后台访问）</label>
                <textarea
                  className={`input mono-num ${styles.textareaTall}`}
                  value={ipWhitelist}
                  onChange={(e) => setIpWhitelist(e.target.value)}
                  placeholder={isLoading ? '加载中…' : '10.0.0.0/8,118.24.6.0/24'}
                />
              </div>
            </div>
          </div>

          {/* 高级 → advanced.* 键名契约（缓存清理/重置统计为操作，非配置，保留 TODO） */}
          <div className={`${styles.pane} ${activePane === 'advanced' ? styles.on : ''}`}>
            <div className={styles.card}>
              <h3>高级选项</h3>
              <p className={styles.desc}>系统级运行参数，谨慎调整。</p>
              <SwRow title="维护模式" desc="开启后仅管理员可访问，普通用户看到维护页" checked={maintenanceMode} onChange={setMaintenanceMode} />
              <SwRow title="调试日志" desc="记录详细请求日志（会增加存储占用）" checked={debugLog} onChange={setDebugLog} />
              <div className={styles.field2} style={{ marginTop: 'var(--space-4)' }}>
                <div className={styles.field}>
                  <label className="field-label">请求超时（秒）</label>
                  <input
                    className="input mono-num"
                    value={requestTimeoutSec}
                    onChange={(e) => setRequestTimeoutSec(e.target.value)}
                    placeholder={isLoading ? '加载中…' : '120'}
                  />
                </div>
                <div className={styles.field}>
                  <label className="field-label">日志保留（天）</label>
                  <input
                    className="input mono-num"
                    value={logRetentionDays}
                    onChange={(e) => setLogRetentionDays(e.target.value)}
                    placeholder={isLoading ? '加载中…' : '90'}
                  />
                </div>
              </div>
              {/* TODO(api-missing): 缓存清理为操作（非配置键），需专用操作端点，本轮不做 */}
              <div className={styles.field}>
                <label className="field-label">缓存</label>
                <Button variant="sec" size="sm">清理缓存</Button>
                <div className="field-hint">当前缓存占用约 482 MB</div>
              </div>
            </div>

            {/* 危险操作区 */}
            <div className={styles.dangerZone}>
              <h3>危险操作</h3>
              {/* TODO(api-missing): 清空缓存 / 重置统计为操作（非配置键），需专用操作端点，本轮不做 */}
              <div className={styles.dangerItem}>
                <div className={styles.diInfo}>
                  <div className={styles.t}>清空全部缓存</div>
                  <div className={styles.d}>立即清除所有路由 / 模型 / 配额缓存</div>
                </div>
                <Button variant="danger" size="sm">清空缓存</Button>
              </div>
              <div className={styles.dangerItem}>
                <div className={styles.diInfo}>
                  <div className={styles.t}>重置统计数据</div>
                  <div className={styles.d}>清零全站请求 / 费用 / token 统计，不可恢复</div>
                </div>
                <Button variant="danger" size="sm">重置统计</Button>
              </div>
            </div>
          </div>

          {/* 保存栏 */}
          <div className={styles.saveBar}>
            <span className={styles.hint}>{saveHint}</span>
            <span className={styles.grow} />
            <Button variant="ghost">放弃更改</Button>
            <Button onClick={handleSave} disabled={saveOption.isPending || isLoading}>
              {saveOption.isPending ? '保存中…' : '保存设置'}
            </Button>
          </div>
        </div>
      </div>
    </AppShell>
  );
}
