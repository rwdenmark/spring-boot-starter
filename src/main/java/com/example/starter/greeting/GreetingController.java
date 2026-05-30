package com.example.starter.greeting;

import com.example.starter.config.AppProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/greeting")
public class GreetingController {

    private final AppProperties props;

    public GreetingController(AppProperties props) {
        this.props = props;
    }

    @GetMapping
    public Map<String, String> greet() {
        return Map.of("message", props.greeting());
    }
}
