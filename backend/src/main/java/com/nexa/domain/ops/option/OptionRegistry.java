package com.nexa.domain.ops.option;

import com.nexa.domain.ops.exception.InvalidOptionValueException;

import java.util.List;
import java.util.Set;

/**
 * 选项更新校验领域服务（F-4018 + 横切配置 F-4032/F-4035，纯领域规则，零框架依赖）。
 *
 * <p>PUT /api/option/ 逐键写入前的领域校验全部收敛于此（充血领域服务，backend-engineer §2.4）：
 * 主题值合法性、限流分组 JSON/[count,duration] 结构、支付合规键禁改护栏。把这些散落在现网
 * Go controller 里的 switch-case 校验提炼为可单测的领域规则，接口层只调 {@link #validate}。</p>
 *
 * <p>校验失败统一抛 {@link InvalidOptionValueException}（→400），message 对齐契约文案。
 * 未列入特殊校验的键按「直通」处理（覆盖式写入，幂等），不在领域层枚举全部合法键
 * （现网 OptionMap 上百键，逐一枚举不现实且脆弱）——领域只守护「有明确业务约束」的键。</p>
 *
 * <p>领域规则来源：API-ENDPOINTS §9.2 PUT /api/option/ 错误码段 + §9.6 横切配置表
 * （F-4032 限流分组、F-4035 主题）。F-4033 敏感词 / F-4034 自动分组在现网为「格式宽松、
 * 运行时解析」，无强制结构校验，故按直通处理（写入即生效，解析失败时运行时降级）。</p>
 */
public final class OptionRegistry {

    /** F-4035 主题合法值（前端依 GetStatus.theme 渲染）。 */
    private static final String THEME_FRONTEND_KEY = "theme.frontend";
    private static final List<String> VALID_THEMES = List.of("default", "classic");

    /** F-4032 模型请求限流分组键。 */
    private static final String RATE_LIMIT_GROUP_KEY = "ModelRequestRateLimitGroup";

    /** F-4030/§9.5 支付合规键前缀：禁止经 PUT /api/option/ 修改（须走专用确认端点）。 */
    private static final String COMPLIANCE_KEY_PREFIX = "payment_setting.compliance_";

    /** R3-01 系统配置面板：布尔字符串键（值须为 "true"/"false"）。 */
    private static final Set<String> BOOLEAN_KEYS = Set.of(
            "register.invite_only",
            "smtp.tls",
            "security.force_2fa",
            "advanced.maintenance_mode",
            "advanced.debug_log");

    /** R3-01：非负整数键（值须为整数且 ≥0）。 */
    private static final Set<String> NON_NEGATIVE_INT_KEYS = Set.of(
            "ratelimit.default_rpm",
            "ratelimit.default_tpm",
            "security.lockout_threshold",
            "advanced.log_retention_days");

    /** R3-01：正整数键（值须为整数且 ≥1）。 */
    private static final Set<String> POSITIVE_INT_KEYS = Set.of(
            "security.session_ttl_minutes",
            "advanced.request_timeout_sec");

    /** R3-01：计费货币键 + 白名单。 */
    private static final String BILLING_CURRENCY_KEY = "billing.currency";
    private static final List<String> VALID_CURRENCIES = List.of("CNY", "USD");

    /** R3-01：SMTP 端口键（值须为 1-65535 整数）。 */
    private static final String SMTP_PORT_KEY = "smtp.port";

    private OptionRegistry() {
    }

    /**
     * 校验单键选项更新是否合法（PUT /api/option/ 前置门）。
     *
     * <p>分支：
     * <ol>
     *   <li>支付合规键（{@code payment_setting.compliance_*}）→ 禁改，抛异常（须走 §9.5）；</li>
     *   <li>{@code theme.frontend} → 必须是 default/classic（F-4035）；</li>
     *   <li>{@code ModelRequestRateLimitGroup} → 必须是合法 JSON 且每项为 [count,duration] 正整数对（F-4032）；</li>
     *   <li>其他键 → 直通（无强制结构约束）。</li>
     * </ol>
     * </p>
     *
     * @param key   配置键名
     * @param value 配置值
     * @throws InvalidOptionValueException 校验不通过（→400）
     * @throws IllegalArgumentException    key 为空白（脏键，调用方应已绑定校验拦住）
     */
    public static void validate(String key, String value) {
        OptionKey optionKey = OptionKey.of(key);
        String k = optionKey.value();

        // 规则 1：支付合规键禁止经本接口修改（§9.5 走专用确认端点，防绕过合规闸门）。
        if (k.startsWith(COMPLIANCE_KEY_PREFIX)) {
            throw new InvalidOptionValueException(
                    "支付合规设置不可经选项接口修改，请使用合规确认端点");
        }

        // 规则 2：前端主题值白名单（F-4035）。
        if (THEME_FRONTEND_KEY.equals(k)) {
            validateTheme(value);
            return;
        }

        // 规则 3：模型请求限流分组格式（F-4032）。
        if (RATE_LIMIT_GROUP_KEY.equals(k)) {
            validateRateLimitGroup(value);
            return;
        }

        // 规则 4：R3-01 系统配置面板有约束键（布尔 / 整数范围 / 货币枚举 / 端口）。
        if (BOOLEAN_KEYS.contains(k)) {
            validateBoolean(k, value);
            return;
        }
        if (NON_NEGATIVE_INT_KEYS.contains(k)) {
            validateIntInRange(k, value, 0, Integer.MAX_VALUE);
            return;
        }
        if (POSITIVE_INT_KEYS.contains(k)) {
            validateIntInRange(k, value, 1, Integer.MAX_VALUE);
            return;
        }
        if (BILLING_CURRENCY_KEY.equals(k)) {
            validateCurrency(value);
            return;
        }
        if (SMTP_PORT_KEY.equals(k)) {
            validateIntInRange(k, value, 1, 65535);
            return;
        }

        // 其他键直通（覆盖式写入，无领域强约束）。
    }

    /**
     * 校验主题值（F-4035）。
     *
     * @param value 主题值
     * @throws InvalidOptionValueException 非 default/classic
     */
    private static void validateTheme(String value) {
        if (value == null || !VALID_THEMES.contains(value)) {
            // 文案对齐契约（API-ENDPOINTS §9.2）。
            throw new InvalidOptionValueException(
                    "无效的主题值，可选值：default（新版前端）、classic（经典前端）");
        }
    }

    /**
     * 校验模型请求限流分组配置（F-4032，期望形如 {@code {"group":[count,duration], ...}}）。
     *
     * <p>不引入 JSON 库（domain 零框架依赖），用轻量手写解析校验结构：
     * 必须是非空 JSON 对象，每个 value 是恰含两个非负整数的数组 [count, duration]。
     * 这里只校验「能否被解释为合法 [count,duration] 映射」，宽松接受空对象（清空限流）。</p>
     *
     * @param value JSON 字符串
     * @throws InvalidOptionValueException 非法 JSON 或非法 [count,duration]
     */
    private static void validateRateLimitGroup(String value) {
        String trimmed = value == null ? "" : value.trim();
        // 空值 / 空对象 = 清空限流分组，合法。
        if (trimmed.isEmpty() || trimmed.equals("{}")) {
            return;
        }
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new InvalidOptionValueException("限流分组配置必须为合法 JSON 对象");
        }
        // 提取所有 [a,b] 数组段，逐个校验为两个非负整数。容错：用括号配对扫描，不依赖完整 JSON 解析器。
        String body = trimmed.substring(1, trimmed.length() - 1);
        int arrayCount = 0;
        int i = 0;
        while (i < body.length()) {
            char c = body.charAt(i);
            if (c == '[') {
                int end = body.indexOf(']', i);
                if (end < 0) {
                    throw new InvalidOptionValueException("限流分组配置数组未闭合");
                }
                validateCountDurationPair(body.substring(i + 1, end));
                arrayCount++;
                i = end + 1;
            } else {
                i++;
            }
        }
        if (arrayCount == 0) {
            // 形似对象却无任何 [count,duration] 项 → 非法（既非空对象也不含合法分组）。
            throw new InvalidOptionValueException("限流分组配置缺少合法的 [count,duration] 项");
        }
    }

    /**
     * 校验单个 [count,duration] 对（逗号分隔的两个非负整数）。
     *
     * @param inner 去掉方括号后的内容，如 {@code "10,60"}
     * @throws InvalidOptionValueException 非两项 / 非整数 / 负数
     */
    private static void validateCountDurationPair(String inner) {
        String[] parts = inner.split(",");
        if (parts.length != 2) {
            throw new InvalidOptionValueException("限流分组每项必须为 [count,duration] 两个值");
        }
        for (String part : parts) {
            String p = part.trim();
            int n;
            try {
                n = Integer.parseInt(p);
            } catch (NumberFormatException e) {
                // wrap 带上下文（不吞错），便于定位是哪段非整数。
                throw new InvalidOptionValueException("限流分组 [count,duration] 必须为整数：" + p);
            }
            if (n < 0) {
                throw new InvalidOptionValueException("限流分组 [count,duration] 不可为负数：" + p);
            }
        }
    }

    /**
     * 校验布尔字符串键（R3-01，值须恰为 "true"/"false"）。
     *
     * @throws InvalidOptionValueException 非 true/false
     */
    private static void validateBoolean(String key, String value) {
        if (!"true".equals(value) && !"false".equals(value)) {
            throw new InvalidOptionValueException(
                    "配置项 " + key + " 的值必须为 true 或 false");
        }
    }

    /**
     * 校验整数范围键（R3-01，值须为合法整数且落在 [min,max] 闭区间）。
     *
     * @throws InvalidOptionValueException 非整数或越界
     */
    private static void validateIntInRange(String key, String value, int min, int max) {
        int n;
        try {
            n = Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException e) {
            throw new InvalidOptionValueException("配置项 " + key + " 的值必须为整数");
        }
        if (n < min || n > max) {
            throw new InvalidOptionValueException(
                    "配置项 " + key + " 的值必须在 " + min + " 到 " + max + " 之间");
        }
    }

    /**
     * 校验计费货币键（R3-01，白名单 CNY/USD）。
     *
     * @throws InvalidOptionValueException 非白名单货币
     */
    private static void validateCurrency(String value) {
        if (value == null || !VALID_CURRENCIES.contains(value)) {
            throw new InvalidOptionValueException("无效的计费货币，可选值：CNY、USD");
        }
    }
}
