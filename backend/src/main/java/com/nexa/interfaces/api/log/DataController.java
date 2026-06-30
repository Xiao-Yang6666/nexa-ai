package com.nexa.interfaces.api.log;

import com.nexa.application.log.QueryAdminQuotaDataUseCase;
import com.nexa.common.web.ApiResponse;
import com.nexa.interfaces.api.log.dto.QuotaDataItemVO;
import com.nexa.common.security.domain.rbac.AuthLevel;
import com.nexa.common.security.interfaces.annotation.RequireRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端配额按日数据控制器（adminAuth 端点，接口层，F-4007/F-4008 GET /api/data/）。
 *
 * <p>对齐 openapi {@code GET /api/data/}：按日聚合配额（QuotaDataItem 数组）。可按 username 过滤
 * 指定用户（空=全站，F-4007）；F-4008（按用户聚合）由同一聚合查询的 username 维度 + (日,model) 分组覆盖。</p>
 *
 * <p><b>鉴权</b>：类级 {@link RequireRole}({@link AuthLevel#ADMIN})，未达 admin → 403。管理端全站可见，
 * 无 self-scope。数据为按日聚合用售价 quota，不含成本/利润。</p>
 */
@RestController
@RequestMapping("/api/data")
@RequireRole(AuthLevel.ADMIN)
public class DataController {

    private final QueryAdminQuotaDataUseCase queryAdminQuotaDataUseCase;

    /** @param queryAdminQuotaDataUseCase 管理端配额按日用例（F-4007/F-4008） */
    public DataController(QueryAdminQuotaDataUseCase queryAdminQuotaDataUseCase) {
        this.queryAdminQuotaDataUseCase = queryAdminQuotaDataUseCase;
    }

    /**
     * 管理端配额按日数据（F-4007，{@code GET /api/data/}，可按 username）。
     *
     * @param startTimestamp 起始 epoch 秒（可空=不限）
     * @param endTimestamp   结束 epoch 秒（可空=不限）
     * @param username       指定用户名过滤（空/null=全站）
     * @return 成功信封，data = QuotaDataItem 数组（按日期升序）
     */
    @GetMapping("/")
    public ApiResponse<List<QuotaDataItemVO>> quotaByDay(
            @RequestParam(name = "start_timestamp", required = false) Long startTimestamp,
            @RequestParam(name = "end_timestamp", required = false) Long endTimestamp,
            @RequestParam(name = "username", required = false) String username) {

        List<QuotaDataItemVO> data = queryAdminQuotaDataUseCase.query(startTimestamp, endTimestamp, username)
                .stream().map(QuotaDataItemVO::from).toList();
        return ApiResponse.okData(data);
    }
}
