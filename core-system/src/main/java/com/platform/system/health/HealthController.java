package com.platform.system.health;

import com.platform.core.common.result.JsonResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/health")
public class HealthController {

    @Value("${spring.application.name:core-service}")
    private String appName;

    private final Environment env;

    public HealthController(Environment env) {
        this.env = env;
    }

    @GetMapping
    public JsonResult<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("app", appName);
        data.put("profile", String.join(",", env.getActiveProfiles()));
        data.put("status", "UP");
        data.put("timestamp", Instant.now().toString());
        return JsonResult.ok(data);
    }
}
