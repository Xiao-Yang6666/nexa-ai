package com.nexa.channel.interfaces.api;

import com.nexa.channel.application.QueryChannelPoolUseCase;
import com.nexa.channel.interfaces.api.dto.ApiResponse;
import com.nexa.channel.interfaces.api.dto.ChannelPoolListView;
import com.nexa.channel.interfaces.api.dto.ChannelPoolMember;
import com.nexa.shared.security.domain.rbac.AuthLevel;
import com.nexa.shared.security.interfaces.annotation.RequireRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 供应渠道池查询控制器（AdminAuth/RootAuth 端点，接口层，F-6005）。
 *
 * <p>承载供应渠道池查询端点（对齐 openapi /api/channel/pool）：
 * <ul>
 *   <li>{@code GET /api/channel/pool}  按对外模型 A / 真实模型 B / 分组查供应渠道池（管理端）</li>
 * </ul>
 * </p>
 *
 * <p><b>B 不可见三道闸——接口层闸</b>：响应 {@link ChannelPoolMember}（含 upstream_model=B），
 * 仅 admin/root；客户无任何读路径触达本控制器。类级 {@link RequireRole}({@link AuthLevel#ADMIN})。</p>
 */
@RestController
@RequestMapping("/api/channel/pool")
@RequireRole(AuthLevel.ADMIN)
public class ChannelPoolController {

    private final QueryChannelPoolUseCase useCase;

    /** @param useCase 渠道池查询用例 */
    public ChannelPoolController(QueryChannelPoolUseCase useCase) {
        this.useCase = useCase;
    }

    /**
     * 查供应渠道池（F-6005，{@code GET /api/channel/pool}）。
     *
     * <p>按 group + upstream_model=B 过滤同一 B 下的渠道池成员（按 priority 分层 + weight 加权随机选渠）。
     * public_name(A) 参数在本投影中仅作语义占位（A→B 映射在系统内部，查询直接按 B 维拉成员）。</p>
     *
     * @param publicName    query 对外名 A（语义占位，可空；选渠按 B 维进行）
     * @param upstreamModel query 真实模型 B（可空 → 返回该 group 全部渠道成员）
     * @param group         query 分组（可空 → 不过滤）
     * @return 成功信封，data = { items[] }（AdminView，含 B）
     */
    @GetMapping
    public ApiResponse<ChannelPoolListView> pool(
            @RequestParam(name = "public_name", required = false) String publicName,
            @RequestParam(name = "upstream_model", required = false) String upstreamModel,
            @RequestParam(name = "group", required = false) String group) {
        List<ChannelPoolMember> items = useCase.queryPool(group, upstreamModel).stream()
                .map(c -> ChannelPoolMember.from(c, upstreamModel))
                .toList();
        return ApiResponse.okData(new ChannelPoolListView(items));
    }
}
