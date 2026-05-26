package com.example.starter.greeting;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/greeting")
@EnableConfigurationProperties(GreetingController.AppProperties.class)
public class GreetingController {
    private final String greeting;

    @ConfigurationProperties(prefix = "app")
    public record AppProperties(String greeting) {}

    public GreetingController(AppProperties props) {
        this.greeting = props.greeting();
    }

    @GetMapping
    public Map<String, String> greet() {
        return Map.of("message", greeting);
    }
}