package com.nexa.infrastructure.billing.persistence.po;

import com.nexa.domain.billing.model.BalanceTransaction;
import com.nexa.domain.billing.vo.BalanceTransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link BalanceTransactionPO} 就近映射往返完整性测试（P1 行为等价，纯内存无 Spring/DB）。
 *
 * <p>护栏：方案 1（手写就近映射）唯一硬伤是「加字段忘同步映射、运行期才炸」。本测试断言
 * {@code domain → PO → domain} 往返在所有持久化字段上保持等价，任一字段漏映射即在此暴露。
 * 作为后续各域往返测试的复制模板。</p>
 *
 * <p>Validates: Requirements 5.1, 5.2, 5.3。</p>
 */
@DisplayName("BalanceTransactionPO 映射往返完整性")
class BalanceTransactionPOMappingTest {

    @Test
    @DisplayName("全字段往返保持等价（含枚举 wire 码）")
    void roundTrip_preservesAllFields() {
        BalanceTransaction origin = BalanceTransaction.rehydrate(
                42L, 7L, BalanceTransactionType.ADMIN_CREDIT,
                1000L, 5000L, 99L, "manual topup", 1_700_000_000L);

        BalanceTransaction roundTripped = BalanceTransactionPO.of(origin).toDomain();

        assertThat(roundTripped.id()).isEqualTo(origin.id());
        assertThat(roundTripped.userId()).isEqualTo(origin.userId());
        assertThat(roundTripped.type()).isEqualTo(origin.type());
        assertThat(roundTripped.amount()).isEqualTo(origin.amount());
        assertThat(roundTripped.balanceAfter()).isEqualTo(origin.balanceAfter());
        assertThat(roundTripped.operatorId()).isEqualTo(origin.operatorId());
        assertThat(roundTripped.remark()).isEqualTo(origin.remark());
        assertThat(roundTripped.createdTime()).isEqualTo(origin.createdTime());
    }

    @Test
    @DisplayName("负额扣费 + 可空 operatorId/remark 往返保持")
    void roundTrip_handlesNegativeAmountAndNullableFields() {
        BalanceTransaction origin = BalanceTransaction.rehydrate(
                1L, 7L, BalanceTransactionType.ADMIN_DEBIT,
                -2000L, 0L, null, null, 1_700_000_001L);

        BalanceTransaction roundTripped = BalanceTransactionPO.of(origin).toDomain();

        assertThat(roundTripped.amount()).isEqualTo(-2000L);
        assertThat(roundTripped.operatorId()).isNull();
        assertThat(roundTripped.remark()).isNull();
        assertThat(roundTripped.type()).isEqualTo(BalanceTransactionType.ADMIN_DEBIT);
    }

    @Test
    @DisplayName("数值列 null → 0L 兜底（toDomain 方向）")
    void toDomain_nullNumericColumnsDefaultToZero() {
        BalanceTransactionPO po = new BalanceTransactionPO();
        po.setId(5L);
        po.setType("REDEEM");
        // userId/amount/balanceAfter/createdTime 全留 null，模拟历史数据/默认列。

        BalanceTransaction domain = po.toDomain();

        assertThat(domain.userId()).isZero();
        assertThat(domain.amount()).isZero();
        assertThat(domain.balanceAfter()).isZero();
        assertThat(domain.createdTime()).isZero();
        assertThat(domain.type()).isEqualTo(BalanceTransactionType.REDEEM);
    }
}
