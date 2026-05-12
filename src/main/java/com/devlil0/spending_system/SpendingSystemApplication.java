package com.devlil0.spending_system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SpendingSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpendingSystemApplication.class, args);
	}

}
