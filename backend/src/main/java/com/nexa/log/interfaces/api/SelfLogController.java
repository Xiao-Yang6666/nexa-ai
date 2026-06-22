package com.nexa.log.interfaces.api;

import com.nexa.log.application.LogPage;
import com.nexa.log.application.QueryLogStatUseCase;
import com.nexa.log.application.QuerySelfLogsUseCase;
import com.nexa.log.domain.vo.LogQuery;
import com.nexa.log.domain.vo.LogStat;
import com.nexa.log.domain.vo.Pagination;
import com.nexa.shared.web.ApiResponse;
import com.nexa.log.interfaces.api.dto.LogListView;
import com.nexa.log.interfaces.api.dto.LogStatView;
import com.nexa.log.interfaces.api.dto.UserLogView;
import com.nexa.shared.security.domain.rbac.AuthenticatedActor;
import com.nexa.shared.security.domain.rbac.AuthLevel;
import com.nexa.shared.security.interfaces.annotation.CurrentActor;
import com.nexa.shared.security.interfaces.annotation.RequireRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户自助日志控制器（sessionAuth 端点，接口层，F-4002/F-4005）。
 *
 * <p>承载用户自助日志端点（对齐 openapi）：
 * <ul>
 *   <li>{@code GET /api/log/self}      自助日志分页查询（F-4002，UserLogView，仅本人）</li>
 *   <li>{@code GET /api/log/self/stat} 自助日志统计（F-4005，quota/rpm/tpm，username 来自上下文）</li>
 * </ul>
 * </p>
 *
 * <p><b>鉴权（安全声明）</b>：类级 {@link RequireRole}({@link AuthLevel#USER})，未认证 → 401。
 * self-scope 强制：user_id/username 一律取自 {@code @CurrentActor}（认证上下文），<b>不</b>从请求参数读，
 * 杜绝按他人维度越权（ROLE-PERMISSION-MATRIX §3）。{@link LogQuery#forSelf} 结构上也不接受
 * username/channel/request_id 维度，双重保险。</p>
 *
 * <p><b>客户视图铁律</b>：输出一律 {@link UserLogView}（B/成本/利润/渠道/上游请求 ID 结构级剔除）。</p>
 */
@RestController
@RequestMapping("/api/log/self")
@RequireRole(AuthLevel.USER)
public class SelfLogController {

    private final QuerySelfLogsUseCase querySelfLogsUseCase;
    private final QueryLogStatUseCase queryLogStatUseCase;

    /**
     * @param querySelfLogsUseCase 自助列表用例（F-4002）
     * @param queryLogStatUseCase  统计用例（F-4005 复用）
     */
    public SelfLogController(QuerySelfLogsUseCase querySelfLogsUseCase,
                             QueryLogStatUseCase queryLogStatUseCase) {
        this.querySelfLogsUseCase = querySelfLogsUseCase;
        this.queryLogStatUseCase = queryLogStatUseCase;
    }

    /**
     * 用户自助日志分页查询（F-4002，{@code GET /api/log/self}）。
     *
     * @param type      类型（0/缺省=全部）
     * @param startTimestamp 起始 epoch 秒（可空）
     * @param endTimestamp   结束 epoch 秒（可空）
     * @param tokenName 令牌名过滤（可空）
     * @param modelName 模型名过滤（可空）
     * @param group     分组过滤（可空）
     * @param page      页号（可空→1）
     * @param pageSize  每页条数（可空→10）
     * @param actor     认证主体（提供 self-scope user_id）
     * @return 成功信封，data = { items[](UserLogView), total, page, page_size }
     */
    @GetMapping
    public ApiResponse<LogListView<UserLogView>> list(
            @RequestParam(name = "type", required = false) Integer type,
            @RequestParam(name = "start_timestamp", required = false) Long startTimestamp,
            @RequestParam(name = "end_timestamp", required = false) Long endTimestamp,
            @RequestParam(name = "token_name", required = false) String tokenName,
            @RequestParam(name = "model_name", required = false) String modelName,
            @RequestParam(name = "group", required = false) String group,
            @RequestParam(name = "p", required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @CurrentActor AuthenticatedActor actor) {

        LogQuery query = LogQuery.forSelf(actor.userId(), type, startTimestamp, endTimestamp,
                tokenName, modelName, group);
        Pagination pagination = Pagination.of(page, pageSize);
        LogPage result = querySelfLogsUseCase.query(query, pagination);
        return ApiResponse.okData(LogListView.user(result));
    }

    /**
     * 用户自助日志统计（F-4005，{@code GET /api/log/self/stat}，username 来自上下文不可伪造）。
     *
     * @param startTimestamp 起始 epoch 秒（可空，参与 rpm/tpm 时间窗）
     * @param endTimestamp   结束 epoch 秒（可空）
     * @param actor          认证主体（self-scope user_id）
     * @return 成功信封，data = LogStat（quota/rpm/tpm，仅本人 Type=2）
     */
    @GetMapping("/stat")
    public ApiResponse<LogStatView> stat(
            @RequestParam(name = "start_timestamp", required = false) Long startTimestamp,
            @RequestParam(name = "end_timestamp", required = false) Long endTimestamp,
            @CurrentActor AuthenticatedActor actor) {

        // forSelf 钉死 user_id；统计不需要 username 参数（自助统计天然按本人 user_id 聚合）。
        LogQuery query = LogQuery.forSelf(actor.userId(), 2, startTimestamp, endTimestamp,
                null, null, null);
        LogStat stat = queryLogStatUseCase.stat(query);
        return ApiResponse.okData(LogStatView.from(stat));
    }
}
