package com.nexa.channel.interfaces.api;

import com.nexa.channel.application.BatchOperateChannelsUseCase;
import com.nexa.channel.application.CreateChannelUseCase;
import com.nexa.channel.application.DeleteChannelUseCase;
import com.nexa.channel.application.GetChannelUseCase;
import com.nexa.channel.application.ListChannelsUseCase;
import com.nexa.channel.application.ManageOllamaUseCase;
import com.nexa.channel.application.ProbeUpstreamModelsUseCase;
import com.nexa.channel.application.QueryCodexUsageUseCase;
import com.nexa.channel.application.SearchChannelsUseCase;
import com.nexa.channel.application.TestChannelUseCase;
import com.nexa.channel.application.ToggleChannelsByTagUseCase;
import com.nexa.channel.application.UpdateChannelBalanceUseCase;
import com.nexa.channel.application.UpdateChannelUseCase;
import com.nexa.channel.domain.vo.Pagination;
import com.nexa.shared.web.ApiResponse;
import com.nexa.channel.interfaces.api.dto.BatchActionRequest;
import com.nexa.channel.interfaces.api.dto.ChannelAdminView;
import com.nexa.channel.interfaces.api.dto.ChannelCreateRequest;
import com.nexa.channel.interfaces.api.dto.ChannelListView;
import com.nexa.channel.interfaces.api.dto.ChannelOllamaRequest;
import com.nexa.channel.interfaces.api.dto.ChannelFetchModelsRequest;
import com.nexa.channel.interfaces.api.dto.ChannelTagRequest;
import com.nexa.channel.interfaces.api.dto.ChannelTestResultView;
import com.nexa.channel.interfaces.api.dto.ChannelUpdateRequest;
import com.nexa.channel.interfaces.api.dto.ChannelUpstreamApplyRequest;
import com.nexa.channel.interfaces.api.dto.CodexUsageView;
import com.nexa.shared.security.domain.rbac.AuthLevel;
import com.nexa.shared.security.interfaces.annotation.RequireRole;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 渠道管理控制器（AdminAuth 端点，接口层，F-2016~F-2028）。
 *
 * <p>承载渠道管理全部端点（对齐 openapi /api/channel*）：
 * <ul>
 *   <li>{@code GET    /api/channel/}                    渠道列表分页（F-2016）</li>
 *   <li>{@code POST   /api/channel/}                    创建渠道（F-2016，含 F-2020/21/22/25）</li>
 *   <li>{@code PUT    /api/channel/}                    编辑渠道（F-2016，覆盖式）</li>
 *   <li>{@code GET    /api/channel/search}              渠道搜索（F-2016）</li>
 *   <li>{@code DELETE /api/channel/{id}}                删除渠道（F-2016）</li>
 *   <li>{@code POST   /api/channel/batch}               批量操作（F-2016）</li>
 *   <li>{@code GET    /api/channel/test}                全量测试（F-2017）</li>
 *   <li>{@code GET    /api/channel/test/{id}}           单渠道测试（F-2017）</li>
 *   <li>{@code GET    /api/channel/update_balance}      全量余额更新（F-2018）</li>
 *   <li>{@code GET    /api/channel/update_balance/{id}} 单渠道余额更新（F-2018）</li>
 *   <li>{@code POST   /api/channel/tag/enable}          按 tag 启用（F-2019）</li>
 *   <li>{@code POST   /api/channel/tag/disable}         按 tag 禁用（F-2019）</li>
 *   <li>{@code POST   /api/channel/fetch_models/{id}}   上游模型探测（F-2026）</li>
 *   <li>{@code POST   /api/channel/{id}/upstream/apply} 探测结果应用（F-2026）</li>
 *   <li>{@code POST   /api/channel/{id}/ollama/pull}    Ollama 拉取（F-2027）</li>
 *   <li>{@code POST   /api/channel/{id}/ollama/delete}  Ollama 删除（F-2027）</li>
 *   <li>{@code GET    /api/channel/{id}/ollama/version} Ollama 版本（F-2027）</li>
 * </ul>
 * </p>
 *
 * <p>DDD 铁律：接口层只做协议翻译（绑定入参 → 调用用例 → 裁剪视图），无业务逻辑。
 * 分页归一在 {@link Pagination}，字段校验/状态迁移/护栏在领域聚合（充血）。领域/上游异常由
 * {@code ChannelExceptionHandler} 统一翻译（400/404/502）。</p>
 *
 * <p><b>鉴权（安全声明）</b>：契约要求全 {@code /api/channel*} = AdminAuth。本控制器类级
 * {@link RequireRole}({@link AuthLevel#ADMIN})，由 {@code RequireRoleInterceptor} 统一拦截判定，
 * 未认证→401、越权→403（不裸奔）。</p>
 *
 * <p><b>客户视图铁律</b>：渠道管理为管理端能力，出参用 {@link ChannelAdminView}（绝不下发渠道 key
 * 原始凭证；balance 为渠道余额，属管理端运维数据，非客户成本/利润）。</p>
 */
@RestController
@RequestMapping("/api/channel")
@RequireRole(AuthLevel.ADMIN)
public class ChannelController {

    private final ListChannelsUseCase listChannelsUseCase;
    private final SearchChannelsUseCase searchChannelsUseCase;
    private final GetChannelUseCase getChannelUseCase;
    private final CreateChannelUseCase createChannelUseCase;
    private final UpdateChannelUseCase updateChannelUseCase;
    private final DeleteChannelUseCase deleteChannelUseCase;
    private final BatchOperateChannelsUseCase batchOperateChannelsUseCase;
    private final TestChannelUseCase testChannelUseCase;
    private final UpdateChannelBalanceUseCase updateChannelBalanceUseCase;
    private final ToggleChannelsByTagUseCase toggleChannelsByTagUseCase;
    private final ProbeUpstreamModelsUseCase probeUpstreamModelsUseCase;
    private final ManageOllamaUseCase manageOllamaUseCase;
    private final QueryCodexUsageUseCase queryCodexUsageUseCase;

    /**
     * @param listChannelsUseCase         列表用例（F-2016）
     * @param searchChannelsUseCase       搜索用例（F-2016）
     * @param getChannelUseCase           详情用例（F-2016）
     * @param createChannelUseCase        创建用例（F-2016）
     * @param updateChannelUseCase        编辑用例（F-2016）
     * @param deleteChannelUseCase        删除用例（F-2016）
     * @param batchOperateChannelsUseCase 批量操作用例（F-2016）
     * @param testChannelUseCase          连通性测试用例（F-2017）
     * @param updateChannelBalanceUseCase 余额更新用例（F-2018）
     * @param toggleChannelsByTagUseCase  按 tag 启停用例（F-2019）
     * @param probeUpstreamModelsUseCase  上游模型探测/应用用例（F-2026）
     * @param manageOllamaUseCase         Ollama 管理用例（F-2027）
     * @param queryCodexUsageUseCase      Codex 渠道上游用量查询用例（F-4045）
     */
    public ChannelController(ListChannelsUseCase listChannelsUseCase,
                             SearchChannelsUseCase searchChannelsUseCase,
                             GetChannelUseCase getChannelUseCase,
                             CreateChannelUseCase createChannelUseCase,
                             UpdateChannelUseCase updateChannelUseCase,
                             DeleteChannelUseCase deleteChannelUseCase,
                             BatchOperateChannelsUseCase batchOperateChannelsUseCase,
                             TestChannelUseCase testChannelUseCase,
                             UpdateChannelBalanceUseCase updateChannelBalanceUseCase,
                             ToggleChannelsByTagUseCase toggleChannelsByTagUseCase,
                             ProbeUpstreamModelsUseCase probeUpstreamModelsUseCase,
                             ManageOllamaUseCase manageOllamaUseCase,
                             QueryCodexUsageUseCase queryCodexUsageUseCase) {
        this.listChannelsUseCase = listChannelsUseCase;
        this.searchChannelsUseCase = searchChannelsUseCase;
        this.getChannelUseCase = getChannelUseCase;
        this.createChannelUseCase = createChannelUseCase;
        this.updateChannelUseCase = updateChannelUseCase;
        this.deleteChannelUseCase = deleteChannelUseCase;
        this.batchOperateChannelsUseCase = batchOperateChannelsUseCase;
        this.testChannelUseCase = testChannelUseCase;
        this.updateChannelBalanceUseCase = updateChannelBalanceUseCase;
        this.toggleChannelsByTagUseCase = toggleChannelsByTagUseCase;
        this.probeUpstreamModelsUseCase = probeUpstreamModelsUseCase;
        this.manageOllamaUseCase = manageOllamaUseCase;
        this.queryCodexUsageUseCase = queryCodexUsageUseCase;
    }

    /**
     * 渠道列表分页（F-2016，{@code GET /api/channel/}）。
     *
     * @param page     query 页号（可空→1）
     * @param pageSize query 每页条数（可空→10）
     * @param group    query 分组过滤（可空）
     * @param type     query type 过滤（可空）
     * @param tag      query tag 过滤（可空）
     * @param status   query 状态过滤（可空）
     * @return 成功信封，data = { items[], total }（AdminView，key 脱敏）
     */
    @GetMapping("/")
    public ApiResponse<ChannelListView> list(
            @RequestParam(name = "p", required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(name = "group", required = false) String group,
            @RequestParam(name = "type", required = false) Integer type,
            @RequestParam(name = "tag", required = false) String tag,
            @RequestParam(name = "status", required = false) Integer status) {

        Pagination pagination = Pagination.of(page, pageSize);
        return ApiResponse.okData(
                ChannelListView.from(listChannelsUseCase.list(group, type, tag, status, pagination)));
    }

    /**
     * 创建渠道（F-2016，{@code POST /api/channel/}）。
     *
     * @param request 创建请求（type/key/models 必填）
     * @return 成功信封，data = 创建后渠道（AdminView）
     */
    @PostMapping("/")
    public ApiResponse<ChannelAdminView> create(@RequestBody ChannelCreateRequest request) {
        return ApiResponse.okData(
                ChannelAdminView.from(createChannelUseCase.create(request.toCommand())));
    }

    /**
     * 编辑渠道（F-2016，{@code PUT /api/channel/}，覆盖式）。
     *
     * @param request 编辑请求（id/type/models 必填）
     * @return 成功信封，data = 更新后渠道（AdminView）
     */
    @PutMapping("/")
    public ApiResponse<ChannelAdminView> update(@RequestBody ChannelUpdateRequest request) {
        return ApiResponse.okData(
                ChannelAdminView.from(updateChannelUseCase.update(request.toCommand())));
    }

    /**
     * 渠道搜索（F-2016，{@code GET /api/channel/search}）。
     *
     * @param keyword  query 关键词（可空白→全量）
     * @param page     query 页号（可空→1）
     * @param pageSize query 每页条数（可空→10）
     * @return 成功信封，data = { items[], total }（AdminView）
     */
    @GetMapping("/search")
    public ApiResponse<ChannelListView> search(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "p", required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize) {

        Pagination pagination = Pagination.of(page, pageSize);
        return ApiResponse.okData(
                ChannelListView.from(searchChannelsUseCase.search(keyword, pagination)));
    }

    /**
     * 删除渠道（F-2016，{@code DELETE /api/channel/{id}}）。
     *
     * @param id path 渠道 id
     * @return 成功信封（删除成功）
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") long id) {
        deleteChannelUseCase.delete(id);
        return ApiResponse.ok("channel deleted");
    }

    /**
     * 批量操作渠道（F-2016，{@code POST /api/channel/batch}）。
     *
     * @param request 批量请求（ids + action）
     * @return 成功信封，data = 受影响数量
     */
    @PostMapping("/batch")
    public ApiResponse<Integer> batch(@RequestBody BatchActionRequest request) {
        return ApiResponse.okData(
                batchOperateChannelsUseCase.operate(request.ids(), request.action()));
    }

    /**
     * 全量渠道测试（F-2017，{@code GET /api/channel/test}）。
     *
     * @return 成功信封，data = 各渠道测试结果汇总（AdminView）
     */
    @GetMapping("/test")
    public ApiResponse<List<ChannelTestResultView>> testAll() {
        return ApiResponse.okData(
                testChannelUseCase.testAll().stream().map(ChannelTestResultView::from).toList());
    }

    /**
     * 单渠道连通性测试（F-2017，{@code GET /api/channel/test/{id}}）。
     *
     * @param id    path 渠道 id
     * @param model query 指定测试模型（可空）
     * @return 成功信封，data = 测试结果（AdminView）
     */
    @GetMapping("/test/{id}")
    public ApiResponse<ChannelTestResultView> test(
            @PathVariable("id") long id,
            @RequestParam(name = "model", required = false) String model) {

        return ApiResponse.okData(ChannelTestResultView.from(testChannelUseCase.test(id, model)));
    }

    /**
     * 全量余额更新（F-2018，{@code GET /api/channel/update_balance}）。
     *
     * @return 成功信封（批量刷新结果）
     */
    @GetMapping("/update_balance")
    public ApiResponse<Void> updateAllBalance() {
        int refreshed = updateChannelBalanceUseCase.updateAll();
        return ApiResponse.ok("refreshed " + refreshed + " channels");
    }

    /**
     * 单渠道余额更新（F-2018，{@code GET /api/channel/update_balance/{id}}）。
     *
     * @param id path 渠道 id
     * @return 成功信封，data = { balance }（USD）
     */
    @GetMapping("/update_balance/{id}")
    public ApiResponse<Map<String, BigDecimal>> updateBalance(@PathVariable("id") long id) {
        BigDecimal balance = updateChannelBalanceUseCase.updateOne(id);
        return ApiResponse.okData(Map.of("balance", balance));
    }

    /**
     * 按 tag 批量启用渠道（F-2019，{@code POST /api/channel/tag/enable}）。
     *
     * @param request tag 请求
     * @return 成功信封（启用成功）
     */
    @PostMapping("/tag/enable")
    public ApiResponse<Void> enableByTag(@RequestBody ChannelTagRequest request) {
        int affected = toggleChannelsByTagUseCase.enableByTag(request.tag());
        return ApiResponse.ok("enabled " + affected + " channels");
    }

    /**
     * 按 tag 批量禁用渠道（F-2019，{@code POST /api/channel/tag/disable}）。
     *
     * @param request tag 请求
     * @return 成功信封（禁用成功）
     */
    @PostMapping("/tag/disable")
    public ApiResponse<Void> disableByTag(@RequestBody ChannelTagRequest request) {
        int affected = toggleChannelsByTagUseCase.disableByTag(request.tag());
        return ApiResponse.ok("disabled " + affected + " channels");
    }

    /**
     * 上游模型探测（F-2026，{@code POST /api/channel/fetch_models/{id}}，预览不改 Models）。
     *
     * @param id path 渠道 id
     * @return 成功信封，data = 上游模型名列表（AdminView）
     */
    @PostMapping("/fetch_models/{id}")
    public ApiResponse<List<String>> fetchModels(@PathVariable("id") long id) {
        return ApiResponse.okData(probeUpstreamModelsUseCase.fetch(id));
    }

    /**
     * 按参数探测上游模型集（新建渠道场景，{@code POST /api/channel/fetch_models}，无 id、不落库）。
     *
     * <p>用请求体的 type/base_url/key 直接探测上游，供"新建渠道"抽屉点"获取模型列表"使用。</p>
     *
     * @param request 探测参数（type/base_url/key）
     * @return 成功信封，data = 上游模型名列表
     */
    @PostMapping("/fetch_models")
    public ApiResponse<List<String>> fetchModelsByParams(@RequestBody ChannelFetchModelsRequest request) {
        return ApiResponse.okData(
                probeUpstreamModelsUseCase.fetchByParams(request.type(), request.baseUrl(), request.key()));
    }

    /**
     * 探测结果应用到渠道（F-2026，{@code POST /api/channel/{id}/upstream/apply}，覆盖式）。
     *
     * @param id      path 渠道 id
     * @param request 勾选模型集
     * @return 成功信封，data = 更新后渠道（AdminView）
     */
    @PostMapping("/{id}/upstream/apply")
    public ApiResponse<ChannelAdminView> applyUpstream(
            @PathVariable("id") long id,
            @RequestBody ChannelUpstreamApplyRequest request) {

        return ApiResponse.okData(
                ChannelAdminView.from(probeUpstreamModelsUseCase.apply(id, request.models())));
    }

    /**
     * Ollama 模型拉取（F-2027，{@code POST /api/channel/{id}/ollama/pull}，仅 Ollama 渠道）。
     *
     * @param id      path 渠道 id
     * @param request 模型请求
     * @return 成功信封（拉取成功）
     */
    @PostMapping("/{id}/ollama/pull")
    public ApiResponse<Void> ollamaPull(
            @PathVariable("id") long id,
            @RequestBody ChannelOllamaRequest request) {

        manageOllamaUseCase.pull(id, request.model());
        return ApiResponse.ok("ollama pull requested");
    }

    /**
     * Ollama 模型删除（F-2027，{@code POST /api/channel/{id}/ollama/delete}，仅 Ollama 渠道）。
     *
     * @param id      path 渠道 id
     * @param request 模型请求
     * @return 成功信封（删除成功）
     */
    @PostMapping("/{id}/ollama/delete")
    public ApiResponse<Void> ollamaDelete(
            @PathVariable("id") long id,
            @RequestBody ChannelOllamaRequest request) {

        manageOllamaUseCase.delete(id, request.model());
        return ApiResponse.ok("ollama delete requested");
    }

    /**
     * Ollama 版本查询（F-2027，{@code GET /api/channel/{id}/ollama/version}，仅 Ollama 渠道）。
     *
     * @param id path 渠道 id
     * @return 成功信封，data = { version }（AdminView）
     */
    @GetMapping("/{id}/ollama/version")
    public ApiResponse<Map<String, String>> ollamaVersion(@PathVariable("id") long id) {
        return ApiResponse.okData(Map.of("version", manageOllamaUseCase.version(id)));
    }

    /**
     * Codex 渠道上游用量查询（F-4045，{@code GET /api/channel/{id}/codex/usage}，仅 Codex 单 Key 渠道）。
     *
     * <p>领域规则来源：API-ENDPOINTS §5.8。非 Codex 类型 → 400「channel type is not Codex」；
     * multi-key 渠道 → 400「multi-key channel is not supported」；凭证缺 access_token/account_id → 400；
     * 上游故障/刷新失败 → 502。上游 401/403 且有 refresh_token 时由用例编排自动刷新令牌重试并回写渠道
     * key（status∈{1,3} 渠道附带 InitChannelCache）。出参 AdminView（{@link CodexUsageView}）仅含用量原文
     * 与刷新标记，<b>绝不回显任何凭证</b>。</p>
     *
     * @param id path 渠道 id
     * @return 成功信封，data = { usage, token_refreshed }（AdminView）
     */
    @GetMapping("/{id}/codex/usage")
    public ApiResponse<CodexUsageView> codexUsage(@PathVariable("id") long id) {
        return ApiResponse.okData(CodexUsageView.from(queryCodexUsageUseCase.query(id)));
    }
}
