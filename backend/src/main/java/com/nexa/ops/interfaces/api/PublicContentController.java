package com.nexa.ops.interfaces.api;

import com.nexa.ops.application.option.GetPublicContentUseCase;
import com.nexa.ops.interfaces.api.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开内容/状态控制器（匿名公开端点，接口层，F-4027~F-4029）。
 *
 * <p>承载匿名可读的公开内容端点（对齐 openapi）：
 * <ul>
 *   <li>{@code GET /api/user_agreement} 用户协议（F-4027）</li>
 *   <li>{@code GET /api/privacy_policy} 隐私政策（F-4028）</li>
 *   <li>{@code GET /api/notice} 公告（F-4029）</li>
 *   <li>{@code GET /api/about} 关于页（F-4029）</li>
 *   <li>{@code GET /api/home_page_content} 首页自定义内容（F-4029）</li>
 * </ul>
 * 均返回 {@code data = "<文本>"}（未设置为空串，对齐契约）。</p>
 *
 * <p><b>鉴权（安全声明）</b>：全部匿名公开（契约要求），故无 {@code @RequireRole}。安全护栏：
 * 每个端点用<b>固定键名</b>调用用例（非客户端任意指定 key），杜绝经公开端点越权读取敏感配置
 * （如 *Secret）。这些键本身均为对外公开的内容键，无敏感泄露风险。</p>
 */
@RestController
public class PublicContentController {

    /** 公开内容键（与现网 OptionMap 键名一致）。 */
    private static final String KEY_USER_AGREEMENT = "UserAgreement";
    private static final String KEY_PRIVACY_POLICY = "PrivacyPolicy";
    private static final String KEY_NOTICE = "Notice";
    private static final String KEY_ABOUT = "About";
    private static final String KEY_HOME_PAGE_CONTENT = "HomePageContent";

    private final GetPublicContentUseCase getPublicContentUseCase;

    /**
     * @param getPublicContentUseCase 公开内容读取用例
     */
    public PublicContentController(GetPublicContentUseCase getPublicContentUseCase) {
        this.getPublicContentUseCase = getPublicContentUseCase;
    }

    /**
     * 用户协议公开读取（F-4027）。
     *
     * @return {@code data = 协议文本}（未设置为空串）
     */
    @GetMapping("/api/user_agreement")
    public ApiResponse<String> userAgreement() {
        return ApiResponse.okData(getPublicContentUseCase.execute(KEY_USER_AGREEMENT));
    }

    /**
     * 隐私政策公开读取（F-4028）。
     *
     * @return {@code data = 政策文本}（未设置为空串）
     */
    @GetMapping("/api/privacy_policy")
    public ApiResponse<String> privacyPolicy() {
        return ApiResponse.okData(getPublicContentUseCase.execute(KEY_PRIVACY_POLICY));
    }

    /**
     * 公告公开读取（F-4029）。
     *
     * @return {@code data = 公告文本}
     */
    @GetMapping("/api/notice")
    public ApiResponse<String> notice() {
        return ApiResponse.okData(getPublicContentUseCase.execute(KEY_NOTICE));
    }

    /**
     * 关于页公开读取（F-4029）。
     *
     * @return {@code data = 关于页文本}
     */
    @GetMapping("/api/about")
    public ApiResponse<String> about() {
        return ApiResponse.okData(getPublicContentUseCase.execute(KEY_ABOUT));
    }

    /**
     * 首页自定义内容公开读取（F-4029）。
     *
     * @return {@code data = 首页内容文本}
     */
    @GetMapping("/api/home_page_content")
    public ApiResponse<String> homePageContent() {
        return ApiResponse.okData(getPublicContentUseCase.execute(KEY_HOME_PAGE_CONTENT));
    }
}
