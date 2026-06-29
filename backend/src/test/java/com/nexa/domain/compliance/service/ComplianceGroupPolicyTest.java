package com.nexa.domain.compliance.service;

import com.nexa.domain.compliance.exception.CrossBorderRoutingDeniedException;
import com.nexa.domain.compliance.vo.DataResidency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ComplianceGroupPolicy} + {@link DataResidency} 单测（纯 JUnit）——F-5018/F-5019。
 *
 * <p>验证「合规分组仅命中境内渠道、剔除境外候选、选中境外则拒绝」（验收「合规分组请求不命中境外渠道」）。</p>
 */
@DisplayName("合规分组选渠")
class ComplianceGroupPolicyTest {

    private final DataResidency cn = DataResidency.domestic("cn-east");
    private final DataResidency us = DataResidency.overseas("us-west");

    @Test
    @DisplayName("分组识别：compliance/domestic-only（大小写不敏感）为合规分组")
    void groupRecognition() {
        assertTrue(ComplianceGroupPolicy.isComplianceGroup("compliance"));
        assertTrue(ComplianceGroupPolicy.isComplianceGroup("Domestic-Only"));
        assertFalse(ComplianceGroupPolicy.isComplianceGroup("default"));
        assertFalse(ComplianceGroupPolicy.isComplianceGroup(null));
    }

    @Test
    @DisplayName("渠道放行：合规分组仅放行境内；非合规分组不限制")
    void channelAllowance() {
        assertTrue(ComplianceGroupPolicy.isChannelAllowedForGroup("compliance", cn));
        assertFalse(ComplianceGroupPolicy.isChannelAllowedForGroup("compliance", us), "合规分组拒境外");
        assertTrue(ComplianceGroupPolicy.isChannelAllowedForGroup("default", us), "非合规分组放行境外");
    }

    @Test
    @DisplayName("候选过滤：合规分组剔除境外候选；非合规分组原样返回")
    void filterCandidates() {
        record Ch(DataResidency r) {
        }
        List<Ch> all = List.of(new Ch(cn), new Ch(us), new Ch(cn));

        List<Ch> compliant = ComplianceGroupPolicy.filterAllowed("compliance", all, Ch::r);
        assertEquals(2, compliant.size(), "合规分组只剩 2 个境内");
        assertTrue(compliant.stream().allMatch(c -> c.r().isDomestic()));

        List<Ch> noFilter = ComplianceGroupPolicy.filterAllowed("default", all, Ch::r);
        assertEquals(3, noFilter.size(), "非合规分组不过滤");
    }

    @Test
    @DisplayName("收尾护栏：合规分组选中境外 → 抛异常；境内/非合规分组放行")
    void assertNotCrossBorder() {
        assertThrows(CrossBorderRoutingDeniedException.class,
                () -> ComplianceGroupPolicy.assertNotCrossBorder("compliance", us));
        assertDoesNotThrow(() -> ComplianceGroupPolicy.assertNotCrossBorder("compliance", cn));
        assertDoesNotThrow(() -> ComplianceGroupPolicy.assertNotCrossBorder("default", us));
    }

    @Test
    @DisplayName("DataResidency 语义与展示")
    void residencySemantics() {
        assertTrue(cn.isDomestic());
        assertFalse(cn.crossesBorder());
        assertTrue(us.crossesBorder());
        assertTrue(us.displayLabel().startsWith("境外"));
        assertThrows(IllegalArgumentException.class, () -> DataResidency.domestic("  "));
    }
}
