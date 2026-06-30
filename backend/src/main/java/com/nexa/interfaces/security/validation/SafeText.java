package com.nexa.interfaces.security.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 统一输入校验：安全文本约束注解（接口层 DTO 字段用）。
 *
 * <p>在 openapi schema 的 {@code maxLength}/{@code pattern} 之外，补一层<b>跨 bounded context 复用</b>的
 * 通用安全约束：拒绝 NUL/控制字符等可用于截断攻击、日志注入、SQL/命令注入辅助的危险字符。
 * 与 Bean Validation（{@code @NotBlank}/{@code @Size}/{@code @Email}）叠加使用，统一在接口层把住
 * 「不信任外部输入」这道门（backend-engineer §3.4）。</p>
 *
 * <p>校验失败由各 BC 的 {@code @RestControllerAdvice} 或全站兜底处理器翻译为 400，message 透传本约束。</p>
 *
 * <p>用法：
 * <pre>{@code
 *   public record CreateXxxRequest(@SafeText @Size(max = 50) String name) {}
 * }</pre>
 * 字段为 {@code null} 时视为通过（是否必填交给 {@code @NotNull}/{@code @NotBlank} 负责，职责单一）。</p>
 */
@Documented
@Constraint(validatedBy = SafeTextValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface SafeText {

    /** @return 校验失败提示信息 */
    String message() default "field contains illegal control characters";

    /** @return Bean Validation 分组（默认空） */
    Class<?>[] groups() default {};

    /** @return Bean Validation 负载（默认空） */
    Class<? extends Payload>[] payload() default {};

    /**
     * 是否允许换行/制表（{@code \n}/{@code \r}/{@code \t}）。多行文本字段（如备注）置 true，
     * 单行字段（用户名/邮箱/标题）保持默认 false 一并拒绝。
     *
     * @return true 放行常见空白控制符，false 连同换行制表一起拒绝
     */
    boolean allowWhitespaceControls() default false;
}
