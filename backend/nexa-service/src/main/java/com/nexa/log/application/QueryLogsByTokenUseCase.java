package com.nexa.log.application;

import com.nexa.log.domain.exception.InvalidLogQueryException;
import com.nexa.log.domain.model.LogEntry;
import com.nexa.log.domain.repository.LogRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 按令牌 id 查消费日志用例（应用层，F-4003 GET /api/log/token，tokenReadAuth）。
 *
 * <p>用例编排：令牌鉴权层把请求 Authorization 头的明文 key 解析为 token_id 后调本用例。
 * {@code token_id == 0}（key 缺失/无效，鉴权层未解析出有效令牌）按契约抛 {@link InvalidLogQueryException}
 * 「无效的令牌」（接口层翻 400，对齐 openapi F-4003 400 分支）。有效 token_id 返回其消费日志数组。</p>
 *
 * <p>限流（CriticalRateLimit）+ 禁缓存（DisableCache）由接口层/网关横切处理，非本用例职责。
 * 返回口径为 UserLogView（接口层裁剪掉 B/成本/利润）——用令牌查自己的日志天然 self-scope 合法。</p>
 *
 * <p>领域规则来源：prd 日志与用量 F-4003「token_id==0 返回『无效的令牌』；有效 token_id 返回其日志数组」。</p>
 */
@Service
public class QueryLogsByTokenUseCase {

    private final LogRepository logRepository;

    /** @param logRepository 日志查询仓储 */
    public QueryLogsByTokenUseCase(LogRepository logRepository) {
        this.logRepository = logRepository;
    }

    /**
     * 按令牌 id 查消费日志（F-4003）。
     *
     * @param tokenId 令牌 id（由 tokenReadAuth 鉴权层解析；0=无效令牌）
     * @return 该令牌消费日志（新→旧；接口层裁剪为 UserLogView）
     * @throws InvalidLogQueryException token_id==0（无效的令牌）
     */
    public List<LogEntry> query(long tokenId) {
        if (tokenId == 0) {
            // 文案与现网一致（中文），接口层透传翻 400。
            throw new InvalidLogQueryException("无效的令牌");
        }
        return logRepository.findByTokenId(tokenId, 0);
    }
}
