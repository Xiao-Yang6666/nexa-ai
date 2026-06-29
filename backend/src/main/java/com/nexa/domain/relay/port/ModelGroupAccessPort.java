package com.nexa.domain.relay.port;

/**
 * 模型组访问校验端口（domain 定接口，infrastructure 由 modelgroup BC 实现，REQ-05 访问闸门）。
 *
 * <p>灵活模型组管理的访问控制接入点：私有模型组（{@code access_policy=PRIVATE}）只对显式授权的
 * 用户/令牌可用。relay 域只依赖本端口判定「某 token/user 是否有权用某分组 code 对应的模型组」，
 * 公开/自动策略放行、私有策略查授权等细节封装在
 * {@code com.nexa.infrastructure.modelgroup.access.ModelGroupAccessAdapter}（依赖倒置）。</p>
 *
 * <p>判定语义（{@link #isAccessible}）：
 * <ul>
 *   <li>分组 code 无对应存活模型组 → 放行（{@code true}，回落旧行为，分组不受模型组管控）；</li>
 *   <li>命中存活模型组但<b>请求模型 A 不在该组 models 勾选列表</b> → 拒绝（套餐制命脉：可用模型 = 分组勾选模型）；</li>
 *   <li>命中模型组为 PUBLIC / AUTO_LEVEL 且模型在组内 → 放行；</li>
 *   <li>命中模型组为 PRIVATE 且模型在组内 → 仅当 token 级或 user 级有显式授权才放行，否则拒绝。</li>
 * </ul>
 * 闸门只判「能不能用」，不抛异常（拒绝返回 {@code false}，由 relay 应用层抛 403）。</p>
 */
public interface ModelGroupAccessPort {

    /**
     * 判定调用方是否有权用指定分组 code 对应模型组里的指定模型。
     *
     * @param groupCode      调用方使用的分组 code（来自 {@code Token.group}）
     * @param userId         调用方用户 id（user 级授权维）
     * @param tokenId        调用 token id（token 级授权维；可空——token 未接线时按仅 user 维判定）
     * @param requestedModel 请求并解析出的平台模型名 A（套餐制：须在该组 models 勾选列表内）
     * @return 有权使用返回 {@code true}；模型不在组内 / 私有组无授权返回 {@code false}
     */
    boolean isAccessible(String groupCode, long userId, Long tokenId, String requestedModel);
}
