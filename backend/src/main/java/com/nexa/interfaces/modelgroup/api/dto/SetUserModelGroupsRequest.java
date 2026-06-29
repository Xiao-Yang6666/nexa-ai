package com.nexa.interfaces.modelgroup.api.dto;

import java.util.List;

/**
 * 覆盖式设置用户私有模型组授权请求（管理端入参，用户列表里给某用户配私有组）。
 *
 * <p>覆盖式语义：{@code codes} 是该用户最终应拥有的全部 USER 级授权组编码（后端做 diff 增删）。
 * 空数组/null = 清空该用户的私有组授权。</p>
 *
 * @param codes 目标模型组 code 集（覆盖式）
 */
public record SetUserModelGroupsRequest(List<String> codes) {
}
