package com.nexa.application.ops.option;

import com.nexa.domain.ops.exception.InvalidOptionValueException;
import com.nexa.domain.ops.option.Option;
import com.nexa.domain.ops.option.OptionRegistry;
import com.nexa.domain.ops.option.OptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 全站选项更新用例（应用层，F-4018 PUT /api/option/，含 F-4032/F-4035 横切校验）。
 *
 * <p>编排：领域校验（{@link OptionRegistry#validate}，逐键规则）→ 覆盖式写入。校验逻辑全部在
 * 领域服务（充血），用例只调度并守事务边界（backend-engineer §2.1）。</p>
 *
 * <p>审计：写入成功后记 {@code option.update}，仅记 key 不记 value（F-4011/UL-5，敏感值不入审计）。
 * 本片审计埋点以日志承载（结构化），待审计 BC 统一收口（W5 验收审计完整性）。</p>
 */
@Service
public class UpdateOptionUseCase {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UpdateOptionUseCase.class);

    private final OptionRepository optionRepository;

    /**
     * @param optionRepository 选项仓储
     */
    public UpdateOptionUseCase(OptionRepository optionRepository) {
        this.optionRepository = optionRepository;
    }

    /**
     * 更新单个选项（领域校验 + 覆盖写）。
     *
     * @param key   配置键
     * @param value 配置值
     * @throws InvalidOptionValueException 校验不通过（→400，含主题/限流分组/合规键禁改）
     */
    @Transactional
    public void execute(String key, String value) {
        // ① 领域规则校验（主题白名单 / 限流分组结构 / 合规键禁改）。
        OptionRegistry.validate(key, value);
        // ② 覆盖式写入（幂等）。
        optionRepository.save(Option.of(key, value));
        // ③ 审计：仅记 key，绝不记 value（value 可能含敏感配置）。
        log.info("audit option.update key={}", key);
    }
}
