package com.nexa.application.telegram;

import com.nexa.domain.account.model.User;
import com.nexa.domain.account.repository.UserRepository;
import com.nexa.domain.telegram.exception.InvalidTelegramAuthException;
import com.nexa.domain.telegram.exception.TelegramUserNotFoundException;
import com.nexa.domain.telegram.model.TelegramBinding;
import com.nexa.domain.telegram.repository.TelegramBindingRepository;
import com.nexa.domain.telegram.vo.TelegramAuthData;
import com.nexa.domain.telegram.vo.TelegramId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import com.nexa.application.telegram.command.TelegramBindCommand;

/**
 * Telegram 绑定到现有账号用例（应用服务，F-1052 + F-1054）。
 *
 * <p>编排「已登录用户把当前 Telegram 账号绑到本账号」的事务边界：
 * <ol>
 *   <li><b>HMAC 防伪</b>：复用 {@link TelegramLoginUseCase#verifyAndParse}（同一道开关 + HMAC + 时效校验，
 *       F-1053）确认 Telegram 账号身份真实、未篡改。</li>
 *   <li><b>会话用户存在性</b>：据 {@code bindUserId} 查用户仓储，查无此人 → 拒绝（防御式，会话用户应存在）。</li>
 *   <li><b>唯一性校验（F-1054）</b>：据 telegram_id 反查既有绑定：
 *     <ul>
 *       <li>已绑到<b>他人</b> → {@code ensureOwnedBy} 抛冲突（「该 Telegram 账户已被绑定」），阻止绑定。</li>
 *       <li>已绑到<b>本人</b> → 幂等通过（无需重复建绑定）。</li>
 *       <li>未绑定 → 建一条归属会话用户的新绑定。</li>
 *     </ul>
 *   </li>
 * </ol>
 * 绑定不变量与唯一性护栏在 domain 充血方法（{@link TelegramBinding#create}/{@code ensureOwnedBy}）上，
 * 本类只编排（backend-engineer §2.1）。单事务 + telegram_id 唯一索引兜底并发竞态。</p>
 *
 * <p>对齐 openapi {@code GET /api/oauth/telegram/bind}（F-1052，sessionAuth，成功 302 跳
 * {@code /console/personal}——302 由接口层负责，用例只保证绑定落库）。</p>
 */
@Service
public class TelegramBindUseCase {

    private final TelegramLoginUseCase telegramLoginUseCase;
    private final TelegramBindingRepository telegramBindingRepository;
    private final UserRepository userRepository;

    /**
     * @param telegramLoginUseCase      复用其 HMAC 校验前置（verifyAndParse）
     * @param telegramBindingRepository Telegram 绑定仓储
     * @param userRepository            用户仓储（校验会话用户存在）
     */
    public TelegramBindUseCase(TelegramLoginUseCase telegramLoginUseCase,
                               TelegramBindingRepository telegramBindingRepository,
                               UserRepository userRepository) {
        this.telegramLoginUseCase = telegramLoginUseCase;
        this.telegramBindingRepository = telegramBindingRepository;
        this.userRepository = userRepository;
    }

    /**
     * 执行 Telegram 绑定到现有账号（F-1052/F-1054）。
     *
     * @param command 绑定命令（Login Widget 全部参数 + 会话用户 id）
     * @throws InvalidTelegramAuthException   HMAC 校验失败 / 授权过期 / 未启用 / bindUserId 非法
     * @throws TelegramUserNotFoundException  会话用户不存在
     * @throws com.nexa.domain.telegram.exception.TelegramBindingConflictException
     *         目标 Telegram 账号已被他人绑定（F-1054）
     */
    @Transactional
    public void bind(TelegramBindCommand command) {
        long userId = command.bindUserId();
        if (userId <= 0L) {
            // 绑定必须有合法会话身份（接口层从 @CurrentActor 注入），缺失/非法即拒。
            throw new InvalidTelegramAuthException("telegram bind requires an authenticated user");
        }

        // ① HMAC 防伪（复用登录用例的同一前置校验，F-1053）。
        TelegramAuthData authData = telegramLoginUseCase.verifyAndParse(command.params());
        TelegramId telegramId = authData.telegramId();

        // ② 会话用户存在性（防御式：会话有效但用户被软删的边界）。
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new TelegramUserNotFoundException(
                        "bind target user not found: " + userId));

        // ③ 唯一性校验（F-1054）：telegram_id 已绑他人则冲突，绑本人则幂等。
        Optional<TelegramBinding> existing = telegramBindingRepository.findByTelegramId(telegramId);
        if (existing.isPresent()) {
            // 归属另一用户 → 抛 TelegramBindingConflictException；归属本人 → 幂等通过。
            existing.get().ensureOwnedBy(user.id());
            return;
        }

        // ④ 未绑定 → 建归属会话用户的新绑定（唯一索引并发兜底）。
        TelegramBinding binding = TelegramBinding.create(user.id(), telegramId);
        telegramBindingRepository.save(binding);
    }
}
