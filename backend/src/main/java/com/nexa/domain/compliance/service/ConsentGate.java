package com.nexa.domain.compliance.service;

import com.nexa.domain.compliance.exception.ConsentRequiredException;
import com.nexa.domain.compliance.vo.Consent;

import java.util.Objects;

/**
 * 同意闸门领域服务（F-5021 隐私政策与出境条款同意闸门，DC-010，纯领域规则零框架依赖）。
 *
 * <p>调用前置闸门的领域判定：给定「用户的同意记录」与「当前生效条款版本」，判定是否放行需同意的调用。
 * 领域规则来源：API-ENDPOINTS §14.5 F-5021「未接受含出境与留存条款的协议不可调用」、
 * Compliance 验收「未同意协议拒绝调用」。把「同意是否有效」的判定收敛到此领域服务（backend-engineer §2.4），
 * 接口层中间件 / 调用前置拦截器只调 {@link #requireConsent}，不在外部散落版本比较。</p>
 *
 * <p>落地形态：本服务被「调用前置中间件」（拦截需同意的业务端点，如下单 / 调用转发）使用——
 * 中间件读取用户同意记录（来源由基础设施提供：用户 setting / 专用同意表 / Option 关联）与当前条款版本
 * （Option {@code compliance.consent_terms_version}），调用本服务，未通过则中断请求返回 403 引导同意。
 * 与 §9.5 支付合规确认（{@code POST /api/option/payment_compliance}）关联：支付合规是 root 侧站点级确认，
 * 本闸门是用户侧逐用户同意，二者共同构成 DC-010 的同意体系。</p>
 */
public final class ConsentGate {

    private ConsentGate() {
    }

    /**
     * 判断用户当前同意状态是否满足放行条件（不抛异常的查询版）。
     *
     * @param consent             用户同意记录（{@code null} 视为从未同意）
     * @param currentTermsVersion 当前生效条款版本
     * @return 已有效同意返回 {@code true}
     */
    public static boolean isSatisfied(Consent consent, String currentTermsVersion) {
        Consent c = consent == null ? Consent.none() : consent;
        return c.isValidFor(currentTermsVersion);
    }

    /**
     * 断言用户已有效同意当前条款，否则拒绝调用（命令版护栏，闸门入口）。
     *
     * <p>领域规则：用户同意记录必须 {@link Consent#isValidFor} 当前生效条款版本——既要曾同意，
     * 又要同意的版本与当前版本一致（条款升级后需重新同意）。不满足抛 {@link ConsentRequiredException}
     * （→ 中间件返回 403，引导先同意）。</p>
     *
     * @param consent             用户同意记录（{@code null} 视为从未同意）
     * @param currentTermsVersion 当前生效条款版本（非空白）
     * @throws ConsentRequiredException 未同意或同意版本过期
     * @throws NullPointerException     currentTermsVersion 为 null
     */
    public static void requireConsent(Consent consent, String currentTermsVersion) {
        Objects.requireNonNull(currentTermsVersion, "currentTermsVersion");
        if (!isSatisfied(consent, currentTermsVersion)) {
            // 不泄露具体版本号差异，仅给稳定面向用户的提示（引导前往同意页）。
            throw new ConsentRequiredException(
                    "请先阅读并同意包含数据出境与留存条款的隐私政策后再继续");
        }
    }
}
