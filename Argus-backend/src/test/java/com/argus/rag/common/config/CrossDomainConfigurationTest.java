package com.argus.rag.common.config;

import com.argus.rag.auth.config.AuthProperties;
import com.argus.rag.auth.security.AuthCookieSupport;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrossDomainConfigurationTest {

    private static final String ALLOWED_ORIGIN = "https://sites.example.test";

    @Test
    void credentialedCorsReflectsOnlyMatchingOriginsOnErrorResponses() throws Exception {
        CorsFilter filter = new CorsConfiguration(new String[]{ALLOWED_ORIGIN}).corsFilterRegistration().getFilter();
        MockHttpServletRequest request = request("GET", "/api/groups");
        request.addHeader("Origin", ALLOWED_ORIGIN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                ((HttpServletResponse) servletResponse).setStatus(401));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo(ALLOWED_ORIGIN);
        assertThat(response.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
    }

    @Test
    void configuredOriginWithTrailingSlashMatchesBrowserOrigin() throws Exception {
        CorsFilter filter = new CorsConfiguration(new String[]{ALLOWED_ORIGIN + "/"}).corsFilterRegistration().getFilter();
        MockHttpServletRequest request = request("GET", "/api/groups");
        request.addHeader("Origin", ALLOWED_ORIGIN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                ((HttpServletResponse) servletResponse).setStatus(204));

        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo(ALLOWED_ORIGIN);
    }

    @Test
    void preflightAllowsConfiguredMethodsAndHeaders() throws Exception {
        CorsFilter filter = new CorsConfiguration(new String[]{ALLOWED_ORIGIN}).corsFilterRegistration().getFilter();
        MockHttpServletRequest request = request("OPTIONS", "/api/assistant/sessions/1");
        request.addHeader("Origin", ALLOWED_ORIGIN);
        request.addHeader("Access-Control-Request-Method", "PATCH");
        request.addHeader("Access-Control-Request-Headers", "authorization,content-type");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> chainInvoked.set(true));

        assertThat(chainInvoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo(ALLOWED_ORIGIN);
        assertThat(response.getHeader("Access-Control-Allow-Methods")).contains("PATCH");
        assertThat(response.getHeader("Access-Control-Allow-Headers"))
                .containsIgnoringCase("Authorization")
                .containsIgnoringCase("Content-Type");
    }

    @Test
    void corsFilterRunsBeforeAuthenticationForApiPaths() {
        FilterRegistrationBean<CorsFilter> registration =
                new CorsConfiguration(new String[]{ALLOWED_ORIGIN}).corsFilterRegistration();

        assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
        assertThat(registration.getUrlPatterns()).containsExactly("/api/*");
    }

    @Test
    void rejectsWildcardOriginsWhenCredentialsAreEnabled() {
        assertThatThrownBy(() -> new CorsConfiguration(new String[]{"*"}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不允许空值或通配符");
    }

    @Test
    void cookieUsesSamePolicyForIssueAndLogout() {
        AuthProperties properties = new AuthProperties();
        properties.setRefreshCookieSecure(false);
        AuthCookieSupport cookies = new AuthCookieSupport(properties);
        MockHttpServletResponse local = new MockHttpServletResponse();
        cookies.clearRefreshTokenCookie(local);
        assertThat(local.getHeader("Set-Cookie")).contains("HttpOnly", "SameSite=Lax").doesNotContain("Secure");

        properties.setRefreshCookieSameSite("None");
        properties.setRefreshCookieSecure(true);
        MockHttpServletResponse https = new MockHttpServletResponse();
        cookies.writeRefreshTokenCookie(https, "");
        cookies.clearRefreshTokenCookie(https);
        assertThat(https.getHeaders("Set-Cookie")).hasSize(2)
                .allSatisfy(value -> assertThat(value).contains("HttpOnly", "Secure", "SameSite=None"));
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setScheme("https");
        request.setServerName("api.example.test");
        return request;
    }
}
