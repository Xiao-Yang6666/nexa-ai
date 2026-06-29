package com.nexa.domain.routing.model;

import com.nexa.domain.routing.exception.InvalidAffinityParameterException;
import com.nexa.domain.routing.vo.AffinityRequestContext;
import com.nexa.domain.routing.vo.KeySource;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 渠道亲和规则聚合根（充血领域模型，F-2029 会话亲和键提取 + F-2030 header 透传 + F-2034 SkipRetryOnFailure）。
 *
 * <p>领域规则来源：FC-068 {@code setting/operation_setting/channel_affinity_setting.go}。一条规则约束：
 * <ul>
 *   <li>{@code modelRegex}/{@code pathRegex} —— 命中条件（同时满足才算命中本规则）。</li>
 *   <li>{@code keySources} —— 会话键提取来源（有序），从请求里逐个取值，{@code |} 拼接组成会话键
 *       （见 {@link #extractKey}）。</li>
 *   <li>{@code passHeaders} —— F-2030 命中后向上游透传的 CLI 专属 header 模板（如 codex 透传
 *       {@code OpenAI-Beta}、claude 透传 {@code anthropic-version} 等）；接口层选渠后照单注入上游。</li>
 *   <li>{@code skipRetryOnFailure} —— F-2034。命中本规则的请求若失败，是否「直接返错不跨渠道重试」
 *       （内置 codex/claude 均 true，避免缓存被刷到别的渠道破坏会话稳定）。</li>
 *   <li>{@code ttlSeconds} —— 命中→渠道映射在缓存中的 TTL（覆盖 {@link AffinitySettings} 默认 TTL）。</li>
 *   <li>{@code builtIn} —— 内置规则（codex/claude）不可被外部删除，仅可整体禁用。</li>
 * </ul>
 * 本聚合是「亲和规则」一致性边界，所有不变量（正则可编译、来源非空、TTL 非负）在工厂/方法上守护
 * （backend-engineer §2.2 充血、§2.3 repository 抽象）。零框架依赖，可纯单测。</p>
 *
 * <p>聚合不持有 Pattern 缓存——领域模型保持值语义/可序列化；正则编译在 {@link #matches} 内做，由调用方
 * （上层缓存装饰器或选渠服务）决定是否缓存编译后的 Pattern（避免领域层引入可变缓存破坏值语义）。</p>
 */
public class AffinityRule {

    /** 内置 codex 规则名（FC-068）。 */
    public static final String BUILTIN_CODEX = "codex";

    /** 内置 claude 规则名（FC-068）。 */
    public static final String BUILTIN_CLAUDE = "claude";

    /** 规则名最大长度（对齐 DB-SCHEMA varchar(64)）。 */
    public static final int NAME_MAX_LENGTH = 64;

    /** 是否启用本规则（关闭后不参与命中判定）。 */
    private boolean enabled;

    /** 规则名（唯一键，配置/缓存的规则维度都用它）。 */
    private final String name;

    /** model 命中正则（对 RelayInfo.OriginModel 或客户输入 model 做匹配）。 */
    private String modelRegex;

    /** 请求 path 命中正则（对 HTTP path 做匹配，如 codex 用 /v1/responses）。 */
    private String pathRegex;

    /** 会话键来源（有序，逐个提取后 | 拼接，见 extractKey）。 */
    private List<KeySource> keySources;

    /** F-2030 header 透传模板（命中后注入到上游 header；忽略 null/空白值）。 */
    private Map<String, String> passHeaders;

    /** F-2034 命中规则失败时是否跳过跨渠道重试（true=保稳定不重试）。 */
    private boolean skipRetryOnFailure;

    /** 命中→渠道映射 TTL（秒，覆盖 AffinitySettings 默认 TTL；0=用默认）。 */
    private long ttlSeconds;

    /** 是否内置规则（codex/claude，不可被外部删除）。 */
    private final boolean builtIn;

    private AffinityRule(boolean enabled, String name, String modelRegex, String pathRegex,
                        List<KeySource> keySources, Map<String, String> passHeaders,
                        boolean skipRetryOnFailure, long ttlSeconds, boolean builtIn) {
        this.enabled = enabled;
        this.name = name;
        this.modelRegex = modelRegex;
        this.pathRegex = pathRegex;
        this.keySources = keySources;
        this.passHeaders = passHeaders;
        this.skipRetryOnFailure = skipRetryOnFailure;
        this.ttlSeconds = ttlSeconds;
        this.builtIn = builtIn;
    }

    /**
     * 创建自定义亲和规则（充血工厂，全字段校验，F-2031 配置侧）。
     *
     * <p>领域规则：name/modelRegex/pathRegex/keySources 必填；正则需可编译（{@link Pattern#compile}）；
     * keySources 至少 1 个；ttlSeconds 非负（0=用 settings 默认）。skipRetryOnFailure 自定义规则默认
     * false（PRD CH-4 §6 明确）。</p>
     *
     * @param name               规则名（唯一，非空白，≤64）
     * @param enabled            是否启用
     * @param modelRegex         model 正则（必填，可编译）
     * @param pathRegex          path 正则（必填，可编译）
     * @param keySources         会话键来源（至少 1 个）
     * @param passHeaders        header 透传模板（可空）
     * @param skipRetryOnFailure 命中失败是否跳重试
     * @param ttlSeconds         缓存 TTL 秒（>=0，0=用默认）
     * @return 新建规则
     * @throws InvalidAffinityParameterException 字段非法
     */
    public static AffinityRule custom(String name, boolean enabled, String modelRegex, String pathRegex,
                                     List<KeySource> keySources, Map<String, String> passHeaders,
                                     boolean skipRetryOnFailure, long ttlSeconds) {
        return new AffinityRule(
                enabled, requireName(name), requireRegex(modelRegex, "model_regex"),
                requireRegex(pathRegex, "path_regex"), requireKeySources(keySources),
                normalizeHeaders(passHeaders), skipRetryOnFailure,
                requireNonNegative(ttlSeconds, "ttl_seconds"), false);
    }

    /**
     * 构造内置 codex 规则（FC-068 默认配置：gpt-* + /v1/responses + gjson prompt_cache_key + skipRetryOnFailure=true）。
     *
     * <p>领域规则：内置规则不可被删除（仅可禁用），用于保障 codex CLI 端到端的会话粘连稳定。
     * F-2030 透传 OpenAI-Beta（responses=experimental）等 CLI 专属 header。TTL=0 表示用 settings 默认 TTL。</p>
     *
     * @return 内置 codex 规则（默认启用）
     */
    public static AffinityRule builtinCodex() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("OpenAI-Beta", "responses=experimental");
        headers.put("X-Stainless-Helper-Method", "stream");
        return new AffinityRule(
                true, BUILTIN_CODEX, "^gpt-.*", "^/v1/responses$",
                List.of(new KeySource(com.nexa.domain.routing.vo.KeySourceType.GJSON, "prompt_cache_key")),
                Collections.unmodifiableMap(headers), true, 0L, true);
    }

    /**
     * 构造内置 claude 规则（FC-068 默认配置：claude-* + /v1/messages + gjson metadata.user_id + skipRetryOnFailure=true）。
     *
     * <p>F-2030 透传 anthropic-version、anthropic-beta、x-app 等 Claude CLI 专属 header。TTL=0 表示用默认。</p>
     *
     * @return 内置 claude 规则（默认启用）
     */
    public static AffinityRule builtinClaude() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("anthropic-version", "2023-06-01");
        headers.put("anthropic-beta", "");
        headers.put("x-app", "cli");
        return new AffinityRule(
                true, BUILTIN_CLAUDE, "^claude-.*", "^/v1/messages$",
                List.of(new KeySource(com.nexa.domain.routing.vo.KeySourceType.GJSON, "metadata.user_id")),
                Collections.unmodifiableMap(headers), true, 0L, true);
    }

    /**
     * 基础设施层持久化重建专用工厂（不触发创建不变量；用于从 DB/配置反序列化已存规则）。
     *
     * @return 重建的聚合（参数语义见各字段注释）
     */
    public static AffinityRule rehydrate(boolean enabled, String name, String modelRegex, String pathRegex,
                                        List<KeySource> keySources, Map<String, String> passHeaders,
                                        boolean skipRetryOnFailure, long ttlSeconds, boolean builtIn) {
        // 委托 Builder 装配：集合 null→空集合 + 防御性拷贝、ttl 钳为非负的归一逻辑收敛在 Builder 一处。
        return builder()
                .enabled(enabled)
                .name(name)
                .modelRegex(modelRegex)
                .pathRegex(pathRegex)
                .keySources(keySources)
                .passHeaders(passHeaders)
                .skipRetryOnFailure(skipRetryOnFailure)
                .ttlSeconds(ttlSeconds)
                .builtIn(builtIn)
                .build();
    }

    /**
     * 持久化重建构建器入口（基础设施层 {@code toDomain} 专用）。
     *
     * <p>替代 {@link #rehydrate} 的长位置参数列表：调用处以具名链式方法装配，可读性与抗重构性更好
     * （第 5、6 个参数是 keySources 还是 passHeaders，位置参数易错，Builder 一目了然）。
     * 与 {@code rehydrate} 一致——本入口<b>不</b>触发创建不变量校验，纯还原已存状态。</p>
     *
     * @return 新的亲和规则重建构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 亲和规则聚合的持久化重建构建器（充血聚合状态对外只读，仅基础设施层重建时经此装配）。
     *
     * <p>设计要点：{@link #keySources(List)} 把 null 归一为空 List 并做 {@link List#copyOf} 防御性拷贝，
     * {@link #passHeaders(Map)} 把 null 归一为空 Map 并做 {@link Map#copyOf} 防御性拷贝，
     * {@link #ttlSeconds(long)} 把负值钳为 0——这些与原 {@code rehydrate} 完全一致的归一逻辑全部收敛在此，
     * {@code AffinityRuleRepositoryImpl.toDomain} 不再散落兜底。</p>
     */
    public static final class Builder {
        private boolean enabled;
        private String name;
        private String modelRegex;
        private String pathRegex;
        private List<KeySource> keySources = List.of();
        private Map<String, String> passHeaders = Map.of();
        private boolean skipRetryOnFailure;
        private long ttlSeconds;
        private boolean builtIn;

        private Builder() {
        }

        /** @param enabled 是否启用（null 归一为 false） */
        public Builder enabled(Boolean enabled) {
            this.enabled = enabled != null && enabled;
            return this;
        }

        /** @param name 规则名 */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /** @param modelRegex model 命中正则 */
        public Builder modelRegex(String modelRegex) {
            this.modelRegex = modelRegex;
            return this;
        }

        /** @param pathRegex path 命中正则 */
        public Builder pathRegex(String pathRegex) {
            this.pathRegex = pathRegex;
            return this;
        }

        /** @param keySources 会话键来源（null 归一为空 List，非空时做防御性拷贝，与原 rehydrate 一致） */
        public Builder keySources(List<KeySource> keySources) {
            this.keySources = keySources == null ? List.of() : List.copyOf(keySources);
            return this;
        }

        /** @param passHeaders header 透传模板（null 归一为空 Map，非空时做防御性拷贝，与原 rehydrate 一致） */
        public Builder passHeaders(Map<String, String> passHeaders) {
            this.passHeaders = passHeaders == null ? Map.of() : Map.copyOf(passHeaders);
            return this;
        }

        /** @param skipRetryOnFailure 命中失败是否跳重试（null 归一为 false） */
        public Builder skipRetryOnFailure(Boolean skipRetryOnFailure) {
            this.skipRetryOnFailure = skipRetryOnFailure != null && skipRetryOnFailure;
            return this;
        }

        /** @param ttlSeconds 缓存 TTL 秒（null 归一为 0，负值钳为 0，与原 rehydrate 的 {@code Math.max(0L, ttl)} 一致） */
        public Builder ttlSeconds(Long ttlSeconds) {
            this.ttlSeconds = ttlSeconds == null ? 0L : Math.max(0L, ttlSeconds);
            return this;
        }

        /** @param builtIn 是否内置规则（null 归一为 false） */
        public Builder builtIn(Boolean builtIn) {
            this.builtIn = builtIn != null && builtIn;
            return this;
        }

        /**
         * 装配并返回重建的亲和规则聚合（不触发创建不变量校验）。
         *
         * @return 重建的亲和规则聚合
         */
        public AffinityRule build() {
            return new AffinityRule(enabled, name, modelRegex, pathRegex,
                    keySources, passHeaders, skipRetryOnFailure, ttlSeconds, builtIn);
        }
    }

    /**
     * 覆盖式编辑规则（F-2031 配置侧，对内置规则仅允许调 enabled/passHeaders/ttlSeconds，正则与
     * key_sources 不可改——保持内置语义稳定，避免 codex/claude 命中条件被改坏）。
     *
     * @param enabled            新启用状态
     * @param modelRegex         新 model 正则（自定义规则必填；内置规则忽略）
     * @param pathRegex          新 path 正则（自定义规则必填；内置规则忽略）
     * @param keySources         新会话键来源（自定义规则必填；内置规则忽略）
     * @param passHeaders        新 header 透传模板
     * @param skipRetryOnFailure 命中失败是否跳重试
     * @param ttlSeconds         新 TTL 秒
     * @throws InvalidAffinityParameterException 字段非法
     */
    public void update(boolean enabled, String modelRegex, String pathRegex, List<KeySource> keySources,
                       Map<String, String> passHeaders, boolean skipRetryOnFailure, long ttlSeconds) {
        this.enabled = enabled;
        this.passHeaders = normalizeHeaders(passHeaders);
        this.skipRetryOnFailure = skipRetryOnFailure;
        this.ttlSeconds = requireNonNegative(ttlSeconds, "ttl_seconds");
        if (!builtIn) {
            // 仅自定义规则可改命中条件与 key_sources（内置规则保持现网兼容语义不动）。
            this.modelRegex = requireRegex(modelRegex, "model_regex");
            this.pathRegex = requireRegex(pathRegex, "path_regex");
            this.keySources = requireKeySources(keySources);
        }
    }

    /**
     * 切换启用状态（轻量行为；含内置规则——内置规则可禁用但不可删）。
     *
     * @param enabled 新启用状态
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 判断本规则是否命中给定请求（model + path 同时匹配；规则关闭即视为不命中，PRD CH-4 §3）。
     *
     * <p>领域规则：命中要求 modelRegex 与 pathRegex 双条件 AND——任一未匹配则未命中本规则。
     * 正则编译失败视为未命中（保护选渠主干不因配置错误整体崩，由配置侧校验防御）。</p>
     *
     * @param model 请求模型名（如 gpt-4o）
     * @param path  请求 path（如 /v1/responses）
     * @return true=命中本规则，可进会话键提取
     */
    public boolean matches(String model, String path) {
        if (!enabled) {
            return false;
        }
        if (model == null || path == null) {
            return false;
        }
        try {
            return Pattern.compile(modelRegex).matcher(model).find()
                    && Pattern.compile(pathRegex).matcher(path).find();
        } catch (PatternSyntaxException e) {
            // 配置错误不应让选渠整体崩——视为未命中，由 update() 校验前置防御。
            return false;
        }
    }

    /**
     * 提取请求的会话键（F-2029，PRD CH-4 §3 节点 af_key）。
     *
     * <p>领域规则：按 keySources 顺序从请求上下文取值，全部非空白则 {@code |} 拼接组成会话键；
     * 任一来源缺值或拼接结果为空 → 返回 null（视为「键提取失败」，由调用方走普通选渠回退，
     * PRD CH-4 §6「缓存未命中或已过期 → 回退 CH-2 普通选渠，不报错」的更上游版本）。</p>
     *
     * @param request 请求上下文（提供 gjson/header/context 取值能力）
     * @return 会话键串（如 {@code "u_123"} 或 {@code "u_123|en"}），无法构成有效键返回 null
     */
    public String extractKey(AffinityRequestContext request) {
        if (request == null || keySources == null || keySources.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (KeySource src : keySources) {
            String v = readValue(request, src);
            if (v == null || v.isBlank()) {
                return null;
            }
            if (sb.length() > 0) {
                sb.append('|');
            }
            sb.append(v.trim());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /**
     * 计算本规则的有效 TTL 秒（F-2031）。
     *
     * <p>领域规则：规则自身 ttlSeconds>0 则覆盖；否则回落 settings 默认 TTL。</p>
     *
     * @param defaultTtlSeconds settings 默认 TTL
     * @return 本规则生效 TTL（>=1，至少 1 秒）
     */
    public long effectiveTtlSeconds(long defaultTtlSeconds) {
        long t = ttlSeconds > 0 ? ttlSeconds : defaultTtlSeconds;
        return Math.max(1L, t);
    }

    private static String readValue(AffinityRequestContext request, KeySource src) {
        return switch (src.type()) {
            case GJSON -> request.readJsonPath(src.path());
            case REQUEST_HEADER -> request.readHeader(src.path());
            case CONTEXT_INT -> request.readContextInt(src.path()).map(String::valueOf).orElse(null);
            case CONTEXT_STRING -> request.readContextString(src.path()).orElse(null);
        };
    }

    // ---- 校验/归一私有方法（不变量守护） ----

    private static String requireName(String raw) {
        String v = raw == null ? null : raw.trim();
        if (v == null || v.isEmpty()) {
            throw new InvalidAffinityParameterException("rule name is required");
        }
        if (v.length() > NAME_MAX_LENGTH) {
            throw new InvalidAffinityParameterException("rule name length must be <= " + NAME_MAX_LENGTH);
        }
        return v;
    }

    private static String requireRegex(String raw, String field) {
        String v = raw == null ? null : raw.trim();
        if (v == null || v.isEmpty()) {
            throw new InvalidAffinityParameterException(field + " is required");
        }
        try {
            Pattern.compile(v);
        } catch (PatternSyntaxException e) {
            // wrap 带上下文，不裸 throw（backend-engineer §3.2）。
            throw new InvalidAffinityParameterException(field + " is not a valid regex: " + e.getDescription());
        }
        return v;
    }

    private static List<KeySource> requireKeySources(List<KeySource> raw) {
        if (raw == null || raw.isEmpty()) {
            throw new InvalidAffinityParameterException("key_sources must contain at least one entry");
        }
        // 防御：清掉 null 项，但不静默接受全空——空集合即非法。
        List<KeySource> filtered = raw.stream().filter(Objects::nonNull).toList();
        if (filtered.isEmpty()) {
            throw new InvalidAffinityParameterException("key_sources must contain at least one entry");
        }
        return List.copyOf(filtered);
    }

    private static Map<String, String> normalizeHeaders(Map<String, String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        // 保序，过滤 null key，保留空字符串 value（FC-068 中部分 header 模板用空串占位由上层动态填充）。
        Map<String, String> out = new LinkedHashMap<>();
        raw.forEach((k, v) -> {
            if (k != null && !k.isBlank()) {
                out.put(k.trim(), v == null ? "" : v);
            }
        });
        return Collections.unmodifiableMap(out);
    }

    private static long requireNonNegative(long v, String field) {
        if (v < 0) {
            throw new InvalidAffinityParameterException(field + " must be >= 0");
        }
        return v;
    }

    // ---- 只读访问器（聚合状态对外只读） ----

    /** @return 是否启用 */
    public boolean enabled() {
        return enabled;
    }

    /** @return 规则名 */
    public String name() {
        return name;
    }

    /** @return model 命中正则 */
    public String modelRegex() {
        return modelRegex;
    }

    /** @return path 命中正则 */
    public String pathRegex() {
        return pathRegex;
    }

    /** @return 会话键来源（不可变） */
    public List<KeySource> keySources() {
        return keySources;
    }

    /** @return header 透传模板（不可变） */
    public Map<String, String> passHeaders() {
        return passHeaders;
    }

    /** @return 命中失败是否跳重试 */
    public boolean skipRetryOnFailure() {
        return skipRetryOnFailure;
    }

    /** @return 缓存 TTL 秒（0=用默认） */
    public long ttlSeconds() {
        return ttlSeconds;
    }

    /** @return 是否内置规则 */
    public boolean builtIn() {
        return builtIn;
    }
}
