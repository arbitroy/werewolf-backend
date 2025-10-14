package com.werewolfkill.game.service;

import com.werewolfkill.game.dto.AuthResponse;
import com.werewolfkill.game.dto.LoginRequest;
import com.werewolfkill.game.dto.RegisterRequest;
import com.werewolfkill.game.model.User;
import com.werewolfkill.game.repository.UserRepository;
import com.werewolfkill.game.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user = userRepository.save(user);
        
        // Generate real JWT token
        String token = jwtUtil.generateToken(user.getUsername(), user.getId());
        
        return new AuthResponse(
            token,
            user.getId().toString(),
            user.getUsername()
        );
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
            .orElseThrow(() -> new RuntimeException("Invalid credentials"));
        
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }
        
        // Generate real JWT token
        String token = jwtUtil.generateToken(user.getUsername(), user.getId());
        
        return new AuthResponse(
            token,
            user.getId().toString(),
            user.getUsername()
        );
    }
}