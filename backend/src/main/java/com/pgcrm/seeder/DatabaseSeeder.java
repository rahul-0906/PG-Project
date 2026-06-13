package com.pgcrm.seeder;

import com.pgcrm.entity.User;
import com.pgcrm.entity.enums.Role;
import com.pgcrm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
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
public class DatabaseSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${pg.default-owner.email:owner@pgcrm.com}")
    private String defaultOwnerEmail;

    @Value("${pg.default-owner.name:System Owner}")
    private String defaultOwnerName;

    @Value("${pg.default-owner.password:Owner@123}")
    private String defaultOwnerPassword;

    @Override
    public void run(String... args) throws Exception {
        // Safety check to ensure seeder only runs if no users exist
        if (userRepository.count() == 0) {
            User owner = User.builder()
                    .fullName(defaultOwnerName)
                    .email(defaultOwnerEmail)
                    .password(passwordEncoder.encode(defaultOwnerPassword))
                    .role(Role.PG_OWNER)
                    .active(true)
                    .firstLogin(false)
                    .mustChangePassword(false)
                    .build();

            userRepository.save(owner);

            System.out.println("=========================================");
            System.out.println("DATABASE INITIALIZED & OWNER SEEDED");
            System.out.println("Name: " + defaultOwnerName);
            System.out.println("Email: " + defaultOwnerEmail);
            System.out.println("Role: PG_OWNER");
            System.out.println("=========================================");
        }
    }
}
