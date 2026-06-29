package com.nexa.domain.deployment.vo;

import com.nexa.domain.deployment.exception.InvalidDeploymentParameterException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link HardwareId} / {@link GpuCount} / {@link ClusterName} / {@link DeploymentId} / {@link ContainerId}
 * 值对象单测（纯 JUnit，零 Spring）。
 *
 * <p>覆盖部署管理入参校验规则（API-ENDPOINTS §10.3/§10.4），按正常/边界/异常组织
 * （backend-engineer §3.3）。重点验证「缺失」与「非法」两类错误文案严格区分（契约固定文案）。</p>
 */
@DisplayName("部署值对象")
class DeploymentValueObjectsTest {

    // ---- HardwareId（F-3051：缺失→required；非法/<=0→invalid）----

    @Test
    @DisplayName("HardwareId.parse：正常正整数")
    void hardwareIdNormal() {
        assertEquals(42L, HardwareId.parse("42").value());
        assertEquals(7L, HardwareId.parse("  7 ").value(), "应 trim 空白");
    }

    @Test
    @DisplayName("HardwareId.parse：缺失（null/空白）→ required 文案")
    void hardwareIdMissing() {
        InvalidDeploymentParameterException e1 =
                assertThrows(InvalidDeploymentParameterException.class, () -> HardwareId.parse(null));
        assertEquals("hardware_id parameter is required", e1.getMessage());
        assertThrows(InvalidDeploymentParameterException.class, () -> HardwareId.parse("   "));
    }

    @Test
    @DisplayName("HardwareId.parse：非数字/<=0 → invalid 文案（不泄露解析异常）")
    void hardwareIdInvalid() {
        InvalidDeploymentParameterException e1 =
                assertThrows(InvalidDeploymentParameterException.class, () -> HardwareId.parse("abc"));
        assertEquals("invalid hardware_id parameter", e1.getMessage());
        assertThrows(InvalidDeploymentParameterException.class, () -> HardwareId.parse("0"));
        assertThrows(InvalidDeploymentParameterException.class, () -> HardwareId.parse("-3"));
    }

    // ---- GpuCount（F-3051：非正回退 1，不报错）----

    @Test
    @DisplayName("GpuCount.ofOrDefault：正常正数原值")
    void gpuCountNormal() {
        assertEquals(4, GpuCount.ofOrDefault(4).value());
    }

    @Test
    @DisplayName("GpuCount.ofOrDefault：null/非正 → 回退 1（宽松归一）")
    void gpuCountDefault() {
        assertEquals(1, GpuCount.ofOrDefault(null).value());
        assertEquals(1, GpuCount.ofOrDefault(0).value());
        assertEquals(1, GpuCount.ofOrDefault(-2).value());
    }

    // ---- ClusterName（F-3053：空→name parameter is required）----

    @Test
    @DisplayName("ClusterName：正常 trim；空白→ required 文案")
    void clusterName() {
        assertEquals("my-cluster", new ClusterName("  my-cluster ").value());
        InvalidDeploymentParameterException e =
                assertThrows(InvalidDeploymentParameterException.class, () -> new ClusterName(" "));
        assertEquals("name parameter is required", e.getMessage());
        assertThrows(InvalidDeploymentParameterException.class, () -> new ClusterName(null));
    }

    // ---- DeploymentId（F-3043：空→deployment ID is required）----

    @Test
    @DisplayName("DeploymentId：正常 trim；空白→ deployment ID is required")
    void deploymentId() {
        assertEquals("dep-1", new DeploymentId(" dep-1 ").value());
        InvalidDeploymentParameterException e =
                assertThrows(InvalidDeploymentParameterException.class, () -> new DeploymentId(""));
        assertEquals("deployment ID is required", e.getMessage());
    }

    // ---- ContainerId（F-3055 详情 vs F-3056 日志，文案不同）----

    @Test
    @DisplayName("ContainerId.forDetail：空→ container_id is required（path 语义）")
    void containerIdDetail() {
        assertEquals("c-1", ContainerId.forDetail("c-1").value());
        InvalidDeploymentParameterException e =
                assertThrows(InvalidDeploymentParameterException.class, () -> ContainerId.forDetail(null));
        assertEquals("container_id is required", e.getMessage());
    }

    @Test
    @DisplayName("ContainerId.forLogs：空→ container_id parameter is required（query 语义）")
    void containerIdLogs() {
        assertEquals("c-2", ContainerId.forLogs(" c-2 ").value());
        InvalidDeploymentParameterException e =
                assertThrows(InvalidDeploymentParameterException.class, () -> ContainerId.forLogs("  "));
        assertEquals("container_id parameter is required", e.getMessage());
    }
}
