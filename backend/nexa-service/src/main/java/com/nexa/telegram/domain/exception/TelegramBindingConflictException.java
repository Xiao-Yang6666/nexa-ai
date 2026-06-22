package com.nexa.telegram.domain.exception;

/**
 * Telegram 绑定唯一性冲突异常（F-1054）。
 *
 * <p>触发场景：目标 {@code telegram_id} 已被<b>其他</b>本站账号绑定时，当前账号再请求绑定同一
 * Telegram 账户即冲突——「一个 Telegram 账户只能绑定一个本站账号」（BACKLOG T-054/F-1054
 * 「目标 telegram_id 已被其他账号绑定时返回该 Telegram 账户已被绑定，阻止绑定」）。接口层映射 409。</p>
 *
 * <p>对齐 {@code com.nexa.account.OAuthBindingConflictException} 的语义与映射（每 provider 一账号唯一），
 * 但 Telegram 走独立 HMAC 路径、独立绑定表，故置于本子域。对外 message 不回显占用方 userId（防枚举）。</p>
 */
public class TelegramBindingConflictException extends DomainException {

    /**
     * 构造一个面向用户的稳定冲突提示（不含占用方账号细节）。
     */
    public TelegramBindingConflictException() {
        super("该 Telegram 账户已被绑定");
    }
}
