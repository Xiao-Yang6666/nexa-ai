package com.nexa.domain.publicsite.vo;

/**
 * 公开法律文本值对象（不可变，F-4027/F-4028）。
 *
 * <p>承载某类法律文本（用户协议/隐私政策）对外可见的内容。领域规则（openapi 描述「未设置为空串」 +
 * BACKLOG T-199/T-200「xxx_enabled 为 false 不展示」）：
 * <ul>
 *   <li>该文本类型<b>未启用</b>（enabled=false）→ 对外内容为空串（前端据此不渲染该页）。</li>
 *   <li>启用但未设置内容 → 空串（openapi「未设置为空串」）。</li>
 *   <li>启用且有内容 → 原文。</li>
 * </ul>
 * 该「启用门控 + 空串兜底」规则由 {@link #publicContent} 工厂在领域层落地，杜绝接口层各自实现导致偏差。</p>
 *
 * @param type    文本类型
 * @param content 对外内容（已应用启用门控，未启用/未设置恒为空串，非 null）
 */
public record LegalDocument(LegalDocumentType type, String content) {

    /**
     * 紧凑构造器：content 归一为非 null（公开端点返回稳定空串而非 null）。
     */
    public LegalDocument {
        content = content == null ? "" : content;
    }

    /**
     * 按「启用门控 + 空串兜底」规则构造对外法律文本（领域规则工厂，F-4027/F-4028）。
     *
     * @param type       文本类型
     * @param enabled    该文本是否启用展示
     * @param rawContent 已配置的原文（可空）
     * @return 应用门控后的法律文本（未启用恒空串）
     */
    public static LegalDocument publicContent(LegalDocumentType type, boolean enabled, String rawContent) {
        // 未启用 → 对外不暴露内容（空串）；启用 → 原文（null 由紧凑构造器归一为空串）。
        String visible = enabled ? rawContent : "";
        return new LegalDocument(type, visible);
    }
}
