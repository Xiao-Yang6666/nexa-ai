package com.nexa.domain.compliance.vo;

import java.util.List;

/**
 * 数据分级登记（设计约束标注 + 字段清单，F-5016 数据分级登记，DC-001）。
 *
 * <p>把「凭证/PII/内容/计量四级字段标注分级」这一数据字典登记<b>固化进代码库</b>，作为合规审计与
 * 横切策略（加密/脱敏/出境/留存/注销处置）的权威依据。领域规则来源：BACKLOG T-220 F-5016 验收
 * 「字段清单含分级标注」、API-ENDPOINTS §14.5 F-5016（数据字典，Root 维护，无独立端点）。</p>
 *
 * <p>各字段的处置策略不在此重复定义——分级 → 策略的映射由 {@link DataClassification} 的充血方法给出
 * （{@code requiresEncryptionAtRest}/{@code isNeverDisclosed}/{@code deactivationDisposal}）。
 * 本类只负责「哪些字段属于哪一级」的登记清单。</p>
 */
public final class DataClassificationRegistry {

    private DataClassificationRegistry() {
    }

    /**
     * 一条字段分级登记项。
     *
     * @param table          所属表（对齐 DB-SCHEMA）
     * @param field          字段名
     * @param classification 数据分级
     * @param note           备注（处置要点 / PRD 出处）
     */
    public record FieldEntry(String table, String field, DataClassification classification, String note) {
    }

    /**
     * 核心 PII/凭证字段的分级登记清单（节选权威项；完整字典由 Root 在数据字典文档维护）。
     *
     * <p>覆盖最敏感的凭证级与 PII 级字段——这些直接决定加密落库（凭证级）与注销处置（PII 匿名化、
     * 凭证级物理删除）。内容级（日志正文）与计量级（用量）数量大、规则统一，按级处置不逐字段列举。</p>
     *
     * @return 不可变登记清单
     */
    public static List<FieldEntry> coreEntries() {
        return List.of(
                // ---- 凭证级（CREDENTIAL）：强制加密落库 / 绝不下发 / 注销物理删除 ----
                new FieldEntry("users", "password", DataClassification.CREDENTIAL,
                        "密码哈希；注销置不可登录占位（见 User.anonymize）"),
                new FieldEntry("tokens", "key", DataClassification.CREDENTIAL,
                        "API 令牌明文 key；注销级联软删（见 AccountDeactivationCascade）"),
                new FieldEntry("custom_oauth_providers", "client_secret", DataClassification.CREDENTIAL,
                        "第三方 OAuth secret；字段加密（shared FieldEncryptor）"),
                new FieldEntry("two_fas", "secret", DataClassification.CREDENTIAL,
                        "TOTP secret；注销删除（twofa BC 仓储落地后接级联）"),
                new FieldEntry("passkey_credentials", "public_key", DataClassification.CREDENTIAL,
                        "WebAuthn 公钥凭据；注销 deleteByUserId"),

                // ---- PII 级（PII）：注销清空或匿名化 ----
                new FieldEntry("users", "username", DataClassification.PII,
                        "用户名；注销替换为 deleted_<id> 不可逆占位"),
                new FieldEntry("users", "email", DataClassification.PII,
                        "邮箱；注销清空"),
                new FieldEntry("users", "display_name", DataClassification.PII,
                        "展示名；注销清空"),
                new FieldEntry("user_oauth_bindings", "provider_user_id", DataClassification.PII,
                        "第三方账号 id；注销级联删除绑定"),
                new FieldEntry("telegram_bindings", "telegram_id", DataClassification.PII,
                        "Telegram 账号 id；随用户注销解除"),

                // ---- 内容级（CONTENT）：默认不留存，开启留存须脱敏 ----
                new FieldEntry("logs", "content", DataClassification.CONTENT,
                        "请求/响应正文相关；默认不留（PromptRetentionPolicy 控），注销匿名化归属"),

                // ---- 计量级（METERING）：聚合保留（注销保留，已不含 PII） ----
                new FieldEntry("logs", "quota", DataClassification.METERING,
                        "消费额度；聚合计费/审计保留"),
                new FieldEntry("users", "used_quota", DataClassification.METERING,
                        "已用额度；注销保留聚合计量")
        );
    }
}
