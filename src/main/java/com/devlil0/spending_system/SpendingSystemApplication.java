package com.devlil0.spending_system;

import com.devlil0.spending_system.config.ApplicationTimeZoneConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class SpendingSystemApplication {

	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone(ApplicationTimeZoneConfig.SAO_PAULO_TIME_ZONE));
		SpringApplication.run(SpendingSystemApplication.class, args);
	}

}
