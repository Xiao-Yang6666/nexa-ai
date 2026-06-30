package com.nexa.application.account.command;

/**
 * 管理端更新用户资料命令（应用层入参 DTO，F-1011/1013/1014）。
 *
 * <p>对齐 API-ENDPOINTS / openapi.yaml {@code PUT /api/user/}：{@code id} 必填，其余字段为
 * <b>部分更新</b>语义——为 {@code null} 表示「该项不改」，非 null 才落地。覆盖资料（display_name/
 * email/quota，F-1011）、分组绑定（group，F-1013）、备注（remark，F-1014）、状态（status）。
 * {@code operatorRoleCode} 是发起更新的管理员角色，用于领域层角色越权护栏（AC-10）。</p>
 *
 * <p>设计取舍：邮箱以原始字符串传入（接口层不解析），由用例构造 {@code Email} 值对象触发格式校验；
 * 配额以 {@code Long} 包装类型承载，{@code null} 区别于「显式设 0」。</p>
 *
 * @param targetUserId     被更新的目标用户 id（必填）
 * @param displayName      新展示名（null=不改；空白=清空）
 * @param email            新邮箱原始字符串（null=不改）
 * @param group            新分组（null=不改）
 * @param quota            新额度（null=不改）
 * @param remark           新备注（null=不改；空白=清空）
 * @param status           新状态编码（null=不改；1=启用，其它=禁用）
 * @param discountRatio    新用户专属折扣（null=不改；售价侧，分组倍率之后再乘，须 ≥0）
 * @param operatorRoleCode 操作者角色编码（1=common,10=admin,100=root）
 */
public record UpdateUserProfileCommand(long targetUserId,
                                       String displayName,
                                       String email,
                                       String group,
                                       Long quota,
                                       String remark,
                                       Integer status,
                                       java.math.BigDecimal discountRatio,
                                       int operatorRoleCode) {
}
