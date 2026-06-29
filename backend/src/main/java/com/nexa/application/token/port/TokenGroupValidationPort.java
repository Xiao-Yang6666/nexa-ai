package com.nexa.application.token.port;

/**
 * 令牌分组校验端口（token 域定接口，modelgroup BC 实现——DDD 依赖倒置）。
 *
 * <p>套餐制约束：创建 apikey 时绑定的分组 {@code group} 必须是当前用户<b>有权限且存在</b>的套餐分组
 * （公开 + 该用户被授权的私有组，且启用 + 模型集非空）。token 域只依赖本端口判定「某 userId 能否绑定
 * 某 group」，可访问性解析细节封装在 modelgroup BC（{@code ResolveAccessibleModelGroupsUseCase}），
 * token 不编译期耦合 modelgroup 内部。</p>
 *
 * <p>语义：空白 group 视为「不绑定任何套餐」——是否允许由调用方决定（本期 CreateTokenUseCase 要求必绑，
 * 空白即非法）。非空 group 必须命中可访问集合，否则视为越权/不存在。</p>
 */
public interface TokenGroupValidationPort {

    /**
     * 判定指定用户是否可绑定指定分组（创建 apikey 用）。
     *
     * @param userId 归属用户 id
     * @param group  待绑定分组 code（非空白）
     * @return 该用户有权且该分组存在可用 → true；否则 false
     */
    boolean canBind(long userId, String group);
}
