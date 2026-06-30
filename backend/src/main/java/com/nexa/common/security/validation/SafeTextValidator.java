package com.nexa.common.security.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * {@link SafeText} 约束的校验器实现（接口层）。
 *
 * <p>拒绝包含 NUL（{@code \u0000}）及其它 C0/C1 控制字符的字符串，防止：截断攻击、日志注入
 * （CRLF）、终端转义注入、以及作为注入 payload 辅助的不可见字符。是否放行换行/制表由
 * {@link SafeText#allowWhitespaceControls()} 决定。</p>
 *
 * <p>注意：本校验<b>不</b>替代参数化查询/输出编码——它是纵深防御的一层（输入侧），SQL 注入的
 * 根本防线仍是 JPA 参数化绑定（见本切片 SECURITY-NOTES）。</p>
 */
public final class SafeTextValidator implements ConstraintValidator<SafeText, String> {

    private boolean allowWhitespaceControls;

    /** {@inheritDoc} */
    @Override
    public void initialize(SafeText constraint) {
        this.allowWhitespaceControls = constraint.allowWhitespaceControls();
    }

    /**
     * @param value   待校验字符串（{@code null} 视为通过，必填性由其它注解负责）
     * @param context 校验上下文
     * @return true 表示无危险控制字符
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            // 允许的空白控制符：仅当显式开启多行模式时放行 \t \n \r。
            if (allowWhitespaceControls && (c == '\t' || c == '\n' || c == '\r')) {
                continue;
            }
            // C0 控制符(0x00-0x1F)、DEL(0x7F)、C1 控制符(0x80-0x9F) 一律拒绝。
            if (c < 0x20 || c == 0x7F || (c >= 0x80 && c <= 0x9F)) {
                return false;
            }
        }
        return true;
    }
}
