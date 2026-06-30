package com.nexa.interfaces.api.ops.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexa.domain.ops.setup.SetupStatus;

/**
 * 系统初始化状态视图（接口层出参 DTO，F-4015 GET /api/setup）。
 *
 * <p>对齐 API-ENDPOINTS §9.1：已初始化→{@code {status:true}}（其余字段省略）；未初始化→
 * {@code {status:false, root_init, database_type}}。用 {@code @JsonInclude(NON_NULL)} 让已初始化时
 * root_init/database_type 不出现（与契约「直接结束」一致）。</p>
 *
 * @param status       是否已初始化
 * @param rootInit     是否已存在 root 用户（仅未初始化时有意义）
 * @param databaseType 数据库类型（仅未初始化时有意义）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SetupStatusVO(
        boolean status,
        @JsonProperty("root_init") Boolean rootInit,
        @JsonProperty("database_type") String databaseType) {

    /**
     * 由领域状态裁剪为视图。
     *
     * @param status 领域初始化状态
     * @return 视图（已初始化时仅 status=true）
     */
    public static SetupStatusVO from(SetupStatus status) {
        if (status.initialized()) {
            // 已初始化：仅 status=true，其余 null → NON_NULL 不序列化。
            return new SetupStatusVO(true, null, null);
        }
        return new SetupStatusVO(false, status.rootExists(), status.databaseType());
    }
}
