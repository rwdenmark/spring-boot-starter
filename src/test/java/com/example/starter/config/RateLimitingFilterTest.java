package com.example.starter.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link RateLimitingFilter}. No Spring context. Requests are
 * driven through the filter with mock servlet objects.
 */
class RateLimitingFilterTest {

    private RateLimitingFilter filter(int requestsPerMinute, boolean trustForwardedFor) {
        var props = new AppProperties(null, null, null,
                new AppProperties.RateLimit(requestsPerMinute, trustForwardedFor), null);
        return new RateLimitingFilter(props);
    }

    private MockHttpServletResponse register(RateLimitingFilter filter,
                                             String remoteAddr,
                                             String forwardedFor) throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/users");
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void overLimit_returns429WithProblemJson() throws Exception {
        var filter = filter(1, false);

        assertThat(register(filter, "10.0.0.1", null).getStatus()).isEqualTo(200);

        var limited = register(filter, "10.0.0.1", null);
        assertThat(limited.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(limited.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(limited.getContentAsString()).contains("Rate limit exceeded");
    }

    @Test
    void spoofedForwardedFor_isIgnoredByDefault() throws Exception {
        var filter = filter(1, false);

        // Same socket address with rotating X-Forwarded-For values. If the
        // header were trusted, each request would get a fresh bucket.
        assertThat(register(filter, "10.0.0.1", "1.1.1.1").getStatus()).isEqualTo(200);
        assertThat(register(filter, "10.0.0.1", "2.2.2.2").getStatus())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    void forwardedFor_isHonoredWhenTrusted() throws Exception {
        var filter = filter(1, true);

        // Behind a trusted proxy the remote address is the proxy itself, so
        // distinct forwarded clients must get distinct buckets.
        assertThat(register(filter, "10.0.0.1", "1.1.1.1").getStatus()).isEqualTo(200);
        assertThat(register(filter, "10.0.0.1", "2.2.2.2").getStatus()).isEqualTo(200);
        assertThat(register(filter, "10.0.0.1", "1.1.1.1").getStatus())
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    void otherEndpoints_areNotRateLimited() throws Exception {
        var filter = filter(1, false);

        for (int i = 0; i < 3; i++) {
            var request = new MockHttpServletRequest("GET", "/api/users");
            request.setRemoteAddr("10.0.0.1");
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
