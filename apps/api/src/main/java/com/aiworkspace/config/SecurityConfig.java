package com.aiworkspace.config;

import com.aiworkspace.security.ApiRateLimitFilter;
import com.aiworkspace.security.ApiSecurityErrorHandler;
import com.aiworkspace.security.SecurityAuditFilter;
import com.aiworkspace.security.UserQuotaFilter;
import com.aiworkspace.security.CorrelationIdFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ApiSecurityErrorHandler securityErrorHandler,
            ApiRateLimitFilter rateLimitFilter, SecurityAuditFilter auditFilter,
            UserQuotaFilter quotaFilter, CorrelationIdFilter correlationFilter,
            SecurityProperties properties) throws Exception {
        if (properties.requireHttps()) {
            http.redirectToHttps(https -> https.requestMatchers(request -> true));
        }
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/assets/**",
                                "/swagger-ui.html",
                                "/openapi.yaml",
                                "/api/v1/auth/register"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler)
                )
                .httpBasic(basic -> basic.authenticationEntryPoint(securityErrorHandler))
                .addFilterBefore(rateLimitFilter, BasicAuthenticationFilter.class)
                .addFilterBefore(correlationFilter, ApiRateLimitFilter.class)
                .addFilterAfter(quotaFilter, BasicAuthenticationFilter.class)
                .addFilterAfter(auditFilter, BasicAuthenticationFilter.class)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder(SecurityProperties properties) {
        return new BCryptPasswordEncoder(properties.bcryptStrength());
    }

    @Bean
    FilterRegistrationBean<ApiRateLimitFilter> rateLimitFilterRegistration(ApiRateLimitFilter filter) {
        FilterRegistrationBean<ApiRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<SecurityAuditFilter> auditFilterRegistration(SecurityAuditFilter filter) {
        FilterRegistrationBean<SecurityAuditFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<UserQuotaFilter> quotaFilterRegistration(UserQuotaFilter filter) {
        FilterRegistrationBean<UserQuotaFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<CorrelationIdFilter> correlationFilterRegistration(CorrelationIdFilter filter) {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
