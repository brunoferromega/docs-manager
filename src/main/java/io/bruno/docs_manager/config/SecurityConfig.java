package io.bruno.docs_manager.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.bruno.docs_manager.entity.UserRole;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MIN_SECRET_BYTES = 32;

    // Role names without the ROLE_ prefix, which hasRole/hasAnyRole add.
    private static final String ADMIN = UserRole.ADMIN.name();
    private static final String USER = UserRole.USER.name();
    private static final String VIEWER = UserRole.VIEWER.name();

    private final JwtProperties jwtProperties;

    public SecurityConfig(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        AuthenticationEntryPoint entryPoint = ProblemDetailAuthenticationHandlers.entryPoint(objectMapper);
        AccessDeniedHandler accessDeniedHandler = ProblemDetailAuthenticationHandlers.accessDeniedHandler(objectMapper);

        return http
                // No browser sessions or forms: every request carries its own bearer token.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        // VIEWER is read-only, USER may create and edit, only ADMIN may delete.
                        .requestMatchers(HttpMethod.GET, "/api/documents/**")
                                .hasAnyRole(ADMIN, USER, VIEWER)
                        .requestMatchers(HttpMethod.POST, "/api/documents/**").hasAnyRole(ADMIN, USER)
                        .requestMatchers(HttpMethod.PUT, "/api/documents/**").hasAnyRole(ADMIN, USER)
                        .requestMatchers(HttpMethod.PATCH, "/api/documents/**").hasAnyRole(ADMIN, USER)
                        .requestMatchers(HttpMethod.DELETE, "/api/documents/**").hasRole(ADMIN)
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(authoritiesConverter()))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }

    /** Turns the {@code roles} claim into {@code ROLE_*} authorities. */
    private JwtAuthenticationConverter authoritiesConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey()));
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(secretKey()).build();
    }

    private SecretKeySpec secretKey() {
        String secret = jwtProperties.secret();
        byte[] keyBytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);

        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.security.jwt.secret must be at least %d bytes for HS256, got %d"
                            .formatted(MIN_SECRET_BYTES, keyBytes.length));
        }
        return new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
    }
}
