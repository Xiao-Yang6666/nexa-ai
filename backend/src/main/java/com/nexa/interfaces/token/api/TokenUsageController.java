package com.nexa.interfaces.token.api;

import com.nexa.application.token.QueryTokenUsageUseCase;
import com.nexa.shared.web.ApiResponse;
import com.nexa.interfaces.token.api.dto.UsageView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 令牌用量查询控制器（tokenReadAuth 端点，接口层，F-3012 GET /api/usage/token）。
 *
 * <p>openapi 声明 {@code security: tokenReadAuth[]}——请求以 Authorization: Bearer <token_key> 鉴权，
 * 返回该令牌的用量摘要（OpenAI 兼容 credit_summary）。不需要 session/登录。</p>
 *
 * <p>本控制器不标 {@code @RequireRole}（不走 sessionAuth），自行从 Authorization 头解析令牌 key，
 * 调 {@link QueryTokenUsageUseCase#queryByKey} 完成鉴权+查询一体化（key 缺失/无效→401）。</p>
 */
@RestController
@RequestMapping("/api/usage")
public class TokenUsageController {

    private final QueryTokenUsageUseCase queryTokenUsageUseCase;

    /** @param queryTokenUsageUseCase 用量查询用例 */
    public TokenUsageController(QueryTokenUsageUseCase queryTokenUsageUseCase) {
        this.queryTokenUsageUseCase = queryTokenUsageUseCase;
    }

    /**
     * 令牌用量查询（F-3012，{@code GET /api/usage/token}，tokenReadAuth）。
     *
     * <p>从 Authorization 头提取 Bearer token key，查询对应令牌的用量。key 无效/缺失时由用例抛
     * {@code InvalidTokenKeyException}，{@link TokenExceptionHandler} 翻译为 401。</p>
     *
     * @param request HTTP 请求（用于提取 Authorization 头）
     * @return 成功信封，data = 用量摘要（对齐 openapi UsageCreditSummary）
     */
    @GetMapping("/token")
    public ApiResponse<UsageView> queryUsage(HttpServletRequest request) {
        String key = extractBearerToken(request);
        return ApiResponse.okData(UsageView.from(queryTokenUsageUseCase.queryByKey(key)));
    }

    /**
     * 从 Authorization 头提取 Bearer token key。
     *
     * <p>格式：{@code Authorization: Bearer <key>}。缺失或格式错时返回 null/空串，由用例层拒绝
     * （key=null → InvalidTokenKeyException → 401）。不在接口层硬抛，保持「接口层只翻译，规则在领域」原则。</p>
     */
    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            // 返回 null 让用例拒绝（key 缺失→InvalidTokenKeyException→401），保持接口层无逻辑。
            return null;
        }
        return header.substring(7).trim();
    }
}
