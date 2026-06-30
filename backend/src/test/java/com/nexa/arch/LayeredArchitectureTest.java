package com.nexa.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 四层架构依赖守护（Phase 4，ArchUnit）。
 *
 * <p>翻转为「四层优先」结构后，编译器不再阻止 {@code infrastructure} 直接 import 别域
 * {@code domain}，依赖方向只能靠测试固化。本测试固化两类铁律：
 * <ol>
 *   <li><b>层依赖单向</b>：domain 不依赖任何上层；application 不依赖 interfaces/infrastructure；
 *       interfaces 不依赖 infrastructure。</li>
 *   <li><b>common 是底座</b>：common 不依赖任何业务层。</li>
 * </ol>
 *
 * <p>已知历史违规（结构翻转暴露的既有债，待单独整改，不在本次结构重构中盲改）：
 * <ul>
 *   <li>{@code application.growth.*UseCase} → {@code infrastructure.growth.config.GrowthProperties}
 *       （@ConfigurationProperties 错放 infra，应下沉为 application port）</li>
 *   <li>{@code application.model.QueryPublicPricingUseCase} → {@code interfaces.api.model.dto.PricingPublicVO}
 *       （用例直接产出 interfaces 层 VO，应在 interfaces 层组装）</li>
 * </ul>
 * 这些用 {@code .ignoreDependency} 显式豁免——护栏对新代码立即生效，旧债显式可见。</p>
 */
@DisplayName("四层架构依赖守护")
class LayeredArchitectureTest {

    private static final String BASE = "com.nexa";
    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(BASE);
    }

    @Test
    @DisplayName("domain 层不得依赖 interfaces/application/infrastructure")
    void domainDependsOnNoUpperLayer() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.nexa.domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.nexa.interfaces..", "com.nexa.application..", "com.nexa.infrastructure..");
        rule.check(classes);
    }

    @Test
    @DisplayName("application 层不得依赖 interfaces/infrastructure（已知 3 处历史债豁免）")
    void applicationDependsOnNoInterfacesOrInfrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.nexa.application..")
                // 已知历史债（结构翻转暴露），待单独整改，先从规则范围排除以让护栏对其余代码即时生效：
                //  - application.growth.{CreditInviterReward,TransferAffQuota}UseCase -> infrastructure.growth.config.GrowthProperties
                //  - application.model.QueryPublicPricingUseCase -> interfaces.api.model.dto.PricingPublicVO
                .and().areNotAssignableTo(com.nexa.application.growth.CreditInviterRewardUseCase.class)
                .and().areNotAssignableTo(com.nexa.application.growth.TransferAffQuotaUseCase.class)
                .and().areNotAssignableTo(com.nexa.application.model.QueryPublicPricingUseCase.class)
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.nexa.interfaces..", "com.nexa.infrastructure..");
        rule.check(classes);
    }

    @Test
    @DisplayName("interfaces 层不得依赖 infrastructure（已知 2 处历史债豁免）")
    void interfacesDependsOnNoInfrastructure() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.nexa.interfaces..")
                // 已知历史债（结构翻转暴露），待单独整改：
                //  - interfaces.api.observability.MetricsController -> infrastructure.observability.metrics.PrometheusTextRenderer
                //  - interfaces.api.relay.RelayController -> infrastructure.relay.auth.RelayApiKeyAuthentication
                .and().areNotAssignableTo(com.nexa.interfaces.api.observability.MetricsController.class)
                .and().areNotAssignableTo(com.nexa.interfaces.api.relay.RelayController.class)
                .should().dependOnClassesThat()
                .resideInAPackage("com.nexa.infrastructure..");
        rule.check(classes);
    }

    @Test
    @DisplayName("common 底座不得依赖任何业务层")
    void commonDependsOnNoBusinessLayer() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..common..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.nexa.interfaces..",
                        "com.nexa.application..",
                        "com.nexa.domain..",
                        "com.nexa.infrastructure..");
        rule.check(classes);
    }
}
