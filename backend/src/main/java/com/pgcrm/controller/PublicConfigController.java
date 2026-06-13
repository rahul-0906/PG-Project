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
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class PublicConfigController {

    private final SystemConfigProperties systemConfigProperties;

    @GetMapping("/public")
    public ResponseEntity<Map<String, String>> getPublicConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("pgName", systemConfigProperties.getBranding().getName());
        config.put("pgShortName", systemConfigProperties.getBranding().getShortTitle());
        config.put("primaryColor", systemConfigProperties.getBranding().getPrimaryColor());
        return ResponseEntity.ok(config);
    }
}
