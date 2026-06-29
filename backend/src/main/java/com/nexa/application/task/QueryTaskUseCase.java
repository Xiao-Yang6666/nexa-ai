package com.nexa.application.task;

import com.nexa.domain.task.model.Task;
import com.nexa.domain.task.repository.TaskRepository;
import com.nexa.domain.task.vo.TaskQuery;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 任务查询用例（应用层，F-2003 用户列表 / F-2004 管理列表 / F-2006/F-2007 单任务拉取）。
 *
 * <p>用例编排：仓储按 {@link TaskQuery} 过滤分页 + 计数 → 组装 {@link TaskPage}；单查走 task_id
 * （管理）或 task_id+user_id（用户 self-scope）。<b>self-scope</b>（PRD AT-2 C5）由仓储 SQL 强制
 * user_id 过滤兜底（用户侧 query.userId 必非空）。薄编排，无领域规则。</p>
 *
 * <p><b>视图裁剪在接口层</b>：本用例返回领域聚合，接口层据角色裁剪为 TaskUserView（Omit channel_id、
 * 无 privateData）或 TaskAdminView（含 channel_id）。</p>
 */
@Service
public class QueryTaskUseCase {

    private final TaskRepository taskRepository;

    /** @param taskRepository 任务仓储 */
    public QueryTaskUseCase(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    /**
     * 分页查询任务列表（F-2003 用户侧 / F-2004 管理侧）。
     *
     * @param query 查询条件（用户侧 userId 必非空强制 self-scope；管理侧可空跨用户）
     * @return 任务分页结果
     */
    public TaskPage list(TaskQuery query) {
        return new TaskPage(
                taskRepository.findAll(query),
                taskRepository.countAll(query),
                query.page(),
                query.pageSize());
    }

    /**
     * 按 task_id 拉取单任务（F-2006/F-2007 管理或回调侧，无 user 限制）。
     *
     * @param taskId 任务 ID
     * @return 任务聚合（不存在返回 empty）
     */
    public Optional<Task> getByTaskId(String taskId) {
        return taskRepository.findByTaskId(taskId);
    }

    /**
     * 按 task_id + userId 拉取单任务（F-2007 用户侧 self-scope，他人 task 不可见 → empty → 404）。
     *
     * @param taskId      任务 ID
     * @param actorUserId 认证用户 id
     * @return 任务聚合（不存在/非本人返回 empty）
     */
    public Optional<Task> getByTaskIdForUser(String taskId, int actorUserId) {
        return taskRepository.findByTaskIdAndUserId(taskId, actorUserId);
    }
}
