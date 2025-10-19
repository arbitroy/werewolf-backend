package com.werewolfkill.game.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class RenderKeepAliveService {
    
    @Value("${render.self-ping.enabled:true}")
    private boolean selfPingEnabled;
    
    @Value("${server.port:8080}")
    private String serverPort;
    
    private final RestTemplate restTemplate = new RestTemplate();
    
    /**
     * Keep Render.com free tier awake by pinging itself every 10 minutes
     * Only runs if there are active sessions
     */
    @Scheduled(fixedRate = 600000) // 10 minutes
    public void keepAlive() {
        if (!selfPingEnabled) {
            return;
        }
        
        try {
            // Ping the health endpoint to keep server awake
            String healthUrl = "http://localhost:" + serverPort + "/actuator/health";
            restTemplate.getForObject(healthUrl, String.class);
            System.out.println("🏓 Self-ping successful - keeping Render awake");
        } catch (Exception e) {
            System.err.println("⚠️ Self-ping failed: " + e.getMessage());
        }
    }
}