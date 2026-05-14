package com.devlil0.spending_system.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;
import java.util.TimeZone;

@Configuration
public class ApplicationTimeZoneConfig {

    public static final String SAO_PAULO_TIME_ZONE = "America/Sao_Paulo";
    public static final ZoneId SAO_PAULO_ZONE_ID = ZoneId.of(SAO_PAULO_TIME_ZONE);

    @PostConstruct
    void setDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(SAO_PAULO_TIME_ZONE));
    }

    @Bean
    Clock saoPauloClock() {
        return Clock.system(SAO_PAULO_ZONE_ID);
    }

}
