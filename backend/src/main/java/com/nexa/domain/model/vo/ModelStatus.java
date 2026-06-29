package com.nexa.domain.model.vo;

import com.nexa.domain.model.exception.InvalidModelParameterException;

/**
 * 模型/供应商状态值对象（封装 status 整数码 + 语义判定）。
 *
 * <p>领域规则来源：DB-SCHEMA 模块四，模型/供应商共用同一套状态码：
 * {@code 1=启用（ENABLED）}、{@code 2=禁用（DISABLED）}。status_only 更新（F-3016）仅改本字段。</p>
 *
 * <p>值对象（不可变、按值相等），避免裸 int 状态码在领域层散落（backend-engineer §2.4）。</p>
 */
public enum ModelStatus {

    /** 启用（1）。 */
    ENABLED(1),

    /** 禁用（2）。 */
    DISABLED(2);

    private final int code;

    ModelStatus(int code) {
        this.code = code;
    }

    /** @return 持久化整数码 */
    public int code() {
        return code;
    }

    /**
     * 由整数码解析状态。
     *
     * @param code 状态整数码
     * @return 对应状态
     * @throws InvalidModelParameterException 非法状态码
     */
    public static ModelStatus fromCode(int code) {
        for (ModelStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new InvalidModelParameterException("invalid status code: " + code);
    }
}
