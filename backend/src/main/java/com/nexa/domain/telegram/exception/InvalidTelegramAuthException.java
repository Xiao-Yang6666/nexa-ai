package com.nexa.domain.telegram.exception;

/**
 * Telegram 登录授权数据非法/校验失败异常（F-1051/F-1053）。
 *
 * <p>触发场景：Telegram Login Widget 回传的 {@code hash} 与服务端用 Bot Token 重算的
 * HMAC-SHA256 不一致（参数被篡改/伪造，F-1053 防伪铁律），或必填字段（id/hash/auth_date）
 * 缺失/格式非法，或授权时间戳超过有效期（重放防护）。接口层映射 400。</p>
 *
 * <p>领域规则来源：BACKLOG T-051/F-1051「hash 与 HMAC-SHA256(token, sorted params) 一致才登录」、
 * T-053/F-1053「篡改任一参数导致重算 hash 不等于传入 hash 时返回 false 拒绝」。
 * 出于安全（不给攻击者反馈具体哪一步失败），对外 message 统一稳定，不回显 hash/token 细节。</p>
 */
public class InvalidTelegramAuthException extends DomainException {

    /**
     * @param message 失败语义（稳定、不泄露 hash/token 细节）
     */
    public InvalidTelegramAuthException(String message) {
        super(message);
    }
}
