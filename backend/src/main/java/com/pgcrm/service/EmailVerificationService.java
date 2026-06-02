package com.pgcrm.service;

import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EmailVerificationService {

    private static class VerificationDetails {
        String newEmail;
        String code;
        LocalDateTime expiryTime;

        VerificationDetails(String newEmail, String code, LocalDateTime expiryTime) {
            this.newEmail = newEmail;
            this.code = code;
            this.expiryTime = expiryTime;
        }
    }

    // Key: userId, Value: VerificationDetails
    private final Map<String, VerificationDetails> cache = new ConcurrentHashMap<>();

    public void storeCode(String userId, String newEmail, String code) {
        // Code expires in 15 minutes
        cache.put(userId, new VerificationDetails(newEmail, code, LocalDateTime.now().plusMinutes(15)));
    }

    public boolean verifyCode(String userId, String newEmail, String code) {
        VerificationDetails details = cache.get(userId);
        if (details == null) {
            return false;
        }
        if (LocalDateTime.now().isAfter(details.expiryTime)) {
            cache.remove(userId);
            return false;
        }
        if (details.newEmail.equalsIgnoreCase(newEmail) && details.code.equals(code)) {
            cache.remove(userId);
            return true;
        }
        return false;
    }
}
