package com.nexa.domain.telegram.vo;

import com.nexa.domain.telegram.exception.InvalidTelegramAuthException;

/**
 * Telegram 用户 id 值对象（不可变、按值相等）。
 *
 * <p>对齐 DB-SCHEMA §1 User {@code telegram_id}（索引列，字符串）与 openapi
 * {@code /api/oauth/telegram/login} 的 {@code id} 查询参数。Telegram 的用户 id 是一个数字串，
 * 这里以字符串承载（与 DB 列类型一致，避免大数溢出与前导歧义），但要求其为<b>纯数字、非空</b>
 * （Login Widget 回传的 {@code id} 字段恒为数字）。</p>
 *
 * <p>值对象封装校验不变量（backend-engineer §2.4）：构造即保证合法，杜绝非法 telegram_id
 * 流入绑定/登录。非法输入抛 {@link InvalidTelegramAuthException}（接口层映射 400）。</p>
 *
 * @param value 规范化后的 Telegram 数字用户 id 串
 */
public record TelegramId(String value) {

    /** telegram_id 最大长度（Telegram 用户 id 远小于此，留足冗余且对齐索引列容量）。 */
    public static final int MAX_LENGTH = 64;

    /**
     * 紧凑构造器：强制非空、纯数字、不超长。
     *
     * @throws InvalidTelegramAuthException 当为空白、含非数字字符或超长时
     */
    public TelegramId {
        // 缺失 id 是非法授权回调（Login Widget 必带 id），归一为领域异常（接口层映射 400），
        // 不抛 NPE（否则接口层映射 500，掩盖「客户端伪造/参数缺失」的真实语义）。
        if (value == null || value.isBlank()) {
            throw new InvalidTelegramAuthException("telegram id must not be blank");
        }
        // Telegram Login Widget 的 id 恒为正整数串；非数字即视为伪造/篡改，拒绝。
        if (!value.chars().allMatch(Character::isDigit)) {
            throw new InvalidTelegramAuthException("telegram id must be numeric");
        }
        if (value.length() > MAX_LENGTH) {
            throw new InvalidTelegramAuthException("telegram id length must be <= " + MAX_LENGTH);
        }
    }

    /**
     * 由原始串构造（trim 后校验）。
     *
     * @param raw 原始 telegram id 串（来自 query 参数 / 落库值）
     * @return 校验后的值对象
     * @throws InvalidTelegramAuthException 当非法时
     */
    public static TelegramId of(String raw) {
        return new TelegramId(raw == null ? null : raw.trim());
    }
}
