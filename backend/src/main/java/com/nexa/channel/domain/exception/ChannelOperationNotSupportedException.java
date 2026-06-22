package com.nexa.channel.domain.exception;

import com.nexa.shared.kernel.DomainException;

/**
 * 渠道操作不被支持异常（如对非 Ollama 渠道执行 Ollama 管理、对异步渠道执行同步连通性测试，→400）。
 *
 * <p>领域规则来源：
 * <ul>
 *   <li>F-2017：七类异步渠道（MJ/Suno/Kling/Jimeng/DoubaoVideo/Vidu 等）不支持同步连通性测试。</li>
 *   <li>F-2027：Ollama 模型拉取/删除/版本查询仅适用于 Ollama 类型渠道。</li>
 * </ul>
 * 由聚合根充血护栏方法抛出（{@code ensureSyncTestable}/{@code ensureOllama}），
 * 接口层翻译为 400 BadRequest。</p>
 */
public class ChannelOperationNotSupportedException extends DomainException {

    /** @param message 不支持原因（契约文案） */
    public ChannelOperationNotSupportedException(String message) {
        super("CHANNEL_OPERATION_NOT_SUPPORTED", message);
    }
}
