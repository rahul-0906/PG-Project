package com.pgcrm.controlplane.service;

import org.springframework.scheduling.annotation.Async;

public interface EmailNotificationService {
    
    @Async
    void sendWorkspaceLiveEmail(String toEmail, String pgName, String customDomain);
}
