package com.paperpilot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.paperpilot.mapper")
public class PaperPilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaperPilotApplication.class, args);
    }
}
