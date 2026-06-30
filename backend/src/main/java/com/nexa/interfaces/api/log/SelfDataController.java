package com.nexa.interfaces.api.log;

import com.nexa.application.log.QuerySelfQuotaDataUseCase;
import com.nexa.shared.web.ApiResponse;
import com.nexa.interfaces.api.log.dto.QuotaDataItemVO;
import com.nexa.shared.security.rbac.AuthenticatedActor;
import com.nexa.shared.security.rbac.AuthLevel;
import com.nexa.shared.security.annotation.CurrentActor;
import com.nexa.shared.security.annotation.RequireRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户自助配额按日数据控制器（sessionAuth 端点，接口层，F-4009 GET /api/data/self）。
 *
 * <p>对齐 openapi {@code GET /api/data/self}：本人按日配额数据。强制 self-scope（user_id 取自
 * {@code @CurrentActor}，不读请求参数）+ 时间跨度上限 1 个月（领域护栏 TimeRange，超限抛
 * {@code InvalidLogQueryException}「时间跨度不能超过 1 个月」→ 400）。</p>
 *
 * <p><b>鉴权</b>：类级 {@link RequireRole}({@link AuthLevel#USER})，未认证 → 401。</p>
 */
@RestController
@RequestMapping("/api/data/self")
@RequireRole(AuthLevel.USER)
public class SelfDataController {

    private final QuerySelfQuotaDataUseCase querySelfQuotaDataUseCase;

    /** @param querySelfQuotaDataUseCase 自助配额按日用例（F-4009） */
    public SelfDataController(QuerySelfQuotaDataUseCase querySelfQuotaDataUseCase) {
        this.querySelfQuotaDataUseCase = querySelfQuotaDataUseCase;
    }

    /**
     * 自助配额按日数据（F-4009，{@code GET /api/data/self}，跨度上限 1 月，强制本人）。
     *
     * @param startTimestamp 起始 epoch 秒（可空→end-1月）
     * @param endTimestamp   结束 epoch 秒（可空→now）
     * @param actor          认证主体（self-scope user_id）
     * @return 成功信封，data = 本人 QuotaDataItem 数组（按日期升序）
     */
    @GetMapping
    public ApiResponse<List<QuotaDataItemVO>> selfQuotaByDay(
            @RequestParam(name = "start_timestamp", required = false) Long startTimestamp,
            @RequestParam(name = "end_timestamp", required = false) Long endTimestamp,
            @CurrentActor AuthenticatedActor actor) {

        List<QuotaDataItemVO> data = querySelfQuotaDataUseCase.query(actor.userId(), startTimestamp, endTimestamp)
                .stream().map(QuotaDataItemVO::from).toList();
        return ApiResponse.okData(data);
    }
}
