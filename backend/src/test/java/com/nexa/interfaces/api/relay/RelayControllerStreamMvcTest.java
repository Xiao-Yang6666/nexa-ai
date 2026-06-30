package com.nexa.interfaces.api.relay;

import com.nexa.application.model.ListPublicModelsUseCase;
import com.nexa.application.relay.result.RelayForwardResult;
import com.nexa.application.relay.RelayForwardUseCase;
import com.nexa.application.relay.VideoProxyUseCase;
import com.nexa.domain.security.rbac.ActorRole;
import com.nexa.domain.security.rbac.AuthenticatedActor;
import com.nexa.interfaces.security.annotation.CurrentActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RelayController 流式写出层 MockMvc 回归测试（R2-04）。
 *
 * <p>用 standaloneSetup 起真实 Spring MVC 返回值处理器链（{@code HandlerMethodReturnValueHandler}），
 * 这是流式 500 bug 的发生层——既有单测 mock 了 useCase 直接调 {@code forwardStream}，绕过了 MVC
 * 返回值处理，故无法捕获该 bug。本测仅 mock 业务 useCase（让 {@code forwardStream} 往 sink 写 SSE 字节），
 * 但请求经真实 MVC dispatch，确认控制器把流式响应直写 servlet 输出流（200 + text/event-stream），
 * 而非把 StreamingResponseBody Lambda 丢给 HttpMessageConverter（旧写法 → HttpMessageNotWritableException
 * → 500）。</p>
 */
@DisplayName("RelayController 流式写出 MVC 回归 - 真实返回值处理器链")
class RelayControllerStreamMvcTest {

    private RelayForwardUseCase useCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        useCase = mock(RelayForwardUseCase.class);
        ListPublicModelsUseCase listPublicModelsUseCase = mock(ListPublicModelsUseCase.class);
        VideoProxyUseCase videoProxyUseCase = mock(VideoProxyUseCase.class);

        RelayController controller =
                new RelayController(useCase, listPublicModelsUseCase, videoProxyUseCase);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new StubActorResolver())
                .build();
    }

    @Test
    @DisplayName("stream:true → 200 + text/event-stream + SSE 流体（不再 500）")
    void streamingRequest_returnsSseNot500() throws Exception {
        when(useCase.wantsStream(any())).thenReturn(true);
        // forwardStream 把 SSE 字节写进控制器提供的 servlet 输出流。
        doAnswer(inv -> {
            OutputStream out = inv.getArgument(3);
            out.write("data: {\"id\":\"chunk-1\"}\n\n".getBytes(StandardCharsets.UTF_8));
            out.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            return null;
        }).when(useCase).forwardStream(anyString(), any(), any(), any());

        String body = "{\"model\":\"gpt-test\",\"messages\":[],\"stream\":true}";
        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data: [DONE]")));
    }

    @Test
    @DisplayName("stream:false → 仍走非流式 JSON，行为不变")
    void nonStreamingRequest_returnsJsonUnchanged() throws Exception {
        when(useCase.wantsStream(any())).thenReturn(false);
        when(useCase.forward(anyString(), any(), any())).thenReturn(new RelayForwardResult(
                200,
                java.util.Map.of("Content-Type", java.util.List.of("application/json")),
                "{\"ok\":true}".getBytes(StandardCharsets.UTF_8)));

        String body = "{\"model\":\"gpt-test\",\"messages\":[],\"stream\":false}";
        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"ok\":true")));
    }

    /** 注入桩 Actor（standalone MVC 无安全过滤器，{@code @CurrentActor} 需自定义解析器供值）。 */
    private static final class StubActorResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(CurrentActor.class)
                    && AuthenticatedActor.class.isAssignableFrom(parameter.getParameterType());
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            return new AuthenticatedActor(1L, "e2e", ActorRole.COMMON);
        }
    }
}
