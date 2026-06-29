package com.nexa.interfaces.account.provider.api;

import com.nexa.application.account.provider.CreateAccountUseCase;
import com.nexa.application.account.provider.DeleteAccountUseCase;
import com.nexa.application.account.provider.GetAccountUseCase;
import com.nexa.application.account.provider.ListAccountsUseCase;
import com.nexa.application.account.provider.ProbeProviderModelsUseCase;
import com.nexa.application.account.provider.TestProviderModelUseCase;
import com.nexa.application.account.provider.ToggleAccountUseCase;
import com.nexa.application.account.provider.UpdateAccountUseCase;
import com.nexa.application.account.provider.port.ProviderModelTestPort;
import com.nexa.domain.account.provider.vo.Pagination;
import com.nexa.interfaces.account.provider.api.dto.AccountCreateRequest;
import com.nexa.interfaces.account.provider.api.dto.AccountListView;
import com.nexa.interfaces.account.provider.api.dto.AccountUpdateRequest;
import com.nexa.interfaces.account.provider.api.dto.AccountView;
import com.nexa.interfaces.account.provider.api.dto.ProbeModelsRequest;
import com.nexa.interfaces.account.provider.api.dto.TestModelRequest;
import com.nexa.interfaces.account.provider.api.dto.TestModelView;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexa.shared.security.domain.rbac.AuthLevel;
import com.nexa.shared.security.interfaces.annotation.RequireRole;
import com.nexa.shared.web.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 供应商账号管理控制器（AdminAuth 端点，接口层）。
 *
 * <p>承载账号管理 CRUD + 启停端点：
 * <ul>
 *   <li>{@code GET    /api/admin/accounts}             列表分页（按 platform 过滤）</li>
 *   <li>{@code POST   /api/admin/accounts}             创建账号</li>
 *   <li>{@code GET    /api/admin/accounts/{id}}        详情</li>
 *   <li>{@code PUT    /api/admin/accounts/{id}}        编辑（覆盖式）</li>
 *   <li>{@code DELETE /api/admin/accounts/{id}}        删除</li>
 *   <li>{@code PATCH  /api/admin/accounts/{id}/toggle} 启停</li>
 *   <li>{@code POST   /api/admin/accounts/{id}/test-model} 模型连通性测试</li>
 *   <li>{@code POST   /api/admin/accounts/{id}/test-model/stream} 模型连通性测试（SSE 流式）</li>
 * </ul>
 * </p>
 *
 * <p>DDD 铁律：接口层只做协议翻译（绑定入参 → 调用用例 → 裁剪视图），无业务逻辑。
 * 分页归一在 {@link Pagination}，字段校验/状态迁移在领域聚合（充血）。领域异常由
 * {@code ProviderAccountExceptionHandler} 统一翻译（400/404）。</p>
 *
 * <p><b>鉴权</b>：全 {@code /api/admin/accounts*} = AdminAuth。类级 {@link RequireRole}
 * ({@link AuthLevel#ADMIN})，由 {@code RequireRoleInterceptor} 拦截判定，未认证→401、越权→403。</p>
 *
 * <p><b>凭证安全铁律</b>：出参用 {@link AccountView}（绝不下发 credentials 原始凭证）。</p>
 */
@RestController
@RequestMapping("/api/admin/accounts")
@RequireRole(AuthLevel.ADMIN)
public class AccountController {

    private final ListAccountsUseCase listAccountsUseCase;
    private final GetAccountUseCase getAccountUseCase;
    private final CreateAccountUseCase createAccountUseCase;
    private final UpdateAccountUseCase updateAccountUseCase;
    private final DeleteAccountUseCase deleteAccountUseCase;
    private final ToggleAccountUseCase toggleAccountUseCase;
    private final ProbeProviderModelsUseCase probeProviderModelsUseCase;
    private final TestProviderModelUseCase testProviderModelUseCase;

    /** SSE 事件 JSON 序列化（流式测试用；无状态，直接实例化避免改构造器/测试）。 */
    private final ObjectMapper sseMapper = new ObjectMapper();

    /**
     * @param listAccountsUseCase        列表用例
     * @param getAccountUseCase          详情用例
     * @param createAccountUseCase       创建用例
     * @param updateAccountUseCase       编辑用例
     * @param deleteAccountUseCase       删除用例
     * @param toggleAccountUseCase       启停用例
     * @param probeProviderModelsUseCase 探测上游模型列表用例
     * @param testProviderModelUseCase   模型连通性测试用例
     */
    public AccountController(ListAccountsUseCase listAccountsUseCase,
                            GetAccountUseCase getAccountUseCase,
                            CreateAccountUseCase createAccountUseCase,
                            UpdateAccountUseCase updateAccountUseCase,
                            DeleteAccountUseCase deleteAccountUseCase,
                            ToggleAccountUseCase toggleAccountUseCase,
                            ProbeProviderModelsUseCase probeProviderModelsUseCase,
                            TestProviderModelUseCase testProviderModelUseCase) {
        this.listAccountsUseCase = listAccountsUseCase;
        this.getAccountUseCase = getAccountUseCase;
        this.createAccountUseCase = createAccountUseCase;
        this.updateAccountUseCase = updateAccountUseCase;
        this.deleteAccountUseCase = deleteAccountUseCase;
        this.toggleAccountUseCase = toggleAccountUseCase;
        this.probeProviderModelsUseCase = probeProviderModelsUseCase;
        this.testProviderModelUseCase = testProviderModelUseCase;
    }

    /**
     * 账号列表分页（{@code GET /api/admin/accounts}）。
     *
     * @param page     query 页号（可空→1）
     * @param pageSize query 每页条数（可空→10）
     * @param platform query 平台过滤（可空）
     * @return 成功信封，data = { items[], total }（credentials 脱敏）
     */
    @GetMapping
    public ApiResponse<AccountListView> list(
            @RequestParam(name = "p", required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(name = "platform", required = false) String platform) {

        Pagination pagination = Pagination.of(page, pageSize);
        return ApiResponse.okData(
                AccountListView.from(listAccountsUseCase.list(platform, pagination)));
    }

    /**
     * 创建账号（{@code POST /api/admin/accounts}）。
     *
     * @param request 创建请求（name/platform/type 必填）
     * @return 成功信封，data = 创建后账号（AccountView）
     */
    @PostMapping
    public ApiResponse<AccountView> create(@RequestBody AccountCreateRequest request) {
        return ApiResponse.okData(
                AccountView.from(createAccountUseCase.create(request.toCommand())));
    }

    /**
     * 账号详情（{@code GET /api/admin/accounts/{id}}）。
     *
     * @param id path 账号 id
     * @return 成功信封，data = 账号（AccountView）
     */
    @GetMapping("/{id}")
    public ApiResponse<AccountView> get(@PathVariable("id") long id) {
        return ApiResponse.okData(AccountView.from(getAccountUseCase.get(id)));
    }

    /**
     * 编辑账号（{@code PUT /api/admin/accounts/{id}}，覆盖式）。
     *
     * @param id      path 账号 id
     * @param request 编辑请求（name/platform/type 必填）
     * @return 成功信封，data = 更新后账号（AccountView）
     */
    @PutMapping("/{id}")
    public ApiResponse<AccountView> update(@PathVariable("id") long id,
                                           @RequestBody AccountUpdateRequest request) {
        return ApiResponse.okData(
                AccountView.from(updateAccountUseCase.update(request.toCommand(id))));
    }

    /**
     * 删除账号（{@code DELETE /api/admin/accounts/{id}}）。
     *
     * @param id path 账号 id
     * @return 成功信封（删除成功）
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") long id) {
        deleteAccountUseCase.delete(id);
        return ApiResponse.ok("account deleted");
    }

    /**
     * 启停账号（{@code PATCH /api/admin/accounts/{id}/toggle}）。
     *
     * @param id     path 账号 id
     * @param enable query 目标态（true=启用，false=禁用；缺省 true）
     * @return 成功信封，data = 更新后账号（AccountView）
     */
    @PatchMapping("/{id}/toggle")
    public ApiResponse<AccountView> toggle(
            @PathVariable("id") long id,
            @RequestParam(name = "enable", required = false, defaultValue = "true") boolean enable) {
        return ApiResponse.okData(AccountView.from(toggleAccountUseCase.toggle(id, enable)));
    }

    /**
     * 探测上游模型列表（{@code POST /api/admin/accounts/probe-models}）。
     *
     * <p>用新建/编辑表单当前填写的 platform/base_url/api_key 直接调上游 {@code /models} 拉取候选模型，
     * 无需先保存账号。前端"获取模型列表"按钮调用，返回的列表供用户勾选填入「支持的模型」。
     * api_key 仅用于本次探测鉴权，不落库。</p>
     *
     * @param request 探测请求（platform 必填、api_key 必填、base_url 可空）
     * @return 成功信封，data = 模型 ID 列表
     */
    @PostMapping("/probe-models")
    public ApiResponse<List<String>> probeModels(@RequestBody ProbeModelsRequest request) {
        return ApiResponse.okData(probeProviderModelsUseCase.probe(
                request.platform(), request.baseUrl(), request.apiKey()));
    }

    /**
     * 模型连通性测试（{@code POST /api/admin/accounts/{id}/test-model}）。
     *
     * <p>对已保存账号的指定模型发一次非流式聊天补全，验证「该账号 + 该模型」能否跑通
     * （鉴权 / 模型可用 / 上游连通）。apiKey 不经前端往返——服务端从账号已存 credentials 解出，
     * 与 relay 真实转发取 key 路径一致。成功返回耗时 + 回复片段；失败由统一异常处理翻译为 502。</p>
     *
     * @param id      path 账号 id（须已保存）
     * @param request 测试请求（model 必填、prompt 可空）
     * @return 成功信封，data = { ok, latency_ms, reply }
     */
    @PostMapping("/{id}/test-model")
    public ApiResponse<TestModelView> testModel(@PathVariable("id") long id,
                                                @RequestBody TestModelRequest request) {
        return ApiResponse.okData(TestModelView.from(
                testProviderModelUseCase.test(id, request.model(), request.prompt())));
    }

    /**
     * 模型连通性测试（流式，{@code POST /api/admin/accounts/{id}/test-model/stream}）。
     *
     * <p>对已保存账号的指定模型发一次<b>流式</b>聊天补全，按 SSE 把增量 token 实时回写给前端逐字显示：
     * <ul>
     *   <li>{@code event: delta} —— {@code data: {"text":"…"}}，每片增量文本一行；</li>
     *   <li>{@code event: done}  —— {@code data: {"latency_ms":N}}，正常收束（总耗时）；</li>
     * </ul>
     * 直写注入的 {@link HttpServletResponse}（SSE 直写绕过返回值处理器，与 RelayController 同模式），
     * 返回 null 表示响应已自行处理。<b>首片前</b>的失败（账号缺失 / 凭证缺失 / 上游 4xx / 解析失败）
     * 发生在写出任何字节之前，响应未提交，由 {@code ProviderAccountExceptionHandler} 翻译为 404/502
     * 错误信封；首片已写出后的上游中断由用例/端口内部消化（用已累计文本走 done 收束），不外抛。</p>
     *
     * @param id      path 账号 id（须已保存）
     * @param request 测试请求（model 必填、prompt 可空）
     * @param response 注入的 servlet 响应（直写 SSE）
     * @return null（响应已由本方法直写处理）
     */
    @PostMapping("/{id}/test-model/stream")
    public Object testModelStream(@PathVariable("id") long id,
                                  @RequestBody TestModelRequest request,
                                  HttpServletResponse response) {
        // 选渠/取 key/上游连接握手在写出首字节前完成；若抛异常此时响应未提交，可正常翻译为错误信封。
        // 故这里不预设 200——交由 testStream 内首片回调前的失败冒泡给 ExceptionHandler。
        response.setStatus(HttpStatus.OK.value());
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");

        try {
            OutputStream out = response.getOutputStream();
            testProviderModelUseCase.testStream(id, request.model(), request.prompt(),
                    new ProviderModelTestPort.TestStreamListener() {
                        @Override
                        public void onDelta(String text) {
                            writeEvent(out, "delta", sseJson("text", text));
                        }

                        @Override
                        public void onComplete(ProviderModelTestPort.ProviderModelTestResult result) {
                            writeEvent(out, "done", "{\"latency_ms\":" + result.latencyMs() + "}");
                        }
                    });
            out.flush();
        } catch (IOException e) {
            // 客户端断连/写出失败：响应可能已部分提交，无法改写状态码，仅终止写出。
            throw new TestStreamWriteException(e);
        }
        return null;
    }

    /** 写一个 SSE 事件块（{@code event: <name>\n data: <payload>\n\n}）并 flush。 */
    private void writeEvent(OutputStream out, String event, String dataJson) {
        try {
            String frame = "event: " + event + "\ndata: " + dataJson + "\n\n";
            out.write(frame.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            throw new TestStreamWriteException(e);
        }
    }

    /** 用 ObjectMapper 把单字段 {key:value} 安全转义为 JSON 串（避免 delta 文本里的引号/换行破坏 SSE）。 */
    private String sseJson(String key, String value) {
        try {
            ObjectNode node = sseMapper.createObjectNode();
            node.put(key, value == null ? "" : value);
            return sseMapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{\"" + key + "\":\"\"}";
        }
    }

    /** 流式写出阶段的 IO 异常（响应可能已提交，仅终止写出，不翻译为错误信封）。 */
    private static final class TestStreamWriteException extends RuntimeException {
        TestStreamWriteException(Throwable cause) {
            super(cause);
        }
    }
}
