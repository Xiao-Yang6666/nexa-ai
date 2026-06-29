package com.nexa.domain.routing.vo;

import com.nexa.domain.routing.exception.InvalidAffinityParameterException;

/**
 * 单个会话键来源值对象（不可变，F-2029，PRD CH-4「key_sources」）。
 *
 * <p>领域规则来源：FC-068。描述「从哪取、取哪个」——{@link #type()} 决定取值通道
 * （gjson/header/context），{@link #path()} 是该通道下的取值路径（gjson 路径 / header 名 / context key）。
 * 会话亲和键由规则的有序 key_sources 逐个提取并拼接而成（见 {@link AffinityRule#extractKey}）。</p>
 *
 * <p>按值相等、不可变（值对象）。path 必填非空（无路径的来源无意义）。</p>
 *
 * @param type 来源类型（gjson/header/context_int/context_string）
 * @param path 该来源下的取值路径（非空白）
 */
public record KeySource(KeySourceType type, String path) {

    /**
     * 紧凑构造器：校验不变量（type 非空、path 非空白）。
     *
     * @throws InvalidAffinityParameterException type 为空或 path 空白
     */
    public KeySource {
        if (type == null) {
            throw new InvalidAffinityParameterException("key source type is required");
        }
        if (path == null || path.isBlank()) {
            throw new InvalidAffinityParameterException("key source path is required");
        }
        path = path.trim();
    }
}
