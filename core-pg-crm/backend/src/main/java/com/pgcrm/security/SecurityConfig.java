package com.pgcrm.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthFilter;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    /**
     * Comma-separated list of allowed CORS origins read from config.
     * Set {@code APP_CORS_ALLOWED_ORIGINS} in production (e.g. https://app.yourpg.com).
     * Defaults to localhost origins for local development only.
     *
     * <p><strong>SEC-02:</strong> Never use a wildcard (*) with {@code allowCredentials=true}.
     * Origins must be explicitly enumerated.</p>
     */
    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:4173}")
    private String corsAllowedOriginsRaw;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, authEx) -> {
                    res.setContentType("application/json");
                    res.setStatus(401);
                    res.getWriter().write("{\"error\": \"Unauthorized: Session expired or invalid. Please login again.\"}");
                })
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/change-password").authenticated()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/webhooks/**").permitAll()       // Razorpay webhook
                .requestMatchers("/api/system/config").permitAll()    // Public branding endpoint
                .requestMatchers("/api/config/public").permitAll()    // New public config endpoint
                .requestMatchers("/api/super-admin/**").hasRole("SUPER_ADMIN")
                .requestMatchers("/api/owner/**").hasAnyRole("PG_OWNER", "SUPER_ADMIN")
                .requestMatchers("/api/manager/**").hasAnyRole("PG_MANAGER", "PG_OWNER", "SUPER_ADMIN")
                .requestMatchers("/api/guest/**").hasAnyRole("GUEST", "PG_MANAGER", "PG_OWNER", "SUPER_ADMIN")
                .requestMatchers("/api/reports/**").hasAnyRole("PG_OWNER", "PG_MANAGER", "SUPER_ADMIN")
                .requestMatchers("/api/inventory/**").hasAnyRole("PG_MANAGER", "PG_OWNER", "SUPER_ADMIN")
                .requestMatchers("/api/payments/**").hasAnyRole("PG_MANAGER", "PG_OWNER", "SUPER_ADMIN")
                // SEC-03: The /** permitAll wildcard has been removed. Static files must be served
                // exclusively by Nginx in production. This activates anyRequest().authenticated()
                // so every unmatched path requires a valid JWT — nothing is accidentally public.
                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // SEC-02: Explicit origins from config property instead of wildcard pattern.
        // allowedOriginPatterns("*") + allowCredentials(true) is a CORS misconfiguration.
        // Configure APP_CORS_ALLOWED_ORIGINS env var in production.
        List<String> origins = Arrays.asList(corsAllowedOriginsRaw.split(","));
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
