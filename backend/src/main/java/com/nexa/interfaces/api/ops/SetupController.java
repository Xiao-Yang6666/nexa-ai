package com.nexa.interfaces.api.ops;

import com.nexa.application.ops.setup.GetSetupStatusUseCase;
import com.nexa.application.ops.setup.SubmitSetupUseCase;
import com.nexa.domain.ops.setup.SetupSubmission;
import com.nexa.common.web.ApiResponse;
import com.nexa.interfaces.api.ops.dto.SetupStatusVO;
import com.nexa.interfaces.api.ops.dto.SetupSubmitRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统初始化控制器（匿名端点，接口层，F-4015/F-4016）。
 *
 * <p>承载首次部署引导端点（对齐 openapi /api/setup）：
 * <ul>
 *   <li>{@code GET  /api/setup} 初始化状态查询（探测，恒成功，F-4015）</li>
 *   <li>{@code POST /api/setup} 初始化提交（创建 root + 模式开关，幂等，F-4016）</li>
 * </ul>
 * </p>
 *
 * <p><b>鉴权（安全声明）</b>：契约要求 {@code /api/setup} = 匿名（首次部署尚无任何用户）。因此本
 * 控制器<b>不</b>加 {@code @RequireRole}。安全护栏改由领域幂等键守护：已初始化后 POST 直接拒绝
 * （{@code SystemAlreadyInitializedException}→409），杜绝匿名端点被用于重复创建 root。</p>
 *
 * <p>DDD 铁律：接口层只做协议翻译（绑定入参→构造领域提交→调用例→裁剪视图），入参校验在
 * {@code SetupSubmission} 领域构造期（充血），领域异常由 {@code OpsExceptionHandler} 翻译。</p>
 */
@RestController
@RequestMapping("/api/setup")
public class SetupController {

    private final GetSetupStatusUseCase getSetupStatusUseCase;
    private final SubmitSetupUseCase submitSetupUseCase;

    /**
     * @param getSetupStatusUseCase 状态查询用例
     * @param submitSetupUseCase    初始化提交用例
     */
    public SetupController(GetSetupStatusUseCase getSetupStatusUseCase,
                           SubmitSetupUseCase submitSetupUseCase) {
        this.getSetupStatusUseCase = getSetupStatusUseCase;
        this.submitSetupUseCase = submitSetupUseCase;
    }

    /**
     * 系统初始化状态查询（F-4015，匿名探测）。
     *
     * @return {@code data = {status:true}}（已初始化）或 {@code {status:false, root_init, database_type}}
     */
    @GetMapping
    public ApiResponse<SetupStatusVO> status() {
        return ApiResponse.okData(SetupStatusVO.from(getSetupStatusUseCase.execute()));
    }

    /**
     * 系统初始化提交（F-4016，匿名，创建 root + 模式开关）。
     *
     * @param request 初始化提交请求
     * @return {@code {success:true}}
     */
    @PostMapping
    public ApiResponse<Void> submit(@RequestBody SetupSubmitRequest request) {
        // 领域构造期校验（用户名长度/密码长度/两次一致），失败抛 InvalidSetupRequestException→400。
        SetupSubmission submission = SetupSubmission.create(
                request.username(),
                request.password(),
                request.confirmPassword(),
                request.selfUseModeEnabled(),
                request.demoSiteEnabled());
        submitSetupUseCase.execute(submission);
        return ApiResponse.ok(null);
    }
}
