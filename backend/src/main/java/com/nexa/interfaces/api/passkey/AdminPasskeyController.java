package com.nexa.interfaces.api.passkey;

import com.nexa.shared.web.ApiResponse;
import com.nexa.application.passkey.ManagePasskeyUseCase;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Passkey 管理端控制器（接口层，F-1032 管理端重置用户 Passkey，adminAuth）。
 *
 * <p>承载管理员重置目标用户 passkey 的端点（对齐 openapi {@code DELETE /api/user/{id}/reset_passkey}）。
 * 用于用户丢失 authenticator 时由管理员清除其 passkey，使其可重新注册。</p>
 *
 * <p>DDD 铁律：接口层只做协议翻译，重置语义（幂等删除）在 application（backend-engineer §2.1）。
 * 与 {@link PasskeyController} 分开是因为鉴权域不同（adminAuth vs sessionAuth/公开），便于后续按角色
 * 接入不同鉴权过滤器。</p>
 *
 * <p><b>鉴权（安全声明）</b>：openapi 标注本端点为 {@code adminAuth}。本切片<b>尚未</b>落地 AdminAuth
 * 鉴权过滤器；SecurityConfig 当前对非公开端点要求 {@code authenticated()}，故本端点默认需认证（无认证 401），
 * 不会无鉴权裸奔。AdminAuth 过滤器接入后再补 admin 角色校验。</p>
 */
@RestController
public class AdminPasskeyController {

    private final ManagePasskeyUseCase managePasskeyUseCase;

    /**
     * @param managePasskeyUseCase 查询/删除/重置用例（F-1031/1032）
     */
    public AdminPasskeyController(ManagePasskeyUseCase managePasskeyUseCase) {
        this.managePasskeyUseCase = managePasskeyUseCase;
    }

    /**
     * 管理端重置目标用户 passkey（F-1032，对齐 openapi {@code DELETE /api/user/{id}/reset_passkey}）。
     *
     * <p>幂等：目标用户无 passkey 也回成功（重置语义对「本就没有」视为成功）。</p>
     *
     * @param id 目标用户 id（路径段 {@code {id}}）
     * @return 成功回执
     */
    @DeleteMapping("/api/user/{id}/reset_passkey")
    public ApiResponse<Void> resetPasskey(@PathVariable("id") long id) {
        managePasskeyUseCase.resetByAdmin(id);
        return ApiResponse.ok("passkey reset");
    }
}
