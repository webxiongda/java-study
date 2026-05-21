package com.javastudy.dto;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record AuthRequest(String username, String secret) {
    }

    public record AuthResponse(String token, UserDto user) {
    }

    public record UserDto(Long id, String username) {
    }
}
