package com.pgcrm.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class MetaWhatsAppConfig {

    @Value("${meta.whatsapp.phone-number-id:}")
    private String phoneNumberId;

    @Value("${meta.whatsapp.access-token:}")
    private String accessToken;

    @Value("${meta.whatsapp.verify-token:}")
    private String verifyToken;

    public boolean isEnabled() {
        return accessToken != null && !accessToken.isBlank() && phoneNumberId != null && !phoneNumberId.isBlank();
    }
}
