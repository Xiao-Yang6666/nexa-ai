package com.nexa.token.interfaces.api.dto;

import java.util.List;

/**
 * 批量操作请求 DTO（接口层入参，对齐 openapi POST /api/token/batch 与 POST /api/token/keys/batch）。
 *
 * <p>ids 为令牌 id 列表。批量删除无上限限制（仅删本人，仓储兜底）；批量取明文 key 由用例校验 ≤100。</p>
 *
 * @param ids 令牌 id 列表（required）
 */
public record BatchIdsRequest(List<Long> ids) {
}
