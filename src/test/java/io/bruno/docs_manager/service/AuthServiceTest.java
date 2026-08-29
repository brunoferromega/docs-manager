package io.bruno.docs_manager.service;

import io.bruno.docs_manager.config.JwtProperties;
import io.bruno.docs_manager.dto.LoginRequest;
import io.bruno.docs_manager.entity.UserEntity;
import io.bruno.docs_manager.entity.UserRole;
import io.bruno.docs_manager.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String PASSWORD = "user123";

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtEncoder jwtEncoder;

    private AuthService authService;
    private BCryptPasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtEncoder,
                new JwtProperties("x".repeat(32), "docs-manager", Duration.ofHours(1)));
    }

    @Test
    @DisplayName("rejects an unknown username without issuing a token")
    void rejectsUnknownUser() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", PASSWORD)))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid username or password");

        verifyNoInteractions(jwtEncoder);
    }

    @Test
    @DisplayName("rejects a wrong password with the same message as an unknown user")
    void rejectsWrongPassword() {
        when(userRepository.findByUsername("user")).thenReturn(Optional.of(user(true)));

        assertThatThrownBy(() -> authService.login(new LoginRequest("user", "not-the-password")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid username or password");

        verifyNoInteractions(jwtEncoder);
    }

    @Test
    @DisplayName("rejects a disabled account even with the right password")
    void rejectsDisabledAccount() {
        when(userRepository.findByUsername("user")).thenReturn(Optional.of(user(false)));

        assertThatThrownBy(() -> authService.login(new LoginRequest("user", PASSWORD)))
                .isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(jwtEncoder);
    }

    private UserEntity user(boolean enabled) {
        UserEntity user = BeanUtils.instantiateClass(UserEntity.class);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(user, "username", "user");
        ReflectionTestUtils.setField(user, "passwordHash", passwordEncoder.encode(PASSWORD));
        ReflectionTestUtils.setField(user, "role", UserRole.USER);
        ReflectionTestUtils.setField(user, "enabled", enabled);
        return user;
    }
}
