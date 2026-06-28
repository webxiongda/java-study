package com.javastudy.service;

import com.javastudy.domain.User;
import com.javastudy.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DemoAccountBootstrap implements ApplicationRunner {
    private final boolean enabled;
    private final String username;
    private final String secret;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final InitialImportService initialImportService;

    public DemoAccountBootstrap(
        @Value("${app.demo-account.enabled:true}") boolean enabled,
        @Value("${app.demo-account.username:demo}") String username,
        @Value("${app.demo-account.secret:java-study-demo-secret-2026}") String secret,
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        InitialImportService initialImportService
    ) {
        this.enabled = enabled;
        this.username = username;
        this.secret = secret;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.initialImportService = initialImportService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        var normalizedUsername = username.trim();
        var user = userRepository.findByUsername(normalizedUsername)
            .orElseGet(() -> createDemoUser(normalizedUsername));
        if (!passwordEncoder.matches(secret, user.getSecretHash())) {
            user.updateSecretHash(passwordEncoder.encode(secret));
        }
    }

    private User createDemoUser(String normalizedUsername) {
        var user = userRepository.save(new User(normalizedUsername, passwordEncoder.encode(secret)));
        initialImportService.importFor(user);
        return user;
    }
}
