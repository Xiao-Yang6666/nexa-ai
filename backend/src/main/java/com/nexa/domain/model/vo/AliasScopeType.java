package com.nexa.domain.model.vo;

import com.nexa.domain.model.exception.InvalidModelParameterException;

import java.util.Locale;

/**
 * 别名作用域类型值对象（不可变，按值相等，UserModelAlias 用，F-6003）。
 *
 * <p>领域规则来源：DB-SCHEMA §18 UserModelAlias.scope_type ∈ {user, group} +
 * COMPAT-BILLING-DECISIONS §2「客户层映射 C→A：作用域 = 分组级 + 用户级（用户级优先于分组级）」。
 * 优先级 user &gt; group 由查询排序保证；本枚举仅承载类型语义。</p>
 *
 * <p>越权护栏（DB-SCHEMA §18）：user 路由写入强制 {@code scope_type=user AND scope_id=:caller_user_id}，
 * 禁跨 scope 写——此约束在应用层 {@code SaveUserModelAliasUseCase} 落地，本枚举不承担。</p>
 */
public enum AliasScopeType {

    /** 用户级（scope_id = user_id 字符串化），优先级高于 group。 */
    USER("user"),

    /** 分组级（scope_id = 分组名）。 */
    GROUP("group");

    private final String code;

    AliasScopeType(String code) {
        this.code = code;
    }

    /** @return DB/接口层稳定字面值（{@code user}/{@code group}） */
    public String code() {
        return code;
    }

    /**
     * 由字符串解析（接口层入参/DB 重建用）。
     *
     * @param raw 原始字面（大小写不敏感，去空白）
     * @return 对应枚举
     * @throws InvalidModelParameterException 非 {@code user}/{@code group}
     */
    public static AliasScopeType fromCode(String raw) {
        if (raw == null) {
            throw new InvalidModelParameterException("scope_type 不能为空");
        }
        String c = raw.trim().toLowerCase(Locale.ROOT);
        return switch (c) {
            case "user" -> USER;
            case "group" -> GROUP;
            default -> throw new InvalidModelParameterException("scope_type 只能是 user 或 group，收到 " + raw);
        };
    }
}
