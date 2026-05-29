package com.pgcrm.controller;

import com.pgcrm.config.SystemConfigProperties;
import com.pgcrm.dto.SystemConfigResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigProperties systemConfig;

    @GetMapping("/config")
    public ResponseEntity<SystemConfigResponse> getConfig() {
        return ResponseEntity.ok(SystemConfigResponse.fromProperties(systemConfig));
    }
}
