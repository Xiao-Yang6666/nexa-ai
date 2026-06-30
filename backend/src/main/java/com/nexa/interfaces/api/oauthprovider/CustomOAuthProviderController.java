package com.nexa.interfaces.api.oauthprovider;

import com.nexa.interfaces.web.ApiResponse;
import com.nexa.application.oauthprovider.FetchOidcDiscoveryUseCase;
import com.nexa.application.oauthprovider.ManageCustomOAuthProviderUseCase;
import com.nexa.application.oauthprovider.command.SaveCustomOAuthProviderCommand;
import com.nexa.domain.oauthprovider.model.CustomOAuthProvider;
import com.nexa.domain.oauthprovider.vo.OAuthEndpoints;
import com.nexa.interfaces.api.oauthprovider.dto.CustomOAuthProviderVO;
import com.nexa.interfaces.api.oauthprovider.dto.DiscoveryRequest;
import com.nexa.interfaces.api.oauthprovider.dto.DiscoveryVO;
import com.nexa.interfaces.api.oauthprovider.dto.SaveCustomOAuthProviderRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 自定义 OAuth provider 管理控制器（RootAuth 端点，接口层，F-1023/1024）。
 *
 * <p>承载 root 端的自定义 provider 配置端点（对齐 openapi {@code /api/custom-oauth-provider*}）：
 * <ul>
 *   <li>{@code POST /api/custom-oauth-provider/discovery}（F-1023）→ {@link FetchOidcDiscoveryUseCase}</li>
 *   <li>{@code GET  /api/custom-oauth-provider}（F-1024 列表）→ {@link ManageCustomOAuthProviderUseCase#list}</li>
 *   <li>{@code POST /api/custom-oauth-provider}（F-1024 创建）→ {@code save}（id=null）</li>
 *   <li>{@code PUT  /api/custom-oauth-provider}（F-1024 更新）→ {@code save}（id 非空）</li>
 *   <li>{@code DELETE /api/custom-oauth-provider/{id}}（F-1024 删除）→ {@code delete}</li>
 * </ul></p>
 *
 * <p>DDD 铁律：接口层<b>只做协议翻译</b>（HTTP DTO ⇄ 用例命令/结果），不含业务逻辑——字段校验、
 * 端点合法、密钥保留语义全在 domain/application（backend-engineer §2.1）。出参一律用
 * {@link CustomOAuthProviderVO}（<b>绝不含 client_secret</b>，产品铁律）。领域异常由
 * {@link com.nexa.interfaces.api.oauthprovider.CustomOAuthProviderExceptionHandler} 翻译为 HTTP 状态码。</p>
 *
 * <p><b>鉴权（安全声明）</b>：openapi 标注本组端点为 {@code rootAuth}。本切片<b>尚未</b>落地 RootAuth
 * 鉴权过滤器（token→root 身份解析在后续 wave）；SecurityConfig 当前对非公开端点要求 {@code authenticated()}，
 * 故本组端点默认需认证（无认证 401），不会无鉴权裸奔。RootAuth 过滤器接入后再补 root 角色校验。</p>
 */
@RestController
@RequestMapping("/api/custom-oauth-provider")
public class CustomOAuthProviderController {

    private final FetchOidcDiscoveryUseCase fetchOidcDiscoveryUseCase;
    private final ManageCustomOAuthProviderUseCase manageUseCase;

    /**
     * @param fetchOidcDiscoveryUseCase discovery 拉取用例（F-1023）
     * @param manageUseCase             provider CRUD 用例（F-1024）
     */
    public CustomOAuthProviderController(FetchOidcDiscoveryUseCase fetchOidcDiscoveryUseCase,
                                         ManageCustomOAuthProviderUseCase manageUseCase) {
        this.fetchOidcDiscoveryUseCase = fetchOidcDiscoveryUseCase;
        this.manageUseCase = manageUseCase;
    }

    /**
     * 拉取 OIDC discovery 端点（F-1023，对齐 openapi {@code POST /discovery}）。
     *
     * @param request discovery 请求（含 issuer，已校验非空）
     * @return 成功信封，data 为三端点
     */
    @PostMapping("/discovery")
    public ApiResponse<DiscoveryVO> discovery(@Valid @RequestBody DiscoveryRequest request) {
        OAuthEndpoints endpoints = fetchOidcDiscoveryUseCase.fetch(request.issuer());
        return ApiResponse.okData(DiscoveryVO.from(endpoints));
    }

    /**
     * 列出全部自定义 provider（F-1024，对齐 openapi {@code GET /}）。
     *
     * @return 成功信封，data 为 provider 视图列表（无 client_secret）
     */
    @GetMapping
    public ApiResponse<List<CustomOAuthProviderVO>> list() {
        List<CustomOAuthProviderVO> data = manageUseCase.list().stream()
                .map(CustomOAuthProviderVO::from)
                .toList();
        return ApiResponse.okData(data);
    }

    /**
     * 创建自定义 provider（F-1024，对齐 openapi {@code POST /}）。
     *
     * <p>强制 id=null 走创建路径（忽略 body 里可能携带的 id，避免 POST 误当更新）。</p>
     *
     * @param request 创建请求（含 client_secret）
     * @return 成功回执（对齐 openapi 200 = SuccessResponse）
     */
    @PostMapping
    public ApiResponse<Void> create(@Valid @RequestBody SaveCustomOAuthProviderRequest request) {
        manageUseCase.save(toCommand(request, null));
        return ApiResponse.ok("create custom oauth provider success");
    }

    /**
     * 更新自定义 provider（F-1024，对齐 openapi {@code PUT /}）。
     *
     * <p>id 由 body 提供（openapi PUT 无路径 id，沿用视图 schema 的 id 字段定位目标）。
     * client_secret 可空=保留原密钥（聚合内语义）。</p>
     *
     * @param request 更新请求（含目标 id）
     * @return 成功回执
     */
    @PutMapping
    public ApiResponse<Void> update(@Valid @RequestBody SaveCustomOAuthProviderRequest request) {
        // 更新必须带 id：缺 id 的 PUT 退化为创建语义无意义，此处显式以 body.id 定位（为空则用例按创建处理）。
        manageUseCase.save(toCommand(request, request.id()));
        return ApiResponse.ok("update custom oauth provider success");
    }

    /**
     * 删除自定义 provider（F-1024，对齐 openapi {@code DELETE /{id}}）。
     *
     * @param id provider 主键
     * @return 成功回执
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") long id) {
        manageUseCase.delete(id);
        return ApiResponse.ok("delete custom oauth provider success");
    }

    /**
     * 协议翻译：请求 DTO → 应用层保存命令。
     *
     * <p>scopes 列表拼为空格分隔串（领域以串存储，对齐 OIDC scope 习惯）；enabled 缺省 true。</p>
     *
     * @param request 请求 DTO
     * @param id      目标 id（创建为 null）
     * @return 保存命令
     */
    private static SaveCustomOAuthProviderCommand toCommand(SaveCustomOAuthProviderRequest request, Long id) {
        String scopes = (request.scopes() == null || request.scopes().isEmpty())
                ? null
                : String.join(" ", request.scopes());
        boolean enabled = request.enabled() == null || request.enabled();
        return new SaveCustomOAuthProviderCommand(
                id,
                request.name(),
                request.clientId(),
                request.clientSecret(),
                request.authorizationEndpoint(),
                request.tokenEndpoint(),
                request.userinfoEndpoint(),
                scopes,
                enabled);
    }
}
