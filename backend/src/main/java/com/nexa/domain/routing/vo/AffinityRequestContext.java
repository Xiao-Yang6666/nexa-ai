package com.nexa.domain.routing.vo;

import java.util.Optional;

/**
 * 亲和键提取请求上下文抽象（domain 端口，F-2029，PRD CH-4「key_sources」）。
 *
 * <p>领域层通过本接口获取提取会话键所需的四类值来源（gjson / header / context_int / context_string），
 * 具体实现在 infrastructure 层绑定到 HTTP request（DDD 依赖倒置：domain 定接口 infra 实现）。</p>
 *
 * <p>设计动机：领域层不感知 HttpServletRequest / relay 上下文实现——只声明需要的取值能力；
 * 单测可直接 mock 本接口验证 {@link com.nexa.domain.routing.model.AffinityRule#extractKey} 逻辑。</p>
 */
public interface AffinityRequestContext {

    /**
     * 从请求体 JSON 按 gjson 路径取值。
     *
     * <p>gjson 是现网 Go 层的 JSON 路径语法（类 JsonPath / jq 子集）；Java 侧 infra 用
     * JsonPointer / manual parsing 兼容（精确语义见 infra adapter 注释）。</p>
     *
     * @param jsonPath gjson 路径（如 "prompt_cache_key"、"metadata.user_id"）
     * @return 匹配值的字符串表示，缺失或非标量返回 null
     */
    String readJsonPath(String jsonPath);

    /**
     * 从 HTTP 请求头取值。
     *
     * @param headerName header 名（大小写敏感）
     * @return header 值，缺失返回 null
     */
    String readHeader(String headerName);

    /**
     * 从 relay 上下文取整型值。
     *
     * @param key 上下文 key
     * @return 命中返回值，否则空
     */
    Optional<Integer> readContextInt(String key);

    /**
     * 从 relay 上下文取字符串值。
     *
     * @param key 上下文 key
     * @return 命中返回值，否则空
     */
    Optional<String> readContextString(String key);
}
