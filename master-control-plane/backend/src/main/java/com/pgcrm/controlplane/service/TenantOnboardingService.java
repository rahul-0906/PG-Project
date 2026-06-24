package com.pgcrm.controlplane.service;

import com.pgcrm.controlplane.dto.TenantOnboardingRequest;
import java.util.UUID;

public interface TenantOnboardingService {
    void processOnboarding(TenantOnboardingRequest request, UUID currentUserId);
}
