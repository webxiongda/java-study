package com.javastudy.service;

import com.javastudy.domain.User;
import com.javastudy.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 免登录支持：未携带 token（或 token 失效）时，把请求落到一个真实存在的默认用户。
 *
 * <p>背景：业务数据（学习进度 / 笔记 / 错题 / 复习卡 / 面试作答）全部按 user_id 外键隔离，
 * 所以不能只把Security 放开——那样 controller 里的 currentUser(Authentication)
 * 依旧拿不到 principal。必须注入真实 User，user_id 链路才完整。
 *
 * <p>默认复用 DemoAccountBootstrap 建好的 demo 账号（它已在启动时导入初始数据），
 * 因此这里不再重复导入。设置 app.auth.auto-login-username=off 可关回原登录流程。
 */
@Service
public class AutoLoginService {
    private final UserRepository userRepository;
    private final String autoLoginUsername;
    private final String fallbackUsername;

    public AutoLoginService(
        UserRepository userRepository,
        @Value("${app.auth.auto-login-username:demo}") String autoLoginUsername,
        @Value("${app.demo-account.username:demo}") String fallbackUsername
    ) {
        this.userRepository = userRepository;
        this.autoLoginUsername = autoLoginUsername;
        this.fallbackUsername = fallbackUsername;
    }

    /** 未配置或显式设为 off 时，关闭免登录。 */
    public boolean isEnabled() {
        return !"off".equalsIgnoreCase(autoLoginUsername) && !autoLoginUsername.isBlank();
    }

    /**
     * 解析免登录用的默认用户。优先用 app.auth.auto-login-username，
     * 回退到 demo 账号；都找不到时回落到库中第一个用户。
     */
    @Transactional(readOnly = true)
    public User resolveDefaultUser() {
        var byConfigured = findByName(autoLoginUsername);
        if (byConfigured != null) {
            return byConfigured;
        }
        var byDemo = findByName(fallbackUsername);
        if (byDemo != null) {
            return byDemo;
        }
        return userRepository.findAll().stream().findFirst().orElse(null);
    }

    /** 构造一个已认证的 Authentication，供无 token 请求使用。 */
    public Authentication anonymousAuthenticationFor(User user) {
        return new UsernamePasswordAuthenticationToken(user, null, List.of());
    }

    private User findByName(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return userRepository.findByUsername(username.trim()).orElse(null);
    }
}