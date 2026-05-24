package com.pgcrm.controller;

import com.pgcrm.config.SystemConfigProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigProperties systemConfig;

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("branding", systemConfig.getBranding());
        config.put("rules", systemConfig.getRules());
        return ResponseEntity.ok(config);
    }
}
