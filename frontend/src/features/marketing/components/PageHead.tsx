import type { ReactNode } from 'react';
import styles from './PageHead.module.css';

export interface PageHeadProps {
  /** 顶部 pill 文案（带在线圆点），可选 */
  pill?: ReactNode;
  /** 主标题（支持把强调片段用 accent 包裹后传入） */
  title: ReactNode;
  /** 副标题/导语 */
  lead?: ReactNode;
}

/**
 * PageHead — 公开内容页统一页头（克制 aurora 辉光 + 细网格 + fade-up 入场）。
 *
 * 从 06_prototype/final/web-public 各内容页 .page-head 1:1 工程化抽取，
 * models / ranking 等内容页共用，保证页头视觉与动效一致。
 *
 * @example
 * <PageHead pill={<><span className={PageHead.live}/>实时在线</>} title={<>模型<Accent>广场</Accent></>} />
 */
export function PageHead({ pill, title, lead }: PageHeadProps) {
  return (
    <section className={styles.head}>
      <div className="wrap">
        {pill ? (
          <div className={styles.pill}>
            <span className={styles.live} />
            {pill}
          </div>
        ) : null}
        <h1 className={styles.title}>{title}</h1>
        {lead ? <p className={styles.lead}>{lead}</p> : null}
      </div>
    </section>
  );
}

/** 标题强调片段（teal→mint 渐变描字），与原型 .accent 一致。 */
export function Accent({ children }: { children: ReactNode }) {
  return <span className={styles.accent}>{children}</span>;
}
