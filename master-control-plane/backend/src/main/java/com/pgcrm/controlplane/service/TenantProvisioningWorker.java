package com.pgcrm.controlplane.service;

import com.pgcrm.controlplane.model.entity.TenantProfile;
import com.pgcrm.controlplane.model.enums.TenantStatus;
import com.pgcrm.controlplane.repository.TenantProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantProvisioningWorker {

    private final TenantProfileRepository tenantProfileRepository;
    private final EmailNotificationService emailNotificationService;

    @Async
    @Transactional
    public void executeProvisioningPipeline(UUID tenantId) {
        log.info("Starting asynchronous provisioning pipeline for tenant ID: {}", tenantId);

        // 1. Fetch TenantProfile
        TenantProfile profile = tenantProfileRepository.findById(tenantId).orElse(null);
        if (profile == null) {
            log.error("Aborting provisioning: Tenant profile not found for ID: {}", tenantId);
            return;
        }

        // 2. If status is not PROVISIONING, abort
        if (profile.getStatus() != TenantStatus.PROVISIONING) {
            log.warn("Aborting provisioning: Tenant status is {} (expected PROVISIONING) for ID: {}", 
                    profile.getStatus(), tenantId);
            return;
        }

        String dbPrefix = sanitizeDbPrefix(profile.getCustomDomain());
        String adminEmail = profile.getContactEmail();
        String routerIp = profile.getRouterIp() != null && !profile.getRouterIp().isEmpty() ? profile.getRouterIp() : "NONE";
        
        String rzpKey = profile.getRazorpayKey() != null && !profile.getRazorpayKey().isEmpty() ? profile.getRazorpayKey() : "NONE";
        String rzpSecret = profile.getRazorpaySecret() != null && !profile.getRazorpaySecret().isEmpty() ? profile.getRazorpaySecret() : "NONE";
        String waToken = profile.getWhatsappToken() != null && !profile.getWhatsappToken().isEmpty() ? profile.getWhatsappToken() : "NONE";
        String waKey = profile.getWhatsappKey() != null && !profile.getWhatsappKey().isEmpty() ? profile.getWhatsappKey() : "NONE";

        // 3. Resolve the script file location
        File scriptFile = new File("src/main/resources/scripts/provision_tenant.sh");
        if (!scriptFile.exists()) {
            try {
                scriptFile = new ClassPathResource("scripts/provision_tenant.sh").getFile();
            } catch (Exception e) {
                log.warn("Could not find scripts/provision_tenant.sh via classpath, using fallback path.", e);
            }
        }

        log.info("Resolved provisioning script path: {}", scriptFile.getAbsolutePath());

        int exitCode = -1;
        try {
            // 4. Use ProcessBuilder to execute the bash script
            ProcessBuilder pb = new ProcessBuilder(
                    "bash",
                    scriptFile.getAbsolutePath(),
                    tenantId.toString(),
                    dbPrefix,
                    adminEmail,
                    routerIp,
                    rzpKey,
                    rzpSecret,
                    waToken,
                    waKey
            );

            // Merge error stream into standard output stream
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // 5. Read standard output / error stream and log it
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[PROVISIONING SCRIPT LOG] {}", line);
                }
            }

            exitCode = process.waitFor();
            log.info("Provisioning script process exited with code: {}", exitCode);

        } catch (Exception e) {
            log.error("Exception occurred during provisioning script execution: ", e);
        }

        // 6. Update status based on exit code
        if (exitCode == 0) {
            log.info("Provisioning successful! Activating tenant profile ID: {}", tenantId);
            profile.setStatus(TenantStatus.LIVE);
            emailNotificationService.sendWorkspaceLiveEmail(profile.getContactEmail(), profile.getPgName(), profile.getCustomDomain());
        } else {
            log.error("Provisioning failed with exit code {}. Suspending tenant profile ID: {}", exitCode, tenantId);
            profile.setStatus(TenantStatus.SUSPENDED);
        }

        tenantProfileRepository.save(profile);
    }

    private String sanitizeDbPrefix(String input) {
        if (input == null) {
            return "tenant";
        }
        return input.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
    }
}
