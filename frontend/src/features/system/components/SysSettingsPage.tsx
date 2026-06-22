'use client';

import { useState } from 'react';
import { AppShell } from '@/features/shell';
import { Button } from '@/shared/ui';
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
function SwRow({ title, desc, defaultOn }: { title: string; desc: string; defaultOn?: boolean }) {
  return (
    <div className={styles.swRow}>
      <div className={styles.swInfo}>
        <div className={styles.t}>{title}</div>
        <div className={styles.d}>{desc}</div>
      </div>
      <label className="switch">
        <input type="checkbox" defaultChecked={defaultOn} />
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

  const handleSave = () => {
    setSaveHint('设置已保存');
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
                <input className="input" defaultValue="Nexa·AI 网关" />
              </div>
              <div className={styles.field}>
                <label className="field-label">站点描述</label>
                <input className="input" defaultValue="统一多模型 API 网关与计费平台" />
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
              <SwRow title="开放注册" desc="关闭后仅管理员可创建账号" defaultOn />
              <SwRow title="邮箱验证" desc="注册后需验证邮箱才能使用" defaultOn />
              <SwRow title="GitHub OAuth 登录" desc="允许使用 GitHub 账号登录" defaultOn />
              <SwRow title="邀请码注册" desc="注册必须填写有效邀请码" />
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
                  <input className="input mono-num" defaultValue="5.00" />
                </div>
                <div className={styles.field}>
                  <label className="field-label">计费货币</label>
                  <select className="input" defaultValue="USD（美元）">
                    <option>USD（美元）</option>
                    <option>CNY（人民币）</option>
                  </select>
                </div>
              </div>
              <div className={styles.field2}>
                <div className={styles.field}>
                  <label className="field-label">默认 RPM 限制</label>
                  <input className="input mono-num" defaultValue="60" />
                </div>
                <div className={styles.field}>
                  <label className="field-label">默认 TPM 限制</label>
                  <input className="input mono-num" defaultValue="40000" />
                </div>
              </div>
              <SwRow title="额度耗尽自动停用" desc="余额为零时拒绝新请求" defaultOn />
            </div>
          </div>

          {/* 邮件与通知 */}
          <div className={`${styles.pane} ${activePane === 'mail' ? styles.on : ''}`}>
            <div className={styles.card}>
              <h3>SMTP 配置</h3>
              <p className={styles.desc}>用于发送验证邮件与系统通知。</p>
              <div className={styles.field2}>
                <div className={styles.field}>
                  <label className="field-label">SMTP 服务器</label>
                  <input className="input mono-num" defaultValue="smtp.mailgun.org" />
                </div>
                <div className={styles.field}>
                  <label className="field-label">端口</label>
                  <input className="input mono-num" defaultValue="587" />
                </div>
              </div>
              <div className={styles.field2}>
                <div className={styles.field}>
                  <label className="field-label">发件账号</label>
                  <input className="input mono-num" defaultValue="noreply@nexa.ai" />
                </div>
                <div className={styles.field}>
                  <label className="field-label">发件密码</label>
                  <input className="input mono-num" type="password" defaultValue="************" />
                </div>
              </div>
              <div className={styles.field}>
                <label className="field-label">发件人显示名</label>
                <input className="input" defaultValue="Nexa·AI 平台" />
              </div>
              <SwRow title="启用 TLS" desc="使用加密连接发送邮件" defaultOn />
              <div className={styles.field} style={{ marginTop: 'var(--space-4)' }}>
                <Button variant="sec" size="sm">发送测试邮件</Button>
              </div>
            </div>
          </div>

          {/* 安全 */}
          <div className={`${styles.pane} ${activePane === 'security' ? styles.on : ''}`}>
            <div className={styles.card}>
              <h3>安全策略</h3>
              <p className={styles.desc}>账户与访问安全相关设置。</p>
              <SwRow title="强制双因素认证（2FA）" desc="管理员账户必须启用 2FA" defaultOn />
              <SwRow title="登录失败锁定" desc="连续失败 5 次锁定 15 分钟" defaultOn />
              <div className={styles.field2} style={{ marginTop: 'var(--space-4)' }}>
                <div className={styles.field}>
                  <label className="field-label">会话有效期（小时）</label>
                  <input className="input mono-num" defaultValue="72" />
                </div>
                <div className={styles.field}>
                  <label className="field-label">API Key 最大数 / 用户</label>
                  <input className="input mono-num" defaultValue="10" />
                </div>
              </div>
              <div className={styles.field}>
                <label className="field-label">IP 白名单（管理后台访问）</label>
                <textarea
                  className={`input mono-num ${styles.textareaTall}`}
                  defaultValue={'10.0.0.0/8\n118.24.6.0/24'}
                />
              </div>
            </div>
          </div>

          {/* 高级 */}
          <div className={`${styles.pane} ${activePane === 'advanced' ? styles.on : ''}`}>
            <div className={styles.card}>
              <h3>高级选项</h3>
              <p className={styles.desc}>系统级运行参数，谨慎调整。</p>
              <SwRow title="维护模式" desc="开启后仅管理员可访问，普通用户看到维护页" />
              <SwRow title="调试日志" desc="记录详细请求日志（会增加存储占用）" />
              <div className={styles.field2} style={{ marginTop: 'var(--space-4)' }}>
                <div className={styles.field}>
                  <label className="field-label">请求超时（秒）</label>
                  <input className="input mono-num" defaultValue="120" />
                </div>
                <div className={styles.field}>
                  <label className="field-label">日志保留（天）</label>
                  <input className="input mono-num" defaultValue="90" />
                </div>
              </div>
              <div className={styles.field}>
                <label className="field-label">缓存</label>
                <Button variant="sec" size="sm">清理缓存</Button>
                <div className="field-hint">当前缓存占用约 482 MB</div>
              </div>
            </div>

            {/* 危险操作区 */}
            <div className={styles.dangerZone}>
              <h3>危险操作</h3>
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
            <Button onClick={handleSave}>保存设置</Button>
          </div>
        </div>
      </div>
    </AppShell>
  );
}
