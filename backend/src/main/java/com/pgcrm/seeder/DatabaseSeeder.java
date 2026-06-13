package com.pgcrm.seeder;

import com.pgcrm.entity.User;
import com.pgcrm.entity.enums.Role;
import com.pgcrm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * DatabaseSeeder component to initialize the database with a master Owner account.
 * This runs on application startup only when the "prod" profile is NOT active.
 */
@Component
@Profile("!prod & !test")
@RequiredArgsConstructor
public class DatabaseSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // Safety check to ensure seeder only runs if no users exist
        if (userRepository.count() == 0) {
            User owner = User.builder()
                    .fullName("System Owner")
                    .email("owner@pgcrm.com")
                    .password(passwordEncoder.encode("Admin@123"))
                    .role(Role.PG_OWNER)
                    .active(true)
                    .firstLogin(false)
                    .mustChangePassword(false)
                    .build();

            userRepository.save(owner);

            System.out.println("=========================================");
            System.out.println("DATABASE INITIALIZED & OWNER SEEDED");
            System.out.println("Email: owner@pgcrm.com");
            System.out.println("Role: PG_OWNER");
            System.out.println("=========================================");
        }
    }
}
