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
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // ✅ IMPROVED: More explicit CORS configuration
        registry.addEndpoint("/ws/game")
                .setAllowedOriginPatterns("*")
                .setAllowedHeaders("*")
                .setAllowedMethods("*")
                .withSockJS()
                .setClientLibraryUrl("https://cdn.jsdelivr.net/npm/sockjs-client@1.5.2/dist/sockjs.min.js");
        
        // Native WebSocket without SockJS
        registry.addEndpoint("/ws/game")
                .setAllowedOriginPatterns("*")
                .setAllowedHeaders("*")
                .setAllowedMethods("*");
    }
}