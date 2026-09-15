package com.minibank.common.security;

import com.minibank.auth.repository.SessionRepository;
import com.minibank.common.correlation.MdcCorrelationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final SessionRepository sessionRepository;
    private final JsonAuthenticationEntryPoint authenticationEntryPoint;

    public SecurityConfig(SessionRepository sessionRepository, JsonAuthenticationEntryPoint authenticationEntryPoint) {
        this.sessionRepository = sessionRepository;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    private static final String[] PUBLIC_ENDPOINTS = {
            "/clients/register",
            "/clients/register/*/confirm",
            "/auth/login/initiate",
            "/auth/login/confirm",
            "/actuator/health",
            "/actuator/health/**",
            // BUGFIX (found via a real docker-compose run, see README "Architecture decisions -
            // Stage 2 fixes"): these were missing, so Prometheus - which can't send a
            // X-Session-Token - got 401 on every scrape of /actuator/prometheus. That's why the
            // mini-bank-app target showed DOWN and the JVM/Spring Boot Grafana dashboards showed
            // N/A even though the datasource itself was connected fine.
            "/actuator/prometheus",
            "/actuator/metrics",
            "/actuator/metrics/**",
            "/actuator/info"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        SessionTokenAuthenticationFilter sessionTokenAuthenticationFilter =
                new SessionTokenAuthenticationFilter(sessionRepository);
        MdcCorrelationFilter mdcCorrelationFilter = new MdcCorrelationFilter();

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(eh -> eh.authenticationEntryPoint(authenticationEntryPoint))
                .addFilterBefore(sessionTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(mdcCorrelationFilter, SessionTokenAuthenticationFilter.class);

        return http.build();
    }
}
