package com.argus.rag.common.config;

import com.argus.rag.auth.config.AuthProperties;
import com.argus.rag.auth.security.AuthCookieSupport;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CrossDomainConfigurationTest {

    private static class Registry extends CorsRegistry {
        Map<String, CorsConfiguration> mappings() {
            return getCorsConfigurations();
        }
    }

    @Test
    void credentialedCorsReflectsOnlyMatchingOrigins() {
        String origin = "http://localhost:5173";
        for (String pattern : new String[]{"*", origin}) {
            Registry registry = new Registry();
            new com.argus.rag.common.config.CorsConfiguration(new String[]{pattern}).addCorsMappings(registry);
            CorsConfiguration cors = registry.mappings().get("/api/**");
            assertThat(cors.checkOrigin(origin)).isEqualTo(origin);
            assertThat(cors.getAllowCredentials()).isTrue();
            if (pattern.equals(origin)) {
                assertThat(cors.checkOrigin("http://localhost:5174")).isNull();
            }
        }
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
}
