package com.nexa.growth.application;

import com.nexa.growth.application.port.UserQuotaAccount;
import com.nexa.growth.domain.exception.AlreadyCheckedInException;
import com.nexa.growth.domain.exception.CheckinDisabledException;
import com.nexa.growth.domain.model.Checkin;
import com.nexa.growth.domain.repository.CheckinRepository;
import com.nexa.growth.domain.repository.CheckinSettingRepository;
import com.nexa.growth.domain.vo.CheckinDate;
import com.nexa.growth.domain.vo.CheckinSetting;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/**
 * 每日签到用例（PRD GR-1 每日签到状态机，F-1046/F-1048/F-1050）。
 *
 * <p>应用层用例：薄编排 + 事务边界，领域规则在值对象 {@link CheckinSetting}（启用判定 + 区间随机奖励）
 * 与聚合 {@link Checkin}（记录装配）上（backend-engineer §2.1 应用层不含领域逻辑）。本用例在<b>同一本地
 * 事务</b>内完成「写 Checkin 记录 + user.quota += quota_awarded」，保证原子（PRD GR-1 §5「记录写入与额度
 * 增加原子」）。Turnstile 人机校验（F-1050）属接口层前置中间件，不在本用例内。</p>
 *
 * <p>并发同日重复签到（GR-1 §4）：先按 {@code (user_id, today)} 查重快速短路；并发穿过查重的第二笔由
 * 仓储命中复合唯一索引 {@code idx_user_checkin_date}，被转换为 {@link AlreadyCheckedInException}——
 * 保证「仅一笔成功，quota 只增一次」（PRD AC「并发两次签到仅一笔成功」）。</p>
 */
@Service
public class DailyCheckinUseCase {

    private final CheckinSettingRepository settingRepository;
    private final CheckinRepository checkinRepository;
    private final UserQuotaAccount userQuotaAccount;
    private final Clock clock;

    /**
     * @param settingRepository 签到配置仓储（读启用开关与区间）
     * @param checkinRepository 签到记录仓储（查重 + 落库）
     * @param userQuotaAccount  用户额度账户端口（同事务入账）
     * @param clock             时钟（注入便于单测固定「今日」；生产为系统时区时钟）
     */
    public DailyCheckinUseCase(CheckinSettingRepository settingRepository,
                               CheckinRepository checkinRepository,
                               UserQuotaAccount userQuotaAccount,
                               Clock clock) {
        this.settingRepository = settingRepository;
        this.checkinRepository = checkinRepository;
        this.userQuotaAccount = userQuotaAccount;
        this.clock = clock;
    }

    /**
     * 执行签到：校验启用 → 查重 → 抽随机奖励 → 写记录 + 入账（同事务）。
     *
     * <p>流程对应 GR-1 主流程 1~4：① 配置 {@code enabled=false} → {@link CheckinDisabledException}（未启用终态）；
     * ② 当日已有记录 → {@link AlreadyCheckedInException}（今日已签到态）；③ 否则按配置区间抽随机额度、写
     * 记录、给 user.quota 入账等额，返回本次发放额度。</p>
     *
     * @param userId 签到用户 id（认证主体注入，self-scope）
     * @return 本次签到结果（发放额度，接口层据此组装 CheckinResult）
     * @throws CheckinDisabledException  签到功能未启用
     * @throws AlreadyCheckedInException 今日已签到（查重或并发唯一索引拦截）
     */
    @Transactional
    public CheckinResult checkin(long userId) {
        CheckinSetting setting = settingRepository.load();
        if (!setting.enabled()) {
            throw new CheckinDisabledException(); // GR-1 未启用终态
        }

        LocalDate today = LocalDate.now(clock);
        CheckinDate todayVo = CheckinDate.of(today);

        // 快速短路：当日已签直接拒（GR-1 已签到态）。并发穿透由下方唯一索引兜底。
        if (checkinRepository.existsByUserIdAndDate(userId, todayVo)) {
            throw new AlreadyCheckedInException();
        }

        long reward = setting.drawReward();           // 配置区间随机奖励（领域行为）
        long now = clock.instant().getEpochSecond();
        Checkin checkin = Checkin.create(userId, todayVo, reward, now);

        // 先落记录（命中唯一索引 → save 内转为 AlreadyCheckedInException，本次事务回滚不入账）。
        checkinRepository.save(checkin);
        // 再原子入账等额到可用余额（同事务，保证记录与额度一致）。
        userQuotaAccount.credit(userId, reward);

        return new CheckinResult(reward);
    }

    /**
     * 签到结果载体（应用层 → 接口层，接口层据此组装 openapi {@code CheckinResult}）。
     *
     * @param quotaAwarded 本次发放的随机奖励额度
     */
    public record CheckinResult(long quotaAwarded) {
    }
}
