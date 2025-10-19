package com.werewolfkill.game;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling  // ✅ ADD THIS
public class WerewolfGameApplication {
    public static void main(String[] args) {
        SpringApplication.run(WerewolfGameApplication.class, args);
    }
}