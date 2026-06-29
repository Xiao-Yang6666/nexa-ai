package com.nexa.growth.domain.exception;

import com.nexa.shared.kernel.HttpAwareDomainException;

/**
 * 增长子域持久化异常（DB 操作失败 / 唯一索引以外的数据访问错误）。
 *
 * <p>不吞错：底层数据访问异常 wrap 为本异常向上传，保留错误链（{@code cause}），由接口层翻译为
 * HTTP 500（不向客户端泄露底层细节）。注意：签到唯一索引冲突属<b>预期业务分支</b>，应转为
 * {@link AlreadyCheckedInException}（400）而非本异常。</p>
 */
public class GrowthPersistenceException extends HttpAwareDomainException {

    /** 稳定业务错误码。 */
    public static final String CODE = "GROWTH_PERSISTENCE_ERROR";

    /**
     * @param message 出错步骤描述（带上下文）
     * @param cause   根因（保留错误链）
     */
    public GrowthPersistenceException(String message, Throwable cause) {
        super(CODE, 500, message, cause);
    }
}
