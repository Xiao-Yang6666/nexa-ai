package com.nexa.shared.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * JSONB 持久化字段的统一编解码器（基础设施层共享构件）。
 *
 * <p>背景：多个 bounded context 的 {@code XxxRepositoryImpl} 把领域值对象 ↔ {@code jsonb} 列字符串
 * 互转时，各自手写 {@code try { objectMapper.writeValueAsString(...) } catch (...) { throw wrap }}
 * 样板（channel/modelgroup/prefill/routing/task 等 ≥5 份同构 try-catch）。本类收敛该样板：
 * 统一用注入的全局 {@link ObjectMapper}（沿用 application.yml 的 SNAKE_CASE 等全局配置），
 * 序列化/反序列化失败统一 wrap 成 {@link JsonbCodecException}（不吞错、保留错误链，backend-engineer §3.2）。</p>
 *
 * <p>定位：纯基础设施层适配工具，不进 domain（domain 不感知 JSON/Jackson）。各 {@code RepositoryImpl}
 * 注入本类替代裸 {@code ObjectMapper} + 手写 try-catch。空值策略由调用方决定（本类不替调用方判空/兜底，
 * 保持单一职责——调用方更清楚「null 该落 null 串还是默认值对象」的领域语义）。</p>
 */
@Component
public class JsonbCodec {

    private final ObjectMapper objectMapper;

    /**
     * @param objectMapper 全局 Jackson 编解码器（继承 application.yml 的 SNAKE_CASE 等配置）
     */
    public JsonbCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 领域对象 → JSON 串（持久化方向）。
     *
     * @param value 待序列化对象（调用方保证非 null；null 语义由调用方在调用前处理）
     * @return JSON 串
     * @throws JsonbCodecException 序列化失败（编程/数据异常，不吞错）
     */
    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new JsonbCodecException("failed to serialize value to jsonb: "
                    + (value == null ? "null" : value.getClass().getSimpleName()), ex);
        }
    }

    /**
     * JSON 串 → 领域对象（重建方向，简单类型）。
     *
     * @param json JSON 串（调用方保证非空白；空/null 兜底由调用方处理）
     * @param type 目标类型
     * @param <T>  目标类型参数
     * @return 反序列化对象
     * @throws JsonbCodecException 反序列化失败（数据损坏属可观测异常，不吞错）
     */
    public <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ex) {
            throw new JsonbCodecException("failed to deserialize jsonb to "
                    + type.getSimpleName(), ex);
        }
    }

    /**
     * JSON 串 → 领域对象（重建方向，泛型容器，如 {@code List<Foo>}/{@code Map<String,Bar>}）。
     *
     * @param json    JSON 串（调用方保证非空白）
     * @param typeRef 泛型类型引用
     * @param <T>     目标类型参数
     * @return 反序列化对象
     * @throws JsonbCodecException 反序列化失败（数据损坏属可观测异常，不吞错）
     */
    public <T> T read(String json, TypeReference<T> typeRef) {
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (Exception ex) {
            throw new JsonbCodecException("failed to deserialize jsonb to "
                    + typeRef.getType().getTypeName(), ex);
        }
    }
}
