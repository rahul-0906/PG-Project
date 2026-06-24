package com.pgcrm.controlplane.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/webhooks/**", "/api/public/**")
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/webhooks/**", "/api/public/**").permitAll()
                .anyRequest().permitAll() // Allow other requests for now to prevent local dev lockout
            );
        return http.build();
    }
}
