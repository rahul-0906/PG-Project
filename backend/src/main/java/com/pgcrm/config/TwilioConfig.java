package com.pgcrm.config;

import com.twilio.Twilio;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
public class TwilioConfig {

    @Value("${twilio.account-sid}")
    private String accountSid;

    @Value("${twilio.auth-token}")
    private String authToken;

    @Value("${twilio.whatsapp-from}")
    private String whatsappFrom;

    @Value("${twilio.enabled:false}")
    private boolean enabled;

    @PostConstruct
    public void initTwilio() {
        if (enabled && !accountSid.startsWith("PLACEHOLDER")) {
            Twilio.init(accountSid, authToken);
        }
    }
}
