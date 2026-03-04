package com.qanal.control.infrastructure.config;

import com.qanal.control.adapter.in.security.ApiKeyAuthFilter;
import com.qanal.control.adapter.in.security.RateLimitFilter;
import com.qanal.control.application.port.out.ApiKeyStore;
import com.qanal.control.application.port.out.RateLimitPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public RateLimitFilter rateLimitFilter(RateLimitPort rateLimitPort) {
        return new RateLimitFilter(rateLimitPort);
    }

    @Bean
    public ApiKeyAuthFilter apiKeyAuthFilter(ApiKeyStore apiKeyStore) {
        return new ApiKeyAuthFilter(apiKeyStore);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                            RateLimitFilter rateLimitFilter,
                                            ApiKeyAuthFilter apiKeyAuthFilter) throws Exception {
        return http
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll()
                )
                .addFilterBefore(rateLimitFilter, ApiKeyAuthFilter.class)
                .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
