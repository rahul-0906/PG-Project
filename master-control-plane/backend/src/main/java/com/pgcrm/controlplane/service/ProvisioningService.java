package com.pgcrm.controlplane.service;

import com.pgcrm.controlplane.entity.TenantInstance;
import com.pgcrm.controlplane.entity.TenantStatus;
import com.pgcrm.controlplane.model.entity.TenantProfile;
import com.pgcrm.controlplane.repository.TenantInstanceRepository;
import com.pgcrm.controlplane.repository.TenantProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProvisioningService {

    private final TenantInstanceRepository tenantInstanceRepository;
    private final TenantProfileRepository tenantProfileRepository;

    /**
     * Provisions a new B2B client's PG-CORE single-tenant instance (legacy method).
     */
    @Async
    @Transactional
    public void provisionNewTenant(UUID tenantId) {
        TenantInstance tenant = tenantInstanceRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant instance not found: " + tenantId));
        log.info("Starting automated provisioning process for tenant: {}", tenant.getDomainName());

        try {
            Integer maxPort = tenantInstanceRepository.findMaxAllocatedPort();
            int port = (maxPort == null || maxPort < 8081) ? 8081 : maxPort + 1;
            tenant.setAllocatedPort(port);
            
            tenant.setVpsIpAddress("127.0.0.1");
            tenant.setStatus(TenantStatus.PROVISIONING);
            tenantInstanceRepository.saveAndFlush(tenant);

            log.info("Assigned port {} to tenant {}", port, tenant.getDomainName());

            String dbPassword = "pwd_" + UUID.randomUUID().toString().substring(0, 10);
            File userDir = new File(System.getProperty("user.dir"));
            File projectRoot = userDir.getParentFile().getParentFile();

            String whatsappToken = tenant.getWhatsappToken() != null ? tenant.getWhatsappToken() : "";
            String razorpayKeyId = tenant.getRazorpayKeyId() != null ? tenant.getRazorpayKeyId() : "";
            String razorpayKeySecret = tenant.getRazorpayKeySecret() != null ? tenant.getRazorpayKeySecret() : "";
            String primaryColor = tenant.getPrimaryColor() != null ? tenant.getPrimaryColor() : "";

            ProcessBuilder pb = new ProcessBuilder(
                    "bash", 
                    "scripts/provision_tenant.sh", 
                    tenant.getDomainName(), 
                    dbPassword, 
                    String.valueOf(port), 
                    tenant.getClient().getEmail(),
                    whatsappToken,
                    razorpayKeyId,
                    razorpayKeySecret,
                    primaryColor
            );
            pb.directory(projectRoot);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[PROVISIONING SCRIPT] {}", line);
                }
            }

            boolean completed = process.waitFor(5, TimeUnit.MINUTES);
            if (!completed) {
                process.destroyForcibly();
                throw new RuntimeException("Provisioning timed out");
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                tenant.setStatus(TenantStatus.ACTIVE);
                tenantInstanceRepository.save(tenant);
            } else {
                throw new RuntimeException("Provisioning script failed with exit code: " + exitCode);
            }

        } catch (Exception e) {
            log.error("Error occurred while provisioning tenant: ", e);
            tenant.setStatus(TenantStatus.DELETED);
            tenantInstanceRepository.save(tenant);
        }
    }

    /**
     * Executes the E2E onboarding provisioning pipeline using the TenantProfile record.
     */
    @Async
    @Transactional
    public void executeProvisioningPipeline(TenantProfile tenant) {
        log.info("Starting automated provisioning pipeline for custom domain: {}", tenant.getCustomDomain());

        try {
            // Extract the 8 required parameters from the TenantProfile object with safety fallbacks
            String tenantIdStr = tenant.getId() != null ? tenant.getId().toString() : "NONE";
            String dbPrefix = tenant.getCustomDomain() != null && !tenant.getCustomDomain().isEmpty() 
                    ? sanitizeDbPrefix(tenant.getCustomDomain()) : "tenant";
            String adminEmail = tenant.getContactEmail() != null && !tenant.getContactEmail().isEmpty() 
                    ? tenant.getContactEmail() : "NONE";
            String routerIp = tenant.getRouterIp() != null && !tenant.getRouterIp().isEmpty() 
                    ? tenant.getRouterIp() : "127.0.0.1";
            String rzpKey = tenant.getRazorpayKey() != null && !tenant.getRazorpayKey().isEmpty() 
                    ? tenant.getRazorpayKey() : "NONE";
            String rzpSecret = tenant.getRazorpaySecret() != null && !tenant.getRazorpaySecret().isEmpty() 
                    ? tenant.getRazorpaySecret() : "NONE";
            String waToken = tenant.getWhatsappToken() != null && !tenant.getWhatsappToken().isEmpty() 
                    ? tenant.getWhatsappToken() : "NONE";
            String waKey = tenant.getWhatsappKey() != null && !tenant.getWhatsappKey().isEmpty() 
                    ? tenant.getWhatsappKey() : "NONE";
            String customTld = tenant.getCustomTld() != null && !tenant.getCustomTld().isEmpty() 
                    ? tenant.getCustomTld() : "NONE";

            // Update status to PROVISIONING
            tenant.setStatus(com.pgcrm.controlplane.model.enums.TenantStatus.PROVISIONING);
            tenantProfileRepository.saveAndFlush(tenant);

            // Execute the bash script
            ProcessBuilder pb = new ProcessBuilder(
                    "bash", 
                    "src/main/resources/scripts/provision_tenant.sh", 
                    tenantIdStr, 
                    dbPrefix, 
                    adminEmail, 
                    routerIp,
                    rzpKey,
                    rzpSecret,
                    waToken,
                    waKey,
                    customTld
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // Pipe output stream to SLF4J logs prefixed with [PROVISIONING SCRIPT]
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[PROVISIONING SCRIPT] {}", line);
                }
            }

            boolean completed = process.waitFor(5, TimeUnit.MINUTES);
            if (!completed) {
                log.error("Provisioning timed out for tenant ID: {}", tenantIdStr);
                process.destroyForcibly();
                throw new RuntimeException("Provisioning timed out");
            }

            int exitCode = process.exitValue();
            log.info("Provisioning script exited with code: {} for tenant: {}", exitCode, tenantIdStr);

            if (exitCode == 0) {
                tenant.setStatus(com.pgcrm.controlplane.model.enums.TenantStatus.LIVE);
                tenantProfileRepository.save(tenant);
                log.info("Successfully provisioned and activated tenant: {}", tenantIdStr);
            } else {
                throw new RuntimeException("Provisioning script failed with exit code: " + exitCode);
            }

        } catch (Exception e) {
            log.error("Error occurred during executeProvisioningPipeline: ", e);
            tenant.setStatus(com.pgcrm.controlplane.model.enums.TenantStatus.SUSPENDED);
            tenantProfileRepository.save(tenant);
        }
    }

    private String sanitizeDbPrefix(String input) {
        if (input == null) {
            return "tenant";
        }
        return input.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
    }
}
