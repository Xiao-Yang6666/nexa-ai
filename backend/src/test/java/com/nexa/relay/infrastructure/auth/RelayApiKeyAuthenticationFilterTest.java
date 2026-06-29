package com.nexa.relay.infrastructure.auth;

import com.nexa.token.domain.model.Token;
import com.nexa.token.domain.repository.TokenRepository;
import com.nexa.token.domain.vo.TokenStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RelayApiKeyAuthenticationFilter 鉴权提取单测（REQ-API-KEY-AUTH）。
 *
 * <p>核心覆盖双 header 兼容：OpenAI 风格 {@code Authorization: Bearer sk-...}（优先）
 * 与 Anthropic 官方 SDK 风格 {@code x-api-key: sk-...}（回退）。通过 doFilterInternal
 * 的黑盒行为（命中即注入 {@link RelayApiKeyAuthentication}、未命中即 401 不放行）验证
 * 私有 extractApiKey 的两路提取与优先级，回归保护 x-api-key 兼容性缺口的修复。</p>
 */
class RelayApiKeyAuthenticationFilterTest {

    private static final String VALID_KEY = "sk-valid-key-123";

    private TokenRepository tokenRepository;
    private RelayApiKeyAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        tokenRepository = mock(TokenRepository.class);
        filter = new RelayApiKeyAuthenticationFilter(tokenRepository);
    }

    /** 构造一个启用、永不过期、不限额度的可用 token（真实 Builder，不 mock）。 */
    private Token usableToken() {
        return Token.builder()
                .id(1L)
                .userId(2L)
                .key(VALID_KEY)
                .status(TokenStatus.ENABLED)
                .name("e2e-key")
                .expiredTime(Token.NEVER_EXPIRE)
                .unlimitedQuota(true)
                .remainQuota(10_000_000L)
                .group("default")
                .build();
    }

    private MockHttpServletRequest v1Request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setServletPath("/v1/messages");
        return request;
    }

    @Test
    void bearerHeader_authenticated() throws Exception {
        when(tokenRepository.findByKey(VALID_KEY)).thenReturn(Optional.of(usableToken()));
        MockHttpServletRequest request = v1Request();
        request.addHeader("Authorization", "Bearer " + VALID_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth, "Bearer 命中应注入认证");
        verify(chain, times(1)).doFilter(any(), any());
        assertEquals(200, response.getStatus());
    }

    @Test
    void xApiKeyHeader_authenticated() throws Exception {
        when(tokenRepository.findByKey(VALID_KEY)).thenReturn(Optional.of(usableToken()));
        MockHttpServletRequest request = v1Request();
        request.addHeader("x-api-key", VALID_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth, "x-api-key 命中应注入认证（Anthropic SDK 兼容）");
        verify(chain, times(1)).doFilter(any(), any());
        assertEquals(200, response.getStatus());
    }

    @Test
    void bearerTakesPrecedenceOverXApiKey() throws Exception {
        // Bearer 带有效 key、x-api-key 带垃圾 → 应走 Bearer 命中
        when(tokenRepository.findByKey(VALID_KEY)).thenReturn(Optional.of(usableToken()));
        MockHttpServletRequest request = v1Request();
        request.addHeader("Authorization", "Bearer " + VALID_KEY);
        request.addHeader("x-api-key", "sk-garbage");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        // 只用 Bearer 的 key 反查，绝不查垃圾 x-api-key
        verify(tokenRepository, times(1)).findByKey(VALID_KEY);
        verify(tokenRepository, never()).findByKey("sk-garbage");
    }

    @Test
    void emptyBearerFallsBackToXApiKey() throws Exception {
        // Authorization 头是空 Bearer（"Bearer "）→ 应回退到 x-api-key
        when(tokenRepository.findByKey(VALID_KEY)).thenReturn(Optional.of(usableToken()));
        MockHttpServletRequest request = v1Request();
        request.addHeader("Authorization", "Bearer ");
        request.addHeader("x-api-key", VALID_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication(),
                "空 Bearer 应回退到 x-api-key");
        verify(tokenRepository, times(1)).findByKey(VALID_KEY);
    }

    @Test
    void noCredentials_unauthorized() throws Exception {
        MockHttpServletRequest request = v1Request();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain, never()).doFilter(any(), any());
        assertEquals(401, response.getStatus());
    }

    @Test
    void blankXApiKey_unauthorized() throws Exception {
        MockHttpServletRequest request = v1Request();
        request.addHeader("x-api-key", "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain, never()).doFilter(any(), any());
        assertEquals(401, response.getStatus());
    }

    @Test
    void nonV1Path_skipped() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setServletPath("/api/users");
        request.addHeader("x-api-key", VALID_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        // /api 不归本过滤器管：直接放行交回 JWT 链路，不注入 relay 认证
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain, times(1)).doFilter(any(), any());
    }
}
