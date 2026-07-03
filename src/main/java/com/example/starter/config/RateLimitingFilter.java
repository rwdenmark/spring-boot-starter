package com.example.starter.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP rate limiter for POST /api/users. Buckets are in-process, so for
 * multi-node deploys swap for bucket4j-redis. The bucket map is capped at
 * {@link #MAX_TRACKED_IPS} entries. A high-traffic public service should use
 * a Caffeine cache with TTL instead.
 *
 * X-Forwarded-For is only honored when app.rate-limit.trust-forwarded-for is
 * true. The header is client-supplied, so enable it only behind a proxy you
 * control that overwrites it.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);
    private static final int MAX_TRACKED_IPS = 10_000;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int requestsPerMinute;
    private final boolean trustForwardedFor;

    public RateLimitingFilter(AppProperties appProperties) {
        this.requestsPerMinute = appProperties.rateLimit().registrationRequestsPerMinute();
        this.trustForwardedFor = appProperties.rateLimit().trustForwardedFor();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && "/api/users".equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var ip = clientIp(request);
        // Clearing resets everyone's counts, but it is the cheapest guard against unbounded growth from spoofed addresses.
        if (buckets.size() > MAX_TRACKED_IPS) {
            buckets.clear();
        }
        var bucket = buckets.computeIfAbsent(ip, key -> newBucket());

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded for IP {} on {} {}", ip, request.getMethod(), request.getRequestURI());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                    {"type":"about:blank","title":"Too Many Requests","status":429,"detail":"Rate limit exceeded. Try again shortly."}
                    """);
        }
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(requestsPerMinute)
                        .refillIntervally(requestsPerMinute, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    private String clientIp(HttpServletRequest request) {
        if (trustForwardedFor) {
            var forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
