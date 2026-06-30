package com.nexa.interfaces.api.task;

import com.nexa.common.security.rbac.AuthLevel;
import com.nexa.common.security.rbac.AuthenticatedActor;
import com.nexa.common.security.annotation.CurrentActor;
import com.nexa.common.security.annotation.RequireRole;
import com.nexa.application.task.QueryTaskUseCase;
import com.nexa.application.task.TaskPage;
import com.nexa.domain.task.model.Task;
import com.nexa.domain.task.vo.TaskQuery;
import com.nexa.common.web.ApiResponse;
import com.nexa.interfaces.api.task.dto.TaskAdminVO;
import com.nexa.interfaces.api.task.dto.TaskListData;
import com.nexa.interfaces.api.task.dto.TaskUserVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 异步任务中心控制器（接口层，F-2003/F-2004 任务列表查询）。
 *
 * <p>承载任务查询端点（对齐 openapi）：
 * <ul>
 *   <li>{@code GET /api/task/self}  用户任务列表（sessionAuth + USER，强制 self-scope，F-2003）</li>
 *   <li>{@code GET /api/task}       管理端全量任务列表（adminAuth，跨用户含 channel_id，F-2004）</li>
 * </ul>
 * </p>
 *
 * <p>DDD 铁律：接口层只做协议翻译（绑定入参 → 调用例 → 裁剪视图），无业务逻辑。查询条件归一在
 * {@link TaskQuery}，self-scope 在仓储 SQL 兜底（用户侧强制 user_id）。领域异常由
 * {@link TaskExceptionHandler} 统一翻译。</p>
 *
 * <p><b>鉴权（安全声明）</b>：用户列表 {@link RequireRole}({@link AuthLevel#USER})；管理列表
 * {@link RequireRole}({@link AuthLevel#ADMIN})（方法级覆盖类级）。归属用户由 {@code @CurrentActor}
 * 注入，杜绝从请求参数读 user_id 伪造他人归属（用户侧）。</p>
 *
 * <p><b>客户视图铁律</b>：用户列表出参用 {@link TaskUserVO}（Omit channel_id、无 privateData）；
 * 管理列表用 {@link TaskAdminVO}（含 channel_id/user_id，仍无 privateData）。</p>
 */
@RestController
@RequestMapping("/api/task")
@RequireRole(AuthLevel.USER)
public class TaskController {

    private final QueryTaskUseCase queryTaskUseCase;

    /** @param queryTaskUseCase 任务查询用例 */
    public TaskController(QueryTaskUseCase queryTaskUseCase) {
        this.queryTaskUseCase = queryTaskUseCase;
    }

    /**
     * 用户任务列表（F-2003，self-scope）。
     *
     * <p>强制 {@code user_id=本人}（PRD AT-2 C5）：query 的 userId 取自 {@code @CurrentActor}，
     * 不信任请求参数。出参 {@link TaskUserVO}（无 channel_id/privateData）。</p>
     *
     * @param actor     认证主体（注入，提供本人 user_id）
     * @param taskId    任务 ID 过滤（可空）
     * @param action    动作过滤（可空）
     * @param status    状态过滤（可空）
     * @param platform  平台过滤（可空）
     * @param startTime 提交时间起 epoch 秒（可空）
     * @param endTime   提交时间止 epoch 秒（可空）
     * @param page      页码（可空，默认 1）
     * @param pageSize  每页条数（可空，默认 20）
     * @return 用户视图任务分页
     */
    @GetMapping("/self")
    public ApiResponse<TaskListData<TaskUserVO>> listSelf(
            @CurrentActor AuthenticatedActor actor,
            @RequestParam(value = "task_id", required = false) String taskId,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "platform", required = false) String platform,
            @RequestParam(value = "start_timestamp", required = false) Long startTime,
            @RequestParam(value = "end_timestamp", required = false) Long endTime,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "page_size", required = false) Integer pageSize) {
        // user_id 强制取认证主体（self-scope，不读请求参数防伪造）。
        TaskQuery query = TaskQuery.of(taskId, (int) actor.userId(), null,
                platform, action, status, startTime, endTime, page, pageSize);
        TaskPage result = queryTaskUseCase.list(query);
        List<TaskUserVO> views = result.items().stream().map(TaskUserVO::from).toList();
        return ApiResponse.okData(new TaskListData<>(views, result.total(), result.page(), result.pageSize()));
    }

    /**
     * 管理端全量任务列表（F-2004，AdminAuth，跨用户）。
     *
     * <p>无 user_id 限制，支持 channel_id/user_id/platform/action/status/时间区间 过滤。出参
     * {@link TaskAdminVO}（含 channel_id 区别用户自助接口）。方法级 {@link RequireRole}(ADMIN)
     * 覆盖类级 USER。</p>
     *
     * @param channelId 渠道 id 过滤（可空）
     * @param userId    用户 id 过滤（可空）
     * @param platform  平台过滤（可空）
     * @param action    动作过滤（可空）
     * @param status    状态过滤（可空）
     * @param startTime 提交时间起 epoch 秒（可空）
     * @param endTime   提交时间止 epoch 秒（可空）
     * @param page      页码（可空）
     * @param pageSize  每页条数（可空）
     * @return 管理视图任务分页
     */
    @GetMapping
    @RequireRole(AuthLevel.ADMIN)
    public ApiResponse<TaskListData<TaskAdminVO>> listAll(
            @RequestParam(value = "channel_id", required = false) Integer channelId,
            @RequestParam(value = "user_id", required = false) Integer userId,
            @RequestParam(value = "platform", required = false) String platform,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "start_timestamp", required = false) Long startTime,
            @RequestParam(value = "end_timestamp", required = false) Long endTime,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "page_size", required = false) Integer pageSize) {
        TaskQuery query = TaskQuery.of(null, userId, channelId,
                platform, action, status, startTime, endTime, page, pageSize);
        TaskPage result = queryTaskUseCase.list(query);
        List<TaskAdminVO> views = result.items().stream().map(TaskAdminVO::from).toList();
        return ApiResponse.okData(new TaskListData<>(views, result.total(), result.page(), result.pageSize()));
    }
}
