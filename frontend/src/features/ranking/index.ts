/**
 * features/ranking — 模型排行榜域（纯展示，公开评估数据，零泄露）。
 */
export { RankingPage } from './components/RankingPage';
export {
  buildRankingModels,
  sortForBoard,
  BOARDS,
  fmtCalls,
  fmtPrice,
} from './model/ranking.model';
export type { BoardId, BoardConfig, RankingModelVM } from './model/ranking.model';
