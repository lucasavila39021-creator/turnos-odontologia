package com.clinica.turnos.config;

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Fija la zona horaria JVM según APP_TIMEZONE (RN-GL-02).
 * Default: America/Argentina/Buenos_Aires.
 */
@Configuration
public class TimeZoneConfig {

    private static final Logger log = LoggerFactory.getLogger(TimeZoneConfig.class);

    private final String appTimezone;

    public TimeZoneConfig(@Value("${app.timezone:America/Argentina/Buenos_Aires}") String appTimezone) {
        this.appTimezone = appTimezone;
    }

    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone(appTimezone));
        log.info("timezone configured tz={}", appTimezone);
    }
}
