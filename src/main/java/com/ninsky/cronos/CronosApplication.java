package com.ninsky.cronos;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class CronosApplication {
    @PostConstruct
    public void init() {TimeZone.setDefault(TimeZone.getTimeZone("America/Mexico_City"));}
    public static void main(String[] args) {
        SpringApplication.run(CronosApplication.class, args);
    }
}
