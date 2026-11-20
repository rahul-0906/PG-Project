package com.pgcrm.seeder;

import com.pgcrm.entity.User;
import com.pgcrm.entity.enums.Role;
import com.pgcrm.multitenancy.DeploymentProperties;
import com.pgcrm.multitenancy.TenantContext;
import com.pgcrm.multitenancy.TenantRegistryService;
import com.pgcrm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * DatabaseSeeder component to initialize the database with a master Owner account.
 * This runs on application startup only when the "prod" profile is NOT active.
 */
@Component
@Profile("!prod")
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DeploymentProperties deploymentProperties;
    private final TenantRegistryService tenantRegistryService;

    @Value("${pg.default-owner.email:owner@pgcrm.com}")
    private String defaultOwnerEmail;

    @Value("${pg.default-owner.name:System Owner}")
    private String defaultOwnerName;

    @Value("${pg.default-owner.password:Owner@123}")
    private String defaultOwnerPassword;

    @Override
    public void run(String... args) throws Exception {
        if (deploymentProperties.isDedicated()) {
            tenantRegistryService.findTenantBySlug(deploymentProperties.getDedicatedTenant())
                    .ifPresent(TenantContext::setTenant);
        }
        try {
            java.util.Optional<User> ownerOpt = userRepository.findByEmailIgnoreCase("owner@pgcrm.com");
            if (ownerOpt.isEmpty()) {
                User owner = User.builder()
                        .fullName(defaultOwnerName)
                        .email("owner@pgcrm.com")
                        .password(passwordEncoder.encode("Admin@123"))
                        .role(Role.PG_OWNER)
                        .active(true)
                        .firstLogin(true)
                        .mustChangePassword(true)
                        .build();
                userRepository.save(owner);
                log.info("🔒 Default Owner account seeded.");
            } else {
                log.info("Default Owner already exists, skipping seed.");
            }
        } finally {
            if (deploymentProperties.isDedicated()) {
                TenantContext.clear();
            }
        }
    }
}

