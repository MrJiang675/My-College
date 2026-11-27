package com.argus.rag.auth.service;

import com.argus.rag.auth.CurrentUserService;
import com.argus.rag.auth.security.JwtAccessTokenService;
import com.argus.rag.auth.security.RefreshTokenService;
import com.argus.rag.common.enums.SystemRole;
import com.argus.rag.common.enums.UserStatus;
import com.argus.rag.common.exception.BusinessException;
import com.argus.rag.user.mapper.UserMapper;
import com.argus.rag.user.model.entity.User;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "账号或密码错误";
    private static final String PASSWORD_REQUIRED_MESSAGE = "该账号需要密码登录";
    private static final int MAX_LOGIN_ID_LENGTH = 128;
    private static final int MAX_USERNAME_LENGTH = 64;
    private static final int MAX_PASSWORD_LENGTH = 256;
    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final Set<String> RESERVED_USERNAMES = Set.of(
            "admin", "argus_admin", "root", "null", "undefined", "system"
    );

    private final UserMapper userMapper;
    private final PasswordHasher passwordHasher;
    private final JwtAccessTokenService jwtAccessTokenService;
    private final RefreshTokenService refreshTokenService;
    private final Clock clock;

    public AuthService(
            UserMapper userMapper,
            PasswordHasher passwordHasher,
            JwtAccessTokenService jwtAccessTokenService,
            RefreshTokenService refreshTokenService,
            Clock clock
    ) {
        this.userMapper = userMapper;
        this.passwordHasher = passwordHasher;
        this.jwtAccessTokenService = jwtAccessTokenService;
        this.refreshTokenService = refreshTokenService;
        this.clock = clock;
    }

    @Transactional
    public AuthTokens login(String loginId, String password) {
        LoginCommand command = validateLoginCommand(loginId, password);
        User user = findUserForLogin(command.loginId());
        if (user == null) {
            if (command.hasPassword()) {
                throw new BusinessException(INVALID_CREDENTIALS_MESSAGE);
            }
            user = createExperienceUser(command.loginId());
        }
        ensureUserCanLogin(user, command.password());
        refreshTokenService.revokeActiveTokens(user.getId());
        RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokenService.issueToken(user.getId());
        updateSuccessfulLogin(user.getId());
        log.info("用户登录成功: userId={}, username={}", user.getId(), user.getUsername());
        return new AuthTokens(
                user.getId(),
                issueAccessToken(user),
                refreshToken.refreshToken(),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                toCurrentUser(user)
        );
    }

    @Transactional
    public AuthTokens refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException("refresh token 不存在或已失效");
        }
        RefreshTokenRecord activeToken = refreshTokenService.findActiveToken(refreshToken)
                .orElseThrow(() -> new BusinessException("refresh token 不存在或已失效"));
        User user = userMapper.selectById(activeToken.userId());
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        ensureRefreshAllowed(user);
        if (!refreshTokenService.revokeTokenById(activeToken.id())) {
            refreshTokenService.revokeActiveTokens(user.getId());
            log.warn("refresh token 重放攻击: userId={}", user.getId());
            throw new BusinessException("refresh token 已被使用，请重新登录");
        }
        RefreshTokenService.IssuedRefreshToken nextRefreshToken = refreshTokenService.issueToken(user.getId());
        log.info("令牌刷新成功: userId={}", user.getId());
        return new AuthTokens(
                user.getId(),
                issueAccessToken(user),
                nextRefreshToken.refreshToken(),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                toCurrentUser(user)
        );
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        boolean revoked = refreshTokenService.revokeToken(refreshToken);
        if (revoked) {
            log.info("用户登出成功");
        }
    }

    private CurrentUserService.CurrentUser toCurrentUser(User user) {
        return new CurrentUserService.CurrentUser(
                user.getId(),
                user.getUserCode(),
                user.getDisplayName(),
                user.getSystemRole(),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                PasswordHasher.isPasswordEnabled(user.getPasswordHash())
        );
    }

    private LoginCommand validateLoginCommand(String loginId, String password) {
        String normalizedLoginId = normalizeLoginId(loginId);
        boolean hasPassword = password != null && !password.isBlank();
        if (hasPassword) {
            if (password.length() > MAX_PASSWORD_LENGTH) {
                throw new BusinessException("密码长度非法");
            }
            if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
                throw new BusinessException("密码长度超过安全上限，请控制在 72 字节以内");
            }
        } else {
            ensureExperienceUsernameAllowed(normalizedLoginId);
        }
        return new LoginCommand(normalizedLoginId, hasPassword ? password : null, hasPassword);
    }

    private String normalizeLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            throw new BusinessException("登录标识不能为空");
        }
        String normalizedLoginId = loginId.trim();
        if (normalizedLoginId.length() > MAX_LOGIN_ID_LENGTH) {
            throw new BusinessException("登录标识长度非法");
        }
        return normalizedLoginId;
    }

    private void ensureExperienceUsernameAllowed(String username) {
        String normalizedUsername = normalizeUsername(username);
        if (RESERVED_USERNAMES.contains(normalizedUsername.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(PASSWORD_REQUIRED_MESSAGE);
        }
    }

    private String normalizeUsername(String username) {
        String normalizedValue = normalizeRequiredValue(
                username,
                "用户名不能为空",
                "用户名长度不能超过 64",
                MAX_USERNAME_LENGTH
        );
        if (!USERNAME_PATTERN.matcher(normalizedValue).matches()) {
            throw new BusinessException("用户名不合法");
        }
        return normalizedValue;
    }

    private String normalizeRequiredValue(String value, String blankMessage, String lengthMessage, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(blankMessage);
        }
        String normalizedValue = value.trim();
        if (normalizedValue.length() > maxLength) {
            throw new BusinessException(lengthMessage);
        }
        return normalizedValue;
    }

    private User findUserForLogin(String loginId) {
        List<User> users = userMapper.selectByLoginIdForUpdate(loginId);
        if (users.isEmpty()) {
            return null;
        }
        ensureUniqueLoginMatch(users);
        return users.getFirst();
    }

    private void ensureUniqueLoginMatch(List<User> users) {
        Set<Long> userIds = new LinkedHashSet<>();
        for (User user : users) {
            userIds.add(user.getId());
        }
        if (userIds.size() > 1) {
            throw new BusinessException("登录标识存在冲突，请联系管理员处理");
        }
    }

    private User createExperienceUser(String username) {
        User user = new User();
        user.setUserCode(username);
        user.setUsername(username);
        user.setEmail(username + "." + UUID.randomUUID() + "@demo.local");
        user.setDisplayName(username);
        user.setPasswordHash(PasswordHasher.PASSWORDLESS_HASH);
        user.setSystemRole(SystemRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setMustChangePassword(false);
        userMapper.insertUserIfAbsent(user);

        User persisted = findUserForLogin(username);
        if (persisted == null) {
            throw new BusinessException("该用户名暂不可用，请重试");
        }
        log.info("体验用户已创建或复用: userId={}, username={}", persisted.getId(), persisted.getUsername());
        return persisted;
    }

    private void ensureUserCanLogin(User user, String password) {
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new BusinessException("账号已被禁用");
        }
        if (password == null) {
            if (user.getSystemRole() == SystemRole.ADMIN) {
                throw new BusinessException(PASSWORD_REQUIRED_MESSAGE);
            }
            return;
        }
        if (!PasswordHasher.isPasswordEnabled(user.getPasswordHash())
                || !passwordHasher.matches(password, user.getPasswordHash())) {
            throw new BusinessException(INVALID_CREDENTIALS_MESSAGE);
        }
    }

    private void ensureRefreshAllowed(User user) {
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new BusinessException("账号已被禁用");
        }
    }

    private void updateSuccessfulLogin(Long userId) {
        LambdaUpdateWrapper<User> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(User::getId, userId)
                .set(User::getLastLoginAt, LocalDateTime.now(clock));
        userMapper.update(null, updateWrapper);
    }

    private String issueAccessToken(User user) {
        return jwtAccessTokenService.issueToken(
                new JwtAccessTokenService.TokenSubject(
                        user.getId(),
                        user.getUserCode(),
                        user.getDisplayName(),
                        user.getSystemRole(),
                        Boolean.TRUE.equals(user.getMustChangePassword())
                )
        );
    }

    public record AuthTokens(
            Long userId,
            String accessToken,
            String refreshToken,
            boolean mustChangePassword,
            CurrentUserService.CurrentUser currentUser
    ) {
    }

    private record LoginCommand(String loginId, String password, boolean hasPassword) {
    }
}
