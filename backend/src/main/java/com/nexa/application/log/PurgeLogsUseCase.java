package com.nexa.application.log;

import com.nexa.domain.log.exception.InvalidLogQueryException;
import com.nexa.domain.log.repository.LogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 清理历史日志用例（应用层，F-4006 DELETE /api/log/）。
 *
 * <p>用例编排：校验 {@code target_timestamp}（0 抛「target timestamp is required」，对齐契约 400），
 * 委托仓储分批删除（每批上限 100，循环至无更多），返回删除总数。事务边界在本用例（删除属写操作）。</p>
 *
 * <p>领域规则来源：prd 日志与用量 F-4006「target_timestamp 为 0 返回『target timestamp is required』；
 * 成功返回删除数；按目标时间戳分批删除，每批上限 100」。鉴权 AdminAuth 由接口层拦截。</p>
 */
@Service
public class PurgeLogsUseCase {

    /** 单批删除上限（F-4006 契约：每批 ≤ 100）。 */
    private static final int BATCH_SIZE = 100;

    private final LogRepository logRepository;

    /** @param logRepository 日志查询仓储 */
    public PurgeLogsUseCase(LogRepository logRepository) {
        this.logRepository = logRepository;
    }

    /**
     * 清理早于目标时间的历史日志（F-4006）。
     *
     * @param targetTimestamp 删除 created_at &lt; 该值的日志（epoch 秒；0 非法）
     * @return 实际删除条数
     * @throws InvalidLogQueryException targetTimestamp == 0（target timestamp is required）
     */
    @Transactional
    public int purge(long targetTimestamp) {
        if (targetTimestamp == 0) {
            // 文案与契约一致，接口层透传翻 400。
            throw new InvalidLogQueryException("target timestamp is required");
        }
        return logRepository.purgeOlderThan(targetTimestamp, BATCH_SIZE);
    }
}
