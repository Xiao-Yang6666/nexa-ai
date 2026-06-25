/**
 * features/ranking — 模型用量排行榜域（公开，真实日志聚合，零泄露）。
 *
 * 本期仅「用量榜」（GET /api/rankings 真实数据）。多维榜单（综合/性价比/最快响应）
 * 与官方价对比涉及人工策划数据，本期暂不做——详见 ranking.model.ts 顶部 TODO(ranking-multidim)。
 */
export { RankingPage } from './components/RankingPage';
export { useRankings, toRankingRowVMs, PERIODS } from './model/ranking.model';
export type { RankingRowVM, RankingPeriod } from './model/ranking.model';
