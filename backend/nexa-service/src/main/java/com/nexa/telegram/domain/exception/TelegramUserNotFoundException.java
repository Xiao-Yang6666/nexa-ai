package com.nexa.telegram.domain.exception;

/**
 * Telegram 绑定归属用户缺失异常（数据一致性防御）。
 *
 * <p>触发场景：据 {@code telegram_id} 命中绑定但绑定归属的本站用户已不存在（被软删但绑定残留），
 * 或 Telegram 绑定/登录流程引用的会话用户 id 在用户仓储查无此人。接口层映射 400/404
 * （登录路径按凭证非法 400 语义、绑定路径按目标不存在 404 语义）。</p>
 */
public class TelegramUserNotFoundException extends DomainException {

    /**
     * @param message 含定位上下文的描述（不泄露其它账号信息）
     */
    public TelegramUserNotFoundException(String message) {
        super(message);
    }
}
