package com.nexa.log.interfaces.api;

import com.nexa.log.application.LogPage;
import com.nexa.log.application.PurgeLogsUseCase;
import com.nexa.log.application.QueryAdminLogsUseCase;
import com.nexa.log.application.QueryLogStatUseCase;
import com.nexa.log.domain.vo.LogQuery;
import com.nexa.log.domain.vo.LogStat;
import com.nexa.log.domain.vo.Pagination;
import com.nexa.log.interfaces.api.dto.AdminLogView;
import com.nexa.log.interfaces.api.dto.ApiResponse;
import com.nexa.log.interfaces.api.dto.LogListView;
import com.nexa.log.interfaces.api.dto.LogStatView;
import com.nexa.shared.security.domain.rbac.AuthLevel;
import com.nexa.shared.security.interfaces.annotation.RequireRole;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端日志控制器（adminAuth 端点，接口层，F-4001/F-4004/F-4006）。
 *
 * <p>承载管理端日志相关端点（对齐 openapi /api/log*）：
 * <ul>
 *   <li>{@code GET    /api/log/}      全量日志分页查询（F-4001，AdminLogView 全字段含 B/成本/利润）</li>
 *   <li>{@code DELETE /api/log/}      清理历史日志（F-4006，分批删，每批 ≤100）</li>
 *   <li>{@code GET    /api/log/stat}  管理端日志统计（F-4004，quota/rpm/tpm，仅 Type=2）</li>
 * </ul>
 * </p>
 *
 * <p>DDD 铁律：接口层只做协议翻译（绑定入参 → 调用用例 → 裁剪视图），过滤维度归一/统计换算/分批删除规则
 * 全在领域/应用层。领域异常由 {@code LogExceptionHandler} 统一翻译（400/403）。</p>
 *
 * <p><b>鉴权（安全声明）</b>：契约全 {@code /api/log*}（管理端）= adminAuth。类级 {@link RequireRole}
 * ({@link AuthLevel#ADMIN}) 由 {@code RequireRoleInterceptor} 统一拦截，未达 admin → 403、未认证 → 401。
 * 管理端无 self-scope（全站可见），故不注入 {@code @CurrentActor}（角色门槛已足够）。</p>
 *
 * <p><b>客户视图铁律</b>：本控制器属管理端，输出用 {@link AdminLogView}（含 B/成本/利润/渠道）；
 * 客户侧的裁剪视图在 {@link SelfLogController}（UserLogView）。</p>
 */
@RestController
@RequestMapping("/api/log")
@RequireRole(AuthLevel.ADMIN)
public class LogController {

    private final QueryAdminLogsUseCase queryAdminLogsUseCase;
    private final QueryLogStatUseCase queryLogStatUseCase;
    private final PurgeLogsUseCase purgeLogsUseCase;

    /**
     * @param queryAdminLogsUseCase 管理端列表用例（F-4001）
     * @param queryLogStatUseCase   日志统计用例（F-4004）
     * @param purgeLogsUseCase      历史清理用例（F-4006）
     */
    public LogController(QueryAdminLogsUseCase queryAdminLogsUseCase,
                         QueryLogStatUseCase queryLogStatUseCase,
                         PurgeLogsUseCase purgeLogsUseCase) {
        this.queryAdminLogsUseCase = queryAdminLogsUseCase;
        this.queryLogStatUseCase = queryLogStatUseCase;
        this.purgeLogsUseCase = purgeLogsUseCase;
    }

    /**
     * 管理端全量日志分页查询（F-4001，{@code GET /api/log/}）。
     *
     * @param type              类型（0/缺省=全部）
     * @param startTimestamp    起始 epoch 秒（可空）
     * @param endTimestamp      结束 epoch 秒（可空）
     * @param username          用户名过滤（可空）
     * @param tokenName         令牌名过滤（可空）
     * @param modelName         模型名过滤（可空）
     * @param channel           渠道 id 字符串过滤（可空）
     * @param group             分组过滤（可空）
     * @param requestId         请求 id 过滤（可空）
     * @param upstreamRequestId 上游请求 id 过滤（可空）
     * @param page              页号（可空→1）
     * @param pageSize          每页条数（可空→10）
     * @return 成功信封，data = { items[](AdminLogView), total, page, page_size }
     */
    @GetMapping("/")
    public ApiResponse<LogListView<AdminLogView>> list(
            @RequestParam(name = "type", required = false) Integer type,
            @RequestParam(name = "start_timestamp", required = false) Long startTimestamp,
            @RequestParam(name = "end_timestamp", required = false) Long endTimestamp,
            @RequestParam(name = "username", required = false) String username,
            @RequestParam(name = "token_name", required = false) String tokenName,
            @RequestParam(name = "model_name", required = false) String modelName,
            @RequestParam(name = "channel", required = false) String channel,
            @RequestParam(name = "group", required = false) String group,
            @RequestParam(name = "request_id", required = false) String requestId,
            @RequestParam(name = "upstream_request_id", required = false) String upstreamRequestId,
            @RequestParam(name = "p", required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize) {

        LogQuery query = LogQuery.forAdmin(type, startTimestamp, endTimestamp, username,
                tokenName, modelName, channel, group, requestId, upstreamRequestId);
        Pagination pagination = Pagination.of(page, pageSize);
        LogPage result = queryAdminLogsUseCase.query(query, pagination);
        return ApiResponse.okData(LogListView.admin(result));
    }

    /**
     * 清理历史日志（F-4006，{@code DELETE /api/log/}，分批每批 ≤100）。
     *
     * @param targetTimestamp 删除早于该时间的日志（epoch 秒；0 由用例拒绝 400）
     * @return 成功信封，data = 删除条数
     */
    @DeleteMapping("/")
    public ApiResponse<Integer> purge(
            @RequestParam(name = "target_timestamp") long targetTimestamp) {

        return ApiResponse.okData(purgeLogsUseCase.purge(targetTimestamp));
    }

    /**
     * 管理端日志统计（F-4004，{@code GET /api/log/stat}，quota/rpm/tpm，仅 Type=2）。
     *
     * @param type           类型（仅作过滤维度透传；统计强制 Type=2）
     * @param startTimestamp 起始 epoch 秒（可空，参与 rpm/tpm 时间窗）
     * @param endTimestamp   结束 epoch 秒（可空）
     * @param username       用户名过滤（可空）
     * @return 成功信封，data = LogStat（quota/rpm/tpm）
     */
    @GetMapping("/stat")
    public ApiResponse<LogStatView> stat(
            @RequestParam(name = "type", required = false) Integer type,
            @RequestParam(name = "start_timestamp", required = false) Long startTimestamp,
            @RequestParam(name = "end_timestamp", required = false) Long endTimestamp,
            @RequestParam(name = "username", required = false) String username) {

        LogQuery query = LogQuery.forAdmin(type, startTimestamp, endTimestamp, username,
                null, null, null, null, null, null);
        LogStat stat = queryLogStatUseCase.stat(query);
        return ApiResponse.okData(LogStatView.from(stat));
    }
}
