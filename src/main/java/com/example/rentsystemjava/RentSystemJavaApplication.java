package com.example.rentsystemjava;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RentSystemJavaApplication {

    public static void main(String[] args) {
        SpringApplication.run(RentSystemJavaApplication.class, args);
    }

}
