package com.nexa.growth.infrastructure.event;

import com.nexa.account.domain.event.UserRegistered;
import com.nexa.growth.application.CreditInviterRewardUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 邀请返利入账订阅者（基础设施层，PRD GR-4 «返利入账» 回调，F-1042/F-1043）。
 *
 * <p>跨 bounded context 通过领域事件解耦：账号域（com.nexa.account）在新用户注册/OAuth 建号成功后发布
 * {@link UserRegistered}（已回填新用户 id + 解析出的 inviterId）。增长域在此订阅，对有效邀请人入账返利
 * （委托 {@link CreditInviterRewardUseCase}），完成 GR-4 I13/I14 的「邀请人 AffCount++/AffQuota 增」回调。
 * 增长域不反向依赖账号域用例，只消费其发布的领域事件（事件即跨域契约）。</p>
 *
 * <p><b>事务相位</b>：用 {@link TransactionalEventListener}（{@code AFTER_COMMIT}）——注册主事务提交后才
 * 入账返利。理由：返利是注册的<b>副作用</b>，绝不能因返利入账失败回滚被邀请人的注册主流程（被邀请人
 * 该建号成功）；返利入账自带独立短事务（用例 {@code @Transactional}）。无效归因（inviterId&lt;=0）由用例
 * 内部短路。入账异常只记日志、不上抛（不污染已提交的注册流程），单笔失败不影响系统可用性。</p>
 */
@Component
public class InviterRewardOnUserRegistered {

    private static final Logger log = LoggerFactory.getLogger(InviterRewardOnUserRegistered.class);

    private final CreditInviterRewardUseCase creditInviterRewardUseCase;

    /** @param creditInviterRewardUseCase 邀请返利入账用例 */
    public InviterRewardOnUserRegistered(CreditInviterRewardUseCase creditInviterRewardUseCase) {
        this.creditInviterRewardUseCase = creditInviterRewardUseCase;
    }

    /**
     * 注册主事务提交后，对有效邀请人入账返利（GR-4 回调）。
     *
     * <p>{@code AFTER_COMMIT} 相位保证被邀请人已成功落库；随后给邀请人入账。捕获异常仅记日志——
     * 不让一次返利失败反噬已完成的注册（副作用与主流程隔离）。</p>
     *
     * @param event 用户已注册领域事件（携带 inviterId）
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserRegistered(UserRegistered event) {
        try {
            boolean credited = creditInviterRewardUseCase.creditFor(event.inviterId());
            if (credited) {
                log.info("inviter reward credited: inviterId={}, newUserId={}",
                        event.inviterId(), event.userId());
            }
        } catch (RuntimeException ex) {
            // 不上抛：注册已提交，返利是副作用，单笔入账失败不应影响系统。保留错误链记日志便于排查。
            log.warn("credit inviter reward failed for inviterId={}, newUserId={}: {}",
                    event.inviterId(), event.userId(), ex.getMessage(), ex);
        }
    }
}
