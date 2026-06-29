package com.nexa.application.publicsite;

import com.nexa.application.publicsite.port.PublicSiteSettings;
import com.nexa.domain.publicsite.vo.LegalDocument;
import com.nexa.domain.publicsite.vo.LegalDocumentType;
import org.springframework.stereotype.Service;

/**
 * 查询公开法律文本用例（应用服务，F-4027 用户协议 / F-4028 隐私政策）。
 *
 * <p>编排：从 {@link PublicSiteSettings} 端口取「启用开关 + 原文」，交由领域工厂
 * {@link LegalDocument#publicContent} 应用「启用门控 + 空串兜底」规则（领域规则在领域层，
 * 本用例不自行写 if-enabled，backend-engineer §2.1）。两类文本共用一条编排，按类型分发取值。</p>
 *
 * <p>对齐 openapi {@code GET /api/user_agreement}、{@code GET /api/privacy_policy}（security: []，公开）。</p>
 */
@Service
public class QueryLegalDocumentUseCase {

    private final PublicSiteSettings settings;

    /**
     * @param settings 公开站点设置端口（提供法律文本启用开关与原文）
     */
    public QueryLegalDocumentUseCase(PublicSiteSettings settings) {
        this.settings = settings;
    }

    /**
     * 读取指定类型的公开法律文本（应用启用门控）。
     *
     * @param type 文本类型（用户协议/隐私政策）
     * @return 应用门控后的法律文本（未启用恒空串）
     */
    public LegalDocument query(LegalDocumentType type) {
        return switch (type) {
            case USER_AGREEMENT -> LegalDocument.publicContent(
                    LegalDocumentType.USER_AGREEMENT,
                    settings.isUserAgreementEnabled(),
                    settings.userAgreementContent());
            case PRIVACY_POLICY -> LegalDocument.publicContent(
                    LegalDocumentType.PRIVACY_POLICY,
                    settings.isPrivacyPolicyEnabled(),
                    settings.privacyPolicyContent());
        };
    }
}
