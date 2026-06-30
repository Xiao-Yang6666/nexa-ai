package com.nexa.application.task;

import com.nexa.domain.task.model.Task;
import com.nexa.domain.task.repository.TaskRepository;
import com.nexa.domain.task.vo.BillingContext;
import com.nexa.domain.task.vo.TaskPlatform;
import org.springframework.stereotype.Service;

import java.time.Instant;
import com.nexa.application.task.command.SubmitTaskCommand;

/**
 * 任务提交用例（应用层，F-2001/F-2005/F-2007/F-2008 视频/音乐/MJ 提交 → InitTask）。
 *
 * <p>用例编排：构造任务聚合（{@link Task#initTask} 以 NOT_START/progress=0% 落库）→ 存盘。
 * relay 链路在转发上游成功后调用本用例落库一条 Task 行（PRD AT-1）。计费上下文（预扣额度/按次标志/
 * 计费来源）由 relay 提交链路注入，作为后续退款结算依据。薄编排，状态机/校验在领域聚合（充血）。</p>
 *
 * <p><b>附着 relay 链路</b>：本用例为 relay 提交各平台异步任务（MJ/Suno/视频）后落库的统一入口，
 * relay 的 application 用例调用 {@code submit(...)} 完成 InitTask（与 relay BC 经此应用服务解耦，
 * task BC 不反向依赖 relay）。</p>
 */
@Service
public class SubmitTaskUseCase {

    private final TaskRepository taskRepository;

    /** @param taskRepository 任务仓储 */
    public SubmitTaskUseCase(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    /**
     * 提交任务并落库（InitTask，F-2001）。
     *
     * @param command 提交命令（task_id/平台/用户/动作/渠道/预扣额度/计费上下文）
     * @return 持久化后的任务聚合（含 id，status=NOT_START）
     */
    public Task submit(SubmitTaskCommand command) {
        long now = Instant.now().getEpochSecond();
        BillingContext ctx = BillingContext.of(
                command.perCallBilling(),
                BillingContext.BillingSource.fromWire(command.billingSource()),
                command.preConsumedQuota());
        Task task = Task.initTask(
                command.taskId(),
                TaskPlatform.fromWire(command.platform()),
                command.userId(),
                command.group(),
                command.channelId(),
                command.preConsumedQuota(),
                command.action(),
                ctx,
                now);
        return taskRepository.save(task);
    }
}
