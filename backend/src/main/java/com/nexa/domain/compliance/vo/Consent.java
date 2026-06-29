package com.nexa.domain.compliance.vo;

import java.util.Objects;

/**
 * 用户对「含数据出境与留存条款的协议」的同意记录（值对象，不可变，按值相等）——F-5021，DC-010。
 *
 * <p>承载「用户已接受的条款版本 + 接受时刻」。同意闸门据此与当前生效版本比对，判定该用户是否
 * 仍处于「已同意」状态（条款版本升级后旧同意失效，需重新接受）。领域规则来源：
 * API-ENDPOINTS §14.5 F-5021「未接受含出境与留存条款的协议不可调用」。</p>
 *
 * @param acceptedTermsVersion 用户已接受的条款版本号（如 {@code "2026-06-01"}）；{@code null}/空白表示从未同意
 * @param acceptedAtEpochSec   接受时刻 epoch 秒（0 表示从未同意）
 */
public record Consent(String acceptedTermsVersion, long acceptedAtEpochSec) {

    /** 从未同意的空记录单例语义入口。 */
    public static Consent none() {
        return new Consent(null, 0L);
    }

    /**
     * 构造一条有效同意记录。
     *
     * @param termsVersion     接受的条款版本（非空白）
     * @param acceptedAtEpochSec 接受时刻 epoch 秒（须 &gt; 0）
     * @return 同意记录
     * @throws IllegalArgumentException 版本空白或时刻非正
     */
    public static Consent accepted(String termsVersion, long acceptedAtEpochSec) {
        if (termsVersion == null || termsVersion.isBlank()) {
            throw new IllegalArgumentException("accepted terms version must not be blank");
        }
        if (acceptedAtEpochSec <= 0) {
            throw new IllegalArgumentException("accepted timestamp must be positive");
        }
        return new Consent(termsVersion.trim(), acceptedAtEpochSec);
    }

    /** @return 是否曾经同意过任何版本 */
    public boolean hasConsented() {
        return acceptedTermsVersion != null && !acceptedTermsVersion.isBlank();
    }

    /**
     * 判断本同意记录相对「当前生效版本」是否仍然有效。
     *
     * <p>领域规则：必须曾同意，且所同意的版本 == 当前生效版本（区分大小写、精确匹配）。
     * 版本不一致表示条款已更新，旧同意失效，需重新接受（DC-010 条款变更需再同意）。</p>
     *
     * @param currentTermsVersion 当前生效的条款版本（非空白）
     * @return 仍有效返回 {@code true}
     */
    public boolean isValidFor(String currentTermsVersion) {
        if (!hasConsented() || currentTermsVersion == null || currentTermsVersion.isBlank()) {
            return false;
        }
        return acceptedTermsVersion.equals(currentTermsVersion.trim());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Consent other)) {
            return false;
        }
        return acceptedAtEpochSec == other.acceptedAtEpochSec
                && Objects.equals(acceptedTermsVersion, other.acceptedTermsVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(acceptedTermsVersion, acceptedAtEpochSec);
    }
}
