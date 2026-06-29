package com.nexa.domain.routing.vo;

import com.nexa.domain.routing.exception.InvalidAffinityParameterException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 亲和缓存复合键值对象（不可变，F-2029/F-2032/F-2033）。
 *
 * <p>领域规则来源：FC-068。会话键→渠道映射的缓存键由三元组确定：
 * <ul>
 *   <li>{@code ruleName} —— 命中规则名（不同规则的会话键互不混用）。</li>
 *   <li>{@code rawKey} —— 规则提取出的原始会话键（可能含 PII，如 user_id；缓存内不直接落明文，
 *       用 {@link #fingerprint()} 做指纹）。</li>
 *   <li>{@code usingGroup} —— 当前 token 使用分组（auto 模式下不同分组各自维护粘连，避免互相污染）。</li>
 * </ul>
 * F-2033 用量统计接口入参 {@code rule_name + key_fp + using_group} 直接对应本三元组的指纹形态——
 * 客户端用指纹查询，不需要传明文会话键。</p>
 *
 * <p>fingerprint() 返回 SHA-256 hex 串（前 16 字节），稳定可比对、可索引、不可还原明文。</p>
 *
 * @param ruleName   命中规则名（非空）
 * @param rawKey     原始会话键明文（非空）
 * @param usingGroup 当前 token 使用分组（可空——为 null 时缓存键中省略此维度）
 */
public record AffinityCacheKey(String ruleName, String rawKey, String usingGroup) {

    /**
     * 紧凑构造器：归一与不变量。
     *
     * @throws InvalidAffinityParameterException ruleName/rawKey 空白
     */
    public AffinityCacheKey {
        if (ruleName == null || ruleName.isBlank()) {
            throw new InvalidAffinityParameterException("rule_name is required");
        }
        if (rawKey == null || rawKey.isBlank()) {
            throw new InvalidAffinityParameterException("raw_key is required");
        }
        ruleName = ruleName.trim();
        rawKey = rawKey.trim();
        usingGroup = (usingGroup == null || usingGroup.isBlank()) ? null : usingGroup.trim();
    }

    /**
     * 计算原始会话键的指纹（SHA-256 hex 前 16 字节，32 hex 字符）。
     *
     * <p>指纹用于：① 缓存内部存储——避免 PII 明文落 DB；② F-2033 用量统计接口——客户端可在前端
     * 计算同算法指纹后查询，不下发明文 user_id。</p>
     *
     * @return 32 字符 hex 指纹
     */
    public String fingerprint() {
        return sha256Hex(rawKey);
    }

    /**
     * 由指纹形态构造（F-2033 用量统计入参 {@code key_fp} 直接传指纹时用本工厂）。
     *
     * <p>注意：本形态构造的 key 不可还原原始 rawKey；调用方只能用于查询/匹配，不能用于缓存写入。</p>
     *
     * @param ruleName    规则名
     * @param fingerprint 32 字符 hex 指纹
     * @param usingGroup  使用分组（可空）
     * @return 仅含指纹的查询用 key
     */
    public static AffinityCacheKey ofFingerprint(String ruleName, String fingerprint, String usingGroup) {
        if (fingerprint == null || fingerprint.isBlank()) {
            throw new InvalidAffinityParameterException("key_fp is required");
        }
        return new AffinityCacheKey(ruleName, fingerprint.trim(), usingGroup);
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] full = md.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] truncated = new byte[16];
            System.arraycopy(full, 0, truncated, 0, 16);
            return HexFormat.of().formatHex(truncated);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 标配，理论上不会触发；wrap 带上下文不裸 throw（backend-engineer §3.2）。
            throw new IllegalStateException("SHA-256 not available in JDK", e);
        }
    }
}
