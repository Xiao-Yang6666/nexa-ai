package com.nexa.interfaces.api.log;

import com.nexa.application.log.QueryLogsByTokenUseCase;
import com.nexa.application.log.port.TokenIdResolver;
import com.nexa.shared.web.ApiResponse;
import com.nexa.interfaces.api.log.dto.LogListVO;
import com.nexa.interfaces.api.log.dto.UserLogVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 按令牌 key 查消费日志控制器（tokenReadAuth 端点，接口层，F-4003 GET /api/log/token）。
 *
 * <p>openapi 声明 {@code security: tokenReadAuth[]}——请求以 {@code Authorization: Bearer ***} 鉴权，
 * 返回该令牌的消费日志数组。不走 session/登录，故<b>不</b>标 {@link com.nexa.shared.security.annotation.RequireRole}；
 * 控制器自行从 Authorization 头取明文 key，经 {@link TokenIdResolver} 解析为 token_id（key 无效→0），
 * 交用例判定（token_id==0 → 400「无效的令牌」）。</p>
 *
 * <p><b>限流/禁缓存</b>：契约要求 CriticalRateLimit + DisableCache，由网关/横切层处理（非本控制器职责，
 * 与现网一致：在路由注册处挂中间件）。</p>
 *
 * <p><b>客户视图铁律</b>：输出 {@link UserLogVO}（用令牌查自己的日志，裁剪掉 B/成本/利润）。</p>
 */
@RestController
@RequestMapping("/api/log")
public class LogTokenController {

    private final QueryLogsByTokenUseCase queryLogsByTokenUseCase;
    private final TokenIdResolver tokenIdResolver;

    /**
     * @param queryLogsByTokenUseCase 按令牌查日志用例（F-4003）
     * @param tokenIdResolver         令牌 key→id 解析端口
     */
    public LogTokenController(QueryLogsByTokenUseCase queryLogsByTokenUseCase,
                              TokenIdResolver tokenIdResolver) {
        this.queryLogsByTokenUseCase = queryLogsByTokenUseCase;
        this.tokenIdResolver = tokenIdResolver;
    }

    /**
     * 按令牌明文 key 查消费日志（F-4003，{@code GET /api/log/token}，tokenReadAuth）。
     *
     * <p>从 Authorization 头取 Bearer key → 解析 token_id（无效→0）→ 用例查日志。token_id==0 时
     * 用例抛 {@code InvalidLogQueryException}「无效的令牌」，{@code LogExceptionHandler} 翻 400。</p>
     *
     * @param request HTTP 请求（提取 Authorization 头）
     * @return 成功信封，data = UserLogVO 数组
     */
    @GetMapping("/token")
    public ApiResponse<List<UserLogVO>> queryByToken(HttpServletRequest request) {
        String key = extractBearerToken(request);
        long tokenId = tokenIdResolver.resolveTokenId(key);
        return ApiResponse.okData(LogListVO.userArray(queryLogsByTokenUseCase.query(tokenId)));
    }

    /**
     * 从 Authorization 头提取 Bearer key。
     *
     * <p>缺失/格式不符返回 null → 解析得 token_id=0 → 用例拒绝（无效的令牌）。不在接口层硬抛，
     * 保持「接口层只翻译，规则在领域/用例」原则。</p>
     */
    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        return header.substring(7).trim();
    }
}
