package com.nexa.compliance.domain.vo;

import java.util.Objects;

/**
 * 数据分级（值对象，不可变，按值相等）——F-5016 数据分级登记，DC-001。
 *
 * <p>对系统中每个会落库/外传的字段做敏感度分级，用于决定加密、脱敏、出境、留存等横切策略
 * 的强度。领域规则来源：API-ENDPOINTS §14.5 F-5016「凭证/PII/内容/计量四级字段标注分级（DC-001）」。</p>
 *
 * <p>四级（敏感度从高到低）：
 * <ul>
 *   <li>{@link #CREDENTIAL} 凭证级——密码哈希、令牌 key、OAuth secret、2FA secret 等；最高敏感，
 *       必须字段加密（复用 shared {@code FieldEncryptor}）+ 绝不下发客户视图 + 注销时物理删除。</li>
 *   <li>{@link #PII} 个人身份信息——用户名、邮箱、第三方账号 id 等；注销时清空或匿名化（DC-003/DC-011）。</li>
 *   <li>{@link #CONTENT} 内容级——prompt/响应正文；默认不留存，开启留存须脱敏（F-5008/F-5017）。</li>
 *   <li>{@link #METERING} 计量级——用量/额度/请求计数等；可聚合保留用于计费与审计，敏感度最低。</li>
 * </ul></p>
 *
 * <p>把分级做成值对象（而非散落的枚举裸用）以便挂载「该级别应享有的横切策略」查询行为
 * （充血，backend-engineer §2.2），使「分级 → 策略」的映射集中、可单测。</p>
 */
public enum DataClassification {

    /** 凭证级：最高敏感，强制加密、绝不下发、注销物理删除。 */
    CREDENTIAL(4, "credential"),

    /** 个人身份信息级：注销时清空或匿名化。 */
    PII(3, "pii"),

    /** 内容级：prompt/响应正文，默认不留存，留存须脱敏。 */
    CONTENT(2, "content"),

    /** 计量级：用量/额度，敏感度最低，可聚合保留。 */
    METERING(1, "metering");

    private final int sensitivity;
    private final String code;

    DataClassification(int sensitivity, String code) {
        this.sensitivity = sensitivity;
        this.code = code;
    }

    /** @return 敏感度权重（越大越敏感），用于「至少 X 级才需 Y 策略」的比较 */
    public int sensitivity() {
        return sensitivity;
    }

    /** @return 稳定分级代码（小写，用于配置/数据字典登记） */
    public String code() {
        return code;
    }

    /**
     * 本级别字段是否「必须字段级加密落库」。
     *
     * <p>领域规则：仅凭证级（{@link #CREDENTIAL}）强制加密（DC-001 凭证最高敏感）。
     * 复用 shared {@code FieldEncryptor} 实施，本方法只回答「要不要加密」这一分级决策。</p>
     *
     * @return 需加密返回 {@code true}
     */
    public boolean requiresEncryptionAtRest() {
        return this == CREDENTIAL;
    }

    /**
     * 本级别字段是否「绝不可进客户视图 / 对外下发」。
     *
     * <p>领域规则：凭证级绝不下发（与可见性铁律一致——令牌明文/哈希/secret 不回显）。
     * PII/内容/计量是否下发由各自端点 DTO 按角色裁剪决定，不在此一刀切。</p>
     *
     * @return 绝不下发返回 {@code true}
     */
    public boolean isNeverDisclosed() {
        return this == CREDENTIAL;
    }

    /**
     * 账号注销时本级别字段的处置方式（F-5020 / DC-003 / DC-011）。
     *
     * <p>领域规则：
     * <ul>
     *   <li>凭证级 → {@link DisposalAction#PURGE} 物理删除（删令牌/绑定/2FA）；</li>
     *   <li>PII 级 → {@link DisposalAction#ANONYMIZE} 清空或匿名化（用户名/邮箱替换为不可逆占位）；</li>
     *   <li>内容级 → {@link DisposalAction#ANONYMIZE} 解除与用户的关联（日志 user_id 匿名化）；</li>
     *   <li>计量级 → {@link DisposalAction#RETAIN_AGGREGATED} 仅以聚合形式保留（计费/审计需要，已不含 PII）。</li>
     * </ul></p>
     *
     * @return 注销处置动作
     */
    public DisposalAction deactivationDisposal() {
        return switch (this) {
            case CREDENTIAL -> DisposalAction.PURGE;
            case PII, CONTENT -> DisposalAction.ANONYMIZE;
            case METERING -> DisposalAction.RETAIN_AGGREGATED;
        };
    }

    /**
     * 账号注销时对某分级数据的处置动作。
     */
    public enum DisposalAction {
        /** 物理删除（凭证级）。 */
        PURGE,
        /** 清空或匿名化（PII / 内容级，解除与自然人的关联）。 */
        ANONYMIZE,
        /** 以聚合形式保留（计量级，已不含可识别个人信息）。 */
        RETAIN_AGGREGATED
    }

    /**
     * 由分级代码解析（数据字典登记 / 配置读取用）。
     *
     * @param raw 分级代码（大小写不敏感，{@code credential/pii/content/metering}）
     * @return 对应分级
     * @throws IllegalArgumentException 代码非法（脏值不进领域）
     */
    public static DataClassification fromCode(String raw) {
        String c = Objects.requireNonNull(raw, "classification code").trim().toLowerCase();
        for (DataClassification dc : values()) {
            if (dc.code.equals(c)) {
                return dc;
            }
        }
        throw new IllegalArgumentException("unknown data classification code: " + raw);
    }
}
