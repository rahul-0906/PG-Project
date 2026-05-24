package com.pgcrm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson configuration.
 *
 * Registers:
 * - Hibernate6Module: handles Hibernate lazy-loaded proxy serialization
 *   (fixes "ByteBuddyInterceptor" errors when entities are returned directly)
 * - JavaTimeModule: handles LocalDate, LocalDateTime, etc.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Handle Hibernate lazy-loading proxies gracefully
        Hibernate6Module hibernateModule = new Hibernate6Module();
        // Don't force-initialize lazy relations — serialize nulls instead
        hibernateModule.disable(Hibernate6Module.Feature.FORCE_LAZY_LOADING);
        mapper.registerModule(hibernateModule);

        // Handle Java 8 date/time types
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        return mapper;
    }
}
