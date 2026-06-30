package com.nexa.interfaces.api.ops.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 系统初始化提交请求（接口层入参 DTO，F-4016 POST /api/setup）。
 *
 * <p>对齐 API-ENDPOINTS §9.1 入参字段（含大写驼峰的模式开关，用 {@link JsonProperty} 映射）。
 * 仅做协议承载——长度/一致性等领域校验在 {@code SetupSubmission} 构造期完成（充血），本 DTO 不放
 * Bean Validation 注解，避免与领域规则双写/漂移（单一事实来源在领域）。</p>
 *
 * @param username        root 用户名
 * @param password        密码
 * @param confirmPassword 确认密码
 * @param selfUseModeEnabled 自用模式开关（默认 false）
 * @param demoSiteEnabled    演示模式开关（默认 false）
 */
public record SetupSubmitRequest(
        String username,
        String password,
        @JsonProperty("confirm_password") String confirmPassword,
        @JsonProperty("SelfUseModeEnabled") boolean selfUseModeEnabled,
        @JsonProperty("DemoSiteEnabled") boolean demoSiteEnabled) {
}
