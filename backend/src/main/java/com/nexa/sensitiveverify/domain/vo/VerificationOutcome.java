package com.nexa.sensitiveverify.domain.vo;

import java.util.Objects;

/**
 * 单个因子的验证结果值对象（F-1038，不可变、按值相等）。
 *
 * <p>承载「某个 {@link VerificationMethod} 因子是否校验通过」这一事实，供领域服务
 * {@code SensitiveActionVerifier} 汇总裁决（任一通过即放行）。不可变值对象，零框架依赖
 * （backend-engineer §2.4）。</p>
 *
 * @param method 因子类型
 * @param passed 该因子是否校验通过
 */
public record VerificationOutcome(VerificationMethod method, boolean passed) {

    /**
     * 紧凑构造器：强制 method 非空（防御式，避免空因子混入裁决）。
     *
     * @throws NullPointerException method 为 null
     */
    public VerificationOutcome {
        Objects.requireNonNull(method, "method");
    }

    /**
     * 构造一个「通过」结果。
     *
     * @param method 因子类型
     * @return 通过结果
     */
    public static VerificationOutcome passed(VerificationMethod method) {
        return new VerificationOutcome(method, true);
    }

    /**
     * 构造一个「未通过」结果。
     *
     * @param method 因子类型
     * @return 未通过结果
     */
    public static VerificationOutcome failed(VerificationMethod method) {
        return new VerificationOutcome(method, false);
    }
}
