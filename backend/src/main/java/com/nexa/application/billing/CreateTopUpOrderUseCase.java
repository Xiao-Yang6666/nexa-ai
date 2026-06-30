package com.nexa.application.billing;

import com.nexa.application.billing.port.PaymentGateway;
import com.nexa.domain.billing.model.TopUp;
import com.nexa.domain.billing.repository.TopUpRepository;
import com.nexa.domain.billing.vo.Money;
import com.nexa.domain.billing.vo.Quota;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import com.nexa.application.billing.command.CreateTopUpCommand;
import com.nexa.application.billing.result.CreateTopUpResult;

/**
 * 发起充值下单用例（prd-billing BL-1，F-2044）。
 *
 * <p>应用层用例：创建本地 {@code TopUp}（pending）写全局唯一订单号 → 调支付渠道创建收银台会话
 * → 返回跳转信息（BL-1 §3 pay_order→pay_jump）。<b>不入账</b>——额度入账只在回调验签通过后
 * （{@link HandlePaymentCallbackUseCase}）。合规闸门（C7）由上游/接口层保证已过。</p>
 */
@Service
public class CreateTopUpOrderUseCase {

    private final TopUpRepository topUpRepository;
    private final PaymentGateway paymentGateway;

    /**
     * @param topUpRepository 充值订单仓储
     * @param paymentGateway  支付渠道端口
     */
    public CreateTopUpOrderUseCase(TopUpRepository topUpRepository, PaymentGateway paymentGateway) {
        this.topUpRepository = topUpRepository;
        this.paymentGateway = paymentGateway;
    }

    /**
     * 发起充值下单。
     *
     * @param userId  充值用户 id（认证主体注入）
     * @param command 下单命令（额度/金额/支付方式/渠道）
     * @return 下单结果（订单号 + 收银台跳转信息，status=pending）
     */
    @Transactional
    public CreateTopUpResult create(long userId, CreateTopUpCommand command) {
        // 生成全局唯一商户订单号（幂等键）；UUID 去横线，前缀标识充值单。
        String tradeNo = "TP" + UUID.randomUUID().toString().replace("-", "");
        long now = Instant.now().getEpochSecond();

        Quota amount = Quota.of(command.amount() == null ? 0L : command.amount());
        Money money = command.money() == null ? Money.ZERO : Money.of(command.money());

        TopUp order = TopUp.createOrder((int) userId, amount, money, tradeNo,
                command.paymentMethod(), command.paymentProvider(), now);
        topUpRepository.save(order);

        // 调支付渠道创建收银台会话（验签发生在回调侧；此处只取跳转信息）。
        PaymentGateway.PaymentSession session = paymentGateway.createSession(
                tradeNo, command.paymentProvider(),
                Map.of("amount", amount.value(), "money", money.value(), "user_id", userId));

        return new CreateTopUpResult(tradeNo, session.payUrl(), session.payParams(),
                order.status().code());
    }
}
