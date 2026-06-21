'use client';

import { useMemo, useState } from 'react';
import Link from 'next/link';
import { usePricing, type PriceRowVM } from '../model/pricing.model';
import { MODES, PLANS, FAQ, type BillingMode } from '../model/pricing-content';
import styles from './PricingPage.module.css';

/* ── 线性图标（与原型 ico() 一致，stroke 用 currentColor，颜色走 token） ── */

function GaugeIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 14a2 2 0 1 0 0-4 2 2 0 0 0 0 4z" />
      <path d="m15 11 3-3" />
      <path d="M4.5 18a9 9 0 1 1 15 0" />
    </svg>
  );
}

function WalletIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M3 7a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v2" />
      <path d="M3 7v10a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-6a2 2 0 0 0-2-2H5" />
      <circle cx="16.5" cy="13" r="1" />
    </svg>
  );
}

function CheckIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" className={className}>
      <path d="m5 12 4.5 4.5L19 7" />
    </svg>
  );
}

function CrossIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" className={className}>
      <path d="M6 6l12 12M18 6 6 18" />
    </svg>
  );
}

function ChevronIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="m6 9 6 6 6-6" />
    </svg>
  );
}

function DiscIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M3.5 13.5 13 4l2 2-2.5 2.5 1.5 1.5-2 2-1.5-1.5L8 13l1.5 1.5-2 2L6 15l-2.5-1.5z" />
      <circle cx="17.5" cy="6.5" r="1.2" />
    </svg>
  );
}

/** 厂商点色（依模型名前缀给一个稳定的展示色，纯视觉，不涉供应商维度数据）。 */
function vendorDotColor(modelName: string): string {
  const n = modelName.toLowerCase();
  if (n.startsWith('gpt') || n.startsWith('o1') || n.startsWith('o3')) return '#10a37f';
  if (n.startsWith('claude')) return '#d97757';
  if (n.startsWith('gemini')) return '#4285f4';
  if (n.startsWith('deepseek')) return '#5b6bff';
  if (n.startsWith('qwen')) return '#7c3aed';
  return 'var(--hd-mint)';
}

/** 厂商展示名（从模型名前缀推导，仅用于价格表「厂商」列的公开展示）。 */
function vendorLabel(modelName: string): string {
  const n = modelName.toLowerCase();
  if (n.startsWith('gpt') || n.startsWith('o1') || n.startsWith('o3')) return 'OpenAI';
  if (n.startsWith('claude')) return 'Anthropic';
  if (n.startsWith('gemini')) return 'Google';
  if (n.startsWith('deepseek')) return 'DeepSeek';
  if (n.startsWith('qwen')) return 'Qwen';
  return '—';
}

/**
 * 由公开基准价 + 营销「省 X%」反推「官方价」用于对比展示。
 *
 * 零泄露口径：basePrice 是公开的 Nexa 直连基准价，savePercent 是基于
 * 公开价格带推导的营销标——二者都不涉及成本 B / 利润 / 供应商。
 * 官方价 = Nexa 价 / (1 - save%)，仅作划线对比的展示值（与原型同样为「示意」）。
 */
function deriveOfficialPrice(row: PriceRowVM): number | null {
  if (row.basePrice == null || row.savePercent == null) return null;
  const frac = 1 - row.savePercent / 100;
  if (frac <= 0) return null;
  return row.basePrice / frac;
}

/** 价格金额格式化（USD/1M，保留两位有效显示）。 */
function fmtPrice(v: number | null): string {
  if (v == null) return '—';
  if (v >= 100) return v.toFixed(0);
  if (v >= 10) return v.toFixed(1);
  return v.toFixed(2);
}

/**
 * PricingPage — 价格页（web-public/pricing.html 工程化）。
 *
 * 结构 1:1 还原原型：hero（含按量/预付费切换）→ 计费模式卡 → 价格对比表（接 /api/pricing）
 * → 套餐卡 → FAQ（手风琴）→ CTA。
 * 价格对比表走真实接口（PublicView，零泄露：不含成本 B/利润/供应商）；
 * 计费模式、套餐、FAQ 为公开营销内容，落 features/billing/model 常量。
 * loading（骨架行）/error/success 各态完备。
 */
export function PricingPage() {
  const [billing, setBilling] = useState<BillingMode>('usage');
  const [openFaq, setOpenFaq] = useState<number | null>(0);
  const { data, isLoading, isError, refetch } = usePricing();

  const rows = useMemo(() => data ?? [], [data]);

  // 对比表只展示有公开基准价的行，按价格从高到低（旗舰在前，与原型一致）
  const compareRows = useMemo(
    () =>
      rows
        .filter((r) => r.basePrice != null)
        .slice()
        .sort((a, b) => (b.basePrice ?? 0) - (a.basePrice ?? 0)),
    [rows],
  );

  return (
    <main className={styles.page}>
      {/* ── hero ── */}
      <section className={styles.hero}>
        <div className="wrap">
          <div className={styles.pill}>
            <span className={styles.live} />
            已聚合 <b>{rows.length > 0 ? rows.length : '148'}</b> 个模型 · 实时在线
          </div>
          <h1>
            满血直连，<span className={styles.accent}>只付零头</span>
          </h1>
          <p className={styles.lead}>
            没有月费陷阱，没有阶梯捆绑。按 token 实付实结，或预付额度享更低折扣——同一个
            base_url，全球大模型随用随切。
          </p>
          <div className={styles.billingToggle} role="tablist" aria-label="计费模式">
            <button
              type="button"
              role="tab"
              aria-selected={billing === 'usage'}
              className={billing === 'usage' ? styles.on : undefined}
              onClick={() => setBilling('usage')}
            >
              按量付费
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={billing === 'prepaid'}
              className={billing === 'prepaid' ? styles.on : undefined}
              onClick={() => setBilling('prepaid')}
            >
              预付费额度
            </button>
          </div>
        </div>
      </section>

      {/* ── 计费模式说明 ── */}
      <section className={styles.section}>
        <div className="wrap">
          <div className={styles.sectionHead}>
            <span className={styles.eyebrow}>计费模式</span>
            <h2>两种付费方式，按需选择</h2>
            <p>无论是临时调试还是规模化生产，都有适配的计费档位。</p>
          </div>
          <div className={styles.modes}>
            {MODES.map((m) => {
              const active =
                (billing === 'usage' && m.icon === 'gauge') ||
                (billing === 'prepaid' && m.icon === 'wallet');
              return (
                <div
                  key={m.title}
                  className={`${styles.modeCard}${active ? ` ${styles.modeActive}` : ''}`}
                >
                  <span className={styles.ico}>
                    {m.icon === 'gauge' ? <GaugeIcon /> : <WalletIcon />}
                  </span>
                  <span className={styles.tag}>{m.tag}</span>
                  <h3>{m.title}</h3>
                  <p>{m.desc}</p>
                  <ul>
                    {m.points.map((pt) => (
                      <li key={pt}>
                        <CheckIcon />
                        <span>{pt}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              );
            })}
          </div>
        </div>
      </section>

      {/* ── 价格对比表（接 /api/pricing） ── */}
      <section className={styles.section}>
        <div className="wrap">
          <div className={styles.sectionHead}>
            <span className={styles.eyebrow}>价格优势</span>
            <h2>与官方价对比，只付零头</h2>
            <p>主流模型每百万 token 单价（USD），Nexa 直连价对比厂商官方 API 定价。</p>
          </div>

          <div className={styles.compareWrap}>
            {isLoading ? (
              <div>
                {Array.from({ length: 6 }).map((_, i) => (
                  <div key={i} className={styles.skeletonRow} />
                ))}
              </div>
            ) : isError ? (
              <div className={styles.compareError}>
                <p>价格数据加载失败，请稍后重试。</p>
                <button
                  type="button"
                  className={`${styles.btn} ${styles.glass}`}
                  onClick={() => refetch()}
                >
                  重试
                </button>
              </div>
            ) : compareRows.length === 0 ? (
              <div className={styles.compareError}>
                <p>暂无可展示的价格数据，实时单价以控制台计费页为准。</p>
              </div>
            ) : (
              <table className={styles.compare}>
                <thead>
                  <tr>
                    <th>模型</th>
                    <th>厂商</th>
                    <th className={styles.num}>官方价 / 1M</th>
                    <th className={`${styles.num} ${styles.nexa}`}>Nexa 价 / 1M</th>
                    <th className={styles.num}>优惠</th>
                  </tr>
                </thead>
                <tbody>
                  {compareRows.map((r) => {
                    const official = deriveOfficialPrice(r);
                    return (
                      <tr key={r.modelName}>
                        <td>
                          <span className={styles.modelName}>
                            <span
                              className={styles.vendorDot}
                              style={{ background: vendorDotColor(r.modelName) }}
                            />
                            {r.displayName}
                          </span>
                        </td>
                        <td className={styles.muted}>{vendorLabel(r.modelName)}</td>
                        <td className={`${styles.num} ${styles.official}`}>
                          ${fmtPrice(official)}
                        </td>
                        <td className={`${styles.num} ${styles.nexa}`}>
                          ${fmtPrice(r.basePrice)}
                        </td>
                        <td className={styles.num}>
                          {r.savePercent != null ? (
                            <span className={styles.saveBadge}>省 {r.savePercent}%</span>
                          ) : (
                            '—'
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
          </div>
          <p className={styles.compareNote}>
            * 价格为示意，实时单价以控制台计费页为准。预付费额度满 $50 起再享 5%~15% 阶梯返点。
          </p>

          {/* 登录享折扣带 */}
          <div className={styles.loginDisc}>
            <span className={styles.ldIco}>
              <DiscIcon />
            </span>
            <div className={styles.ldTxt}>
              <p className={styles.ldT}>登录后享会员折扣</p>
              <p className={styles.ldS}>
                这里展示的是基准价。登录后按会员等级最高享 <b>85 折 / 7 折</b>{' '}
                优惠，调用越多档位越优。
              </p>
            </div>
            <div className={styles.ldCta}>
              <Link className={`${styles.btn} ${styles.glow}`} href="/register">
                立即注册
              </Link>
              <Link className={`${styles.btn} ${styles.glass}`} href="/login">
                登录看折后价
              </Link>
            </div>
          </div>
        </div>
      </section>

      {/* ── 套餐卡 ── */}
      <section className={styles.section}>
        <div className="wrap">
          <div className={styles.sectionHead}>
            <span className={styles.eyebrow}>套餐档位</span>
            <h2>从免费起步，按团队规模升级</h2>
            <p>所有套餐共享同一套 API 与全部模型，区别只在额度、并发与协作能力。</p>
          </div>
          <div className={styles.plans}>
            {PLANS.map((p) => (
              <div
                key={p.name}
                className={`${styles.planCard}${p.featured ? ` ${styles.planFeatured}` : ''}`}
              >
                {p.featured ? <span className={styles.planBadge}>最受欢迎</span> : null}
                <h3 className={styles.planName}>{p.name}</h3>
                <p className={styles.planTagline}>{p.tagline}</p>
                <div className={styles.planPrice}>
                  {p.amount === null ? (
                    <span className={styles.amt}>{p.amountText}</span>
                  ) : (
                    <>
                      <span className={styles.amt}>${p.amount}</span>
                      {p.per ? <span className={styles.per}>{p.per}</span> : null}
                    </>
                  )}
                </div>
                <p className={styles.planSub}>{p.sub}</p>
                <ul className={styles.planFeats}>
                  {p.feats.map((f) => (
                    <li key={f.text} className={f.ok ? undefined : styles.off}>
                      {f.ok ? (
                        <CheckIcon className={styles.yes} />
                      ) : (
                        <CrossIcon className={styles.no} />
                      )}
                      <span>{f.text}</span>
                    </li>
                  ))}
                </ul>
                <Link
                  className={`${styles.btn} ${styles.btnFull} ${
                    p.ctaVariant === 'glow' ? styles.glow : styles.glass
                  }`}
                  href={p.ctaHref}
                >
                  {p.ctaLabel}
                </Link>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ── FAQ ── */}
      <section className={styles.section}>
        <div className="wrap">
          <div className={styles.sectionHead}>
            <span className={styles.eyebrow}>常见问题</span>
            <h2>关于计费，你可能想知道</h2>
            <p>没找到答案？随时联系我们的支持团队。</p>
          </div>
          <div className={styles.faq}>
            {FAQ.map((f, i) => {
              const open = openFaq === i;
              return (
                <div
                  key={f.q}
                  className={`${styles.faqItem}${open ? ` ${styles.open}` : ''}`}
                >
                  <button
                    type="button"
                    className={styles.faqQ}
                    aria-expanded={open}
                    onClick={() => setOpenFaq(open ? null : i)}
                  >
                    <span>{f.q}</span>
                    <ChevronIcon />
                  </button>
                  <div className={styles.faqA}>
                    <p>{f.a}</p>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </section>

      {/* ── CTA ── */}
      <section className={styles.ctaBand}>
        <div className="wrap">
          <div className={styles.ctaInner}>
            <h2>现在注册，立即拿到测试额度</h2>
            <p>新用户注册即送 $1 免费额度，无需绑卡，一行代码接入全球大模型。</p>
            <div className={styles.ctaActions}>
              <Link className={`${styles.btn} ${styles.glow}`} href="/register">
                免费开始
              </Link>
              <Link className={`${styles.btn} ${styles.glass}`} href="/docs">
                查看接入文档
              </Link>
            </div>
          </div>
        </div>
      </section>
    </main>
  );
}
