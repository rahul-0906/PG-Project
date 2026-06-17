package com.pgcrm.controlplane.service;

import com.pgcrm.controlplane.entity.TenantInstance;
import com.pgcrm.controlplane.entity.TenantStatus;
import com.pgcrm.controlplane.repository.TenantInstanceRepository;
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

    /**
     * Provisions a new B2B client's PG-CORE single-tenant instance.
     */
    @Async
    @Transactional
    public void provisionNewTenant(TenantInstance tenant) {
        log.info("Starting automated provisioning process for tenant: {}", tenant.getDomainName());

        try {
            // 1. Assign a unique port
            Integer maxPort = tenantInstanceRepository.findMaxAllocatedPort();
            int port = (maxPort == null || maxPort < 8081) ? 8081 : maxPort + 1;
            tenant.setAllocatedPort(port);
            
            // Set local loopback/local address as VPS IP address for local demo provisioning
            tenant.setVpsIpAddress("127.0.0.1");
            tenant.setStatus(TenantStatus.PROVISIONING);
            tenantInstanceRepository.saveAndFlush(tenant);

            log.info("Assigned port {} to tenant {}", port, tenant.getDomainName());

            // 2. Generate database password
            String dbPassword = "pwd_" + UUID.randomUUID().toString().substring(0, 10);

            // 3. Build ProcessBuilder for the bash script
            File userDir = new File(System.getProperty("user.dir"));
            File projectRoot = userDir.getParentFile().getParentFile();
            
            log.info("Current user.dir: {}, Calculated projectRoot: {}", userDir.getAbsolutePath(), projectRoot.getAbsolutePath());

            // Execute using bash (standard on Linux hosts, and supported in Windows Git Bash/WSL sandbox environments)
            ProcessBuilder pb = new ProcessBuilder(
                    "bash", 
                    "scripts/provision_tenant.sh", 
                    tenant.getDomainName(), 
                    dbPassword, 
                    String.valueOf(port), 
                    tenant.getClient().getEmail()
            );
            pb.directory(projectRoot);
            pb.redirectErrorStream(true);

            log.info("Executing provisioning command: bash scripts/provision_tenant.sh {} [PASSWORD] {} {}", 
                    tenant.getDomainName(), port, tenant.getClient().getEmail());

            Process process = pb.start();

            // 4. Capture output logs
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[PROVISIONING SCRIPT] {}", line);
                }
            }

            // 5. Wait with timeout (e.g. 5 minutes)
            boolean completed = process.waitFor(5, TimeUnit.MINUTES);
            if (!completed) {
                log.error("Provisioning execution timed out for subdomain: {}", tenant.getDomainName());
                process.destroyForcibly();
                throw new RuntimeException("Provisioning timed out");
            }

            int exitCode = process.exitValue();
            log.info("Provisioning script exited with code: {} for subdomain: {}", exitCode, tenant.getDomainName());

            if (exitCode == 0) {
                // 6. Update tenant instance status to ACTIVE upon success
                tenant.setStatus(TenantStatus.ACTIVE);
                tenantInstanceRepository.save(tenant);
                log.info("Successfully provisioned and activated tenant: {}", tenant.getDomainName());
            } else {
                throw new RuntimeException("Provisioning script failed with exit code: " + exitCode);
            }

        } catch (Exception e) {
            log.error("Error occurred while provisioning tenant: ", e);
            tenant.setStatus(TenantStatus.DELETED);
            tenantInstanceRepository.save(tenant);
        }
    }
}
