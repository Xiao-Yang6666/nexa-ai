package com.nexa.domain.token.vo;

/**
 * 令牌明文 key 脱敏器值对象（无状态工具值对象，安全护栏，F-3002/F-3004）。
 *
 * <p>领域规则来源：openapi TokenUserView「key 字段经 MaskTokenKey 脱敏（列表/创建/更新场景）」+
 * 客户视图铁律。令牌明文 key 是凭证，列表/搜索/创建/更新等批量场景一律返回脱敏值（保留头尾、
 * 中段以 {@code ***} 替换），仅 F-3004/F-3005 受控取明文端点（限本人令牌）才下发完整 key。</p>
 *
 * <p>脱敏策略：完整 key 形如 {@code sk-<random>}；保留前缀（含 {@code sk-} 与首段）与末 4 位，
 * 中间用 {@code ***} 占位；过短（≤8）的 key 全部以 {@code ***} 替换，避免暴露足够还原的片段。</p>
 */
public final class TokenKey {

    /** 脱敏占位符。 */
    private static final String MASK = "***";

    /** 头部保留长度（含 sk- 前缀范围）。 */
    private static final int HEAD_KEEP = 6;

    /** 尾部保留长度。 */
    private static final int TAIL_KEEP = 4;

    /** 触发头尾保留脱敏的最短长度（否则整体 ***）。 */
    private static final int MIN_MASKABLE_LENGTH = HEAD_KEEP + TAIL_KEEP + 1;

    private TokenKey() {
        // 工具值对象，禁实例化。
    }

    /**
     * 对令牌明文 key 脱敏（MaskTokenKey）。
     *
     * <p>null/空 key 返回空串（无凭证可脱敏）；过短 key 整体替换为 {@code ***}；否则保留头 6 尾 4，
     * 中段 {@code ***}。本方法只读不改原值，输出绝不可逆推原 key。</p>
     *
     * @param plain 完整明文 key（可空）
     * @return 脱敏后的可下发 key
     */
    public static String mask(String plain) {
        if (plain == null || plain.isEmpty()) {
            return "";
        }
        if (plain.length() < MIN_MASKABLE_LENGTH) {
            return MASK;
        }
        String head = plain.substring(0, HEAD_KEEP);
        String tail = plain.substring(plain.length() - TAIL_KEEP);
        return head + MASK + tail;
    }
}
