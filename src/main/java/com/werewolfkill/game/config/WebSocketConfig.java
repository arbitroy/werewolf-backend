package com.werewolfkill.game.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple broker for broadcasting
        config.enableSimpleBroker("/topic", "/queue");
        
        // Prefix for client messages
        config.setApplicationDestinationPrefixes("/app");
        
        // Note: Heartbeats are configured on the client side (Flutter)
        // Spring WebSocket handles server-side heartbeats automatically
        
        System.out.println("✅ Message broker configured");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // ✅ CRITICAL FIX: Native WebSocket ONLY - NO SockJS
        // 
        // Why: SockJS causes the `:0` port issue with Flutter's stomp_dart_client
        // The client expects a pure WebSocket connection, not SockJS fallback
        //
        // Before (WRONG):
        // registry.addEndpoint("/ws/game").setAllowedOriginPatterns("*").withSockJS();
        //
        // After (CORRECT):
        registry.addEndpoint("/ws/game")
                .setAllowedOriginPatterns("*");
        
        System.out.println("✅ WebSocket endpoint registered: /ws/game");
        System.out.println("   - Protocol: Native WebSocket (STOMP over WebSocket)");
        System.out.println("   - NO SockJS fallback");
        System.out.println("   - Allowed origins: * (all origins)");
    }
}