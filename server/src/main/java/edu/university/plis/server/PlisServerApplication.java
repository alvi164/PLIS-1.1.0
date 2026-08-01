package edu.university.plis.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class PlisServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(PlisServerApplication.class, args);
    }
}
