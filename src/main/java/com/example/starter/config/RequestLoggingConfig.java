package com.example.starter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

/**
 * Disabled by default. Enable per-request logging by raising the level:
 *   logging.level.org.springframework.web.filter.CommonsRequestLoggingFilter: DEBUG
 * Payload logging is off — request bodies can contain secrets.
 */
@Configuration
public class RequestLoggingConfig {

    @Bean
    public CommonsRequestLoggingFilter requestLoggingFilter() {
        var filter = new CommonsRequestLoggingFilter();
        filter.setIncludeClientInfo(true);
        filter.setIncludeQueryString(true);
        filter.setIncludeHeaders(false);
        filter.setIncludePayload(false);
        filter.setMaxPayloadLength(2_000);
        filter.setBeforeMessagePrefix("REQ  ");
        filter.setAfterMessagePrefix("RES  ");
        return filter;
    }
}
