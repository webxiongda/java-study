package com.javastudy.service;

import com.javastudy.domain.User;
import com.javastudy.dto.AuthDtos;
import com.javastudy.repository.UserRepository;
import com.javastudy.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final InitialImportService initialImportService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService, InitialImportService initialImportService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.initialImportService = initialImportService;
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.AuthRequest request) {
        validate(request);
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("账号已存在");
        }
        boolean firstUser = userRepository.count() == 0;
        var user = userRepository.save(new User(request.username().trim(), passwordEncoder.encode(request.secret())));
        if (firstUser) {
            initialImportService.importFor(user);
        }
        return response(user);
    }

    @Transactional(readOnly = true)
    public AuthDtos.AuthResponse login(AuthDtos.AuthRequest request) {
        var user = userRepository.findByUsername(request.username().trim()).orElseThrow(() -> new IllegalArgumentException("账号或密钥错误"));
        if (!passwordEncoder.matches(request.secret(), user.getSecretHash())) {
            throw new IllegalArgumentException("账号或密钥错误");
        }
        return response(user);
    }

    public AuthDtos.UserDto userDto(User user) {
        return new AuthDtos.UserDto(user.getId(), user.getUsername());
    }

    private AuthDtos.AuthResponse response(User user) {
        return new AuthDtos.AuthResponse(jwtService.createToken(user), userDto(user));
    }

    private void validate(AuthDtos.AuthRequest request) {
        if (request.username() == null || !request.username().matches("[A-Za-z0-9_\\-]{3,64}")) {
            throw new IllegalArgumentException("账号只能包含字母、数字、下划线和短横线，长度 3-64");
        }
        if (request.secret() == null || request.secret().length() < 16) {
            throw new IllegalArgumentException("密钥至少 16 位");
        }
    }
}
