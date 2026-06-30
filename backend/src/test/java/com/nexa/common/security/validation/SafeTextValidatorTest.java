package com.nexa.common.security.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SafeTextValidator} 单测（纯 JUnit，不起 Spring/校验容器——直接构造 validator 调 isValid）。
 *
 * <p>覆盖统一输入校验里「拒绝危险控制字符」的核心逻辑：NUL/C0/C1/DEL 一律拒绝（挡截断攻击、日志/终端
 * 注入），换行制表按 allowWhitespaceControls 开关放行/拒绝，null 与正常文本放行。按正常/边界/异常三类
 * 组织（backend-engineer §3.3）。</p>
 */
@DisplayName("SafeTextValidator 安全文本校验器")
class SafeTextValidatorTest {

    /** 测试用 ConstraintValidatorContext 占位（本校验器逻辑不依赖 context）。 */
    private static final ConstraintValidatorContext NO_CONTEXT = null;

    /** 构造一个指定 allowWhitespaceControls 的 SafeText 注解实例。 */
    private static SafeText safeText(boolean allowWhitespace) {
        return new SafeText() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return SafeText.class;
            }

            @Override
            public String message() {
                return "";
            }

            @Override
            public Class<?>[] groups() {
                return new Class<?>[0];
            }

            @Override
            @SuppressWarnings("unchecked")
            public Class<? extends jakarta.validation.Payload>[] payload() {
                return (Class<? extends jakarta.validation.Payload>[]) new Class<?>[0];
            }

            @Override
            public boolean allowWhitespaceControls() {
                return allowWhitespace;
            }
        };
    }

    private static SafeTextValidator validator(boolean allowWhitespace) {
        SafeTextValidator v = new SafeTextValidator();
        v.initialize(safeText(allowWhitespace));
        return v;
    }

    @Test
    @DisplayName("正常：普通可见文本通过")
    void plainTextValid() {
        assertTrue(validator(false).isValid("Hello World 你好 123", NO_CONTEXT));
    }

    @Test
    @DisplayName("边界：null 视为通过（必填性交给 @NotNull/@NotBlank）")
    void nullIsValid() {
        assertTrue(validator(false).isValid(null, NO_CONTEXT));
    }

    @Test
    @DisplayName("边界：空串通过（无控制字符）")
    void emptyIsValid() {
        assertTrue(validator(false).isValid("", NO_CONTEXT));
    }

    @Test
    @DisplayName("异常：含 NUL 字符拒绝（截断攻击）")
    void rejectsNul() {
        assertFalse(validator(false).isValid("abc\u0000def", NO_CONTEXT));
    }

    @Test
    @DisplayName("异常：含 CR/LF 在单行模式下拒绝（日志注入）")
    void rejectsCrlfInSingleLineMode() {
        assertFalse(validator(false).isValid("line1\r\nline2", NO_CONTEXT));
        assertFalse(validator(false).isValid("tab\there", NO_CONTEXT));
    }

    @Test
    @DisplayName("正常：多行模式下放行换行/制表")
    void allowsWhitespaceInMultilineMode() {
        assertTrue(validator(true).isValid("line1\nline2\twith tab\r\n", NO_CONTEXT));
    }

    @Test
    @DisplayName("异常：多行模式下仍拒绝 NUL 等非空白控制符")
    void multilineStillRejectsNul() {
        assertFalse(validator(true).isValid("ok\u0000bad", NO_CONTEXT));
    }

    @Test
    @DisplayName("异常：DEL(0x7F) 与 C1 控制符拒绝")
    void rejectsDelAndC1() {
        assertFalse(validator(false).isValid("a\u007Fb", NO_CONTEXT));
        assertFalse(validator(false).isValid("a\u0085b", NO_CONTEXT));
    }
}
