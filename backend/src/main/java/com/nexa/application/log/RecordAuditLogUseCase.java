package com.nexa.application.log;

import com.nexa.domain.log.model.LogEntry;
import com.nexa.domain.log.repository.LogRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 审计日志记录用例（应用层横切服务，F-4011/F-4012/F-4013）。
 *
 * <p>「横切/系统内部」能力（API-COVERAGE 标 REST=N）：不挂独立 HTTP 端点，由其他 bounded context
 * （ops 域改系统选项、account 域 passkey 绑定/登录）在高危/安全/登录操作后调用本服务落审计日志。
 * 这样审计写入的领域规则（type=Manage/Login、who/what/when/where、敏感 value 不入库）集中在 log BC，
 * 不散落到各调用域（单一职责 + 可见性铁律：F-4018 系统选项更新仅记 key 不记 value，由调用方渲染 content 时保证）。</p>
 *
 * <p>领域规则来源：prd 日志与用量 F-4011「管理/高危操作 type=3，记 action 模板渲染 content」、
 * F-4012「用户安全敏感操作（passkey 绑定/解绑）adminInfo 为 nil」、F-4013「登录 type=7，记 username + client IP」。</p>
 */
@Service
public class RecordAuditLogUseCase {

    private final LogRepository logRepository;

    /** @param logRepository 日志查询/审计仓储 */
    public RecordAuditLogUseCase(LogRepository logRepository) {
        this.logRepository = logRepository;
    }

    /**
     * 记录管理/高危操作审计（F-4011，Type=3 Manage）。
     *
     * @param operatorUserId  操作者（管理员）用户 id
     * @param operatorName    操作者用户名
     * @param renderedContent action 模板渲染后的内容（调用方保证不含敏感 value，如系统选项仅记 key）
     * @param ip              操作来源 IP
     */
    public void recordManage(long operatorUserId, String operatorName, String renderedContent, String ip) {
        long now = Instant.now().getEpochSecond();
        logRepository.recordAudit(
                LogEntry.manageAudit(operatorUserId, operatorName, renderedContent, ip, now));
    }

    /**
     * 记录用户安全敏感操作审计（F-4012，Type=3 Manage，无管理员归属）。
     *
     * @param userId          用户本人 id
     * @param username        用户本人用户名
     * @param renderedContent 渲染内容（如 "Registered a passkey"）
     * @param ip              来源 IP
     */
    public void recordSecurity(long userId, String username, String renderedContent, String ip) {
        long now = Instant.now().getEpochSecond();
        logRepository.recordAudit(
                LogEntry.securityAudit(userId, username, renderedContent, ip, now));
    }

    /**
     * 记录登录审计（F-4013，Type=7 Login）。
     *
     * @param userId   登录用户 id
     * @param username 登录用户名
     * @param ip       客户端 IP
     * @param content  结构化登录描述（如 "login via password"）
     */
    public void recordLogin(long userId, String username, String ip, String content) {
        long now = Instant.now().getEpochSecond();
        logRepository.recordAudit(
                LogEntry.loginAudit(userId, username, ip, content, now));
    }
}
