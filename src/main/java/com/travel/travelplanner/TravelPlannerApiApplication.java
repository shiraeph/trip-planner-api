package com.travel.travelplanner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class TravelPlannerApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(TravelPlannerApiApplication.class, args);
	}

}
