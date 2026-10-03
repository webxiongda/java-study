package com.javastudy.security;

import com.javastudy.repository.UserRepository;
import com.javastudy.service.AutoLoginService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final AutoLoginService autoLoginService;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository, AutoLoginService autoLoginService) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.autoLoginService = autoLoginService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        var header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                var username = jwtService.subject(header.substring(7));
                userRepository.findByUsername(username).ifPresent(user -> {
                    var authentication = new UsernamePasswordAuthenticationToken(user, null, List.of());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
            } catch (RuntimeException ignored) {
                SecurityContextHolder.clearContext();
            }
        }

        // 免登录兜底：没拿到有效身份时落到默认用户。
        // 必须在 finally 之外判断，确保下游 controller 的 currentUser() 拿得到 principal。
        if (SecurityContextHolder.getContext().getAuthentication() == null && autoLoginService.isEnabled()) {
            var defaultUser = autoLoginService.resolveDefaultUser();
            if (defaultUser != null) {
                SecurityContextHolder.getContext().setAuthentication(autoLoginService.anonymousAuthenticationFor(defaultUser));
            }
        }

        chain.doFilter(request, response);
    }
}