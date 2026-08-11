package com.ksh.config;

import com.ksh.security.Roles;
import com.ksh.security.CustomOidcUserService;
import com.ksh.security.LoginAttemptThrottle;
import com.ksh.security.LoginThrottleFilter;
import com.ksh.security.PermissionExpiryFilter;
import com.ksh.security.RoleAwareAuthenticationSuccessHandler;
import com.ksh.features.admin.permissions.service.PermissionResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.AuthenticatedPrincipalOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Security configuration for the KSH application.
 *
 * <ul>
 *   <li>Form login — always active.</li>
 *   <li>Google OAuth2 — always wired into the filter chain. Activation is
 *       driven at runtime by {@code DbClientRegistrationRepository}, which
 *       returns {@code null} when no client id has been saved in
 *       {@code system_settings}. Spring Security then responds with HTTP 404
 *       to {@code /oauth2/authorization/google} and the login button is
 *       hidden in the UI via {@code @oauthSettingsService.isGoogleEnabled()}.</li>
 *   <li>Public endpoints: static assets, login, forgot/reset-password, and uploaded files.</li>
 *   <li>Passwords hashed with BCrypt.</li>
 *   <li>CSRF protection is enabled by default — Thymeleaf forms inject the token automatically.</li>
 * </ul>
 *
 * <p>Because a custom {@link ClientRegistrationRepository} bean is provided,
 * Spring Boot's {@code OAuth2ClientAutoConfiguration} backs off — we therefore
 * also expose {@link OAuth2AuthorizedClientService} and
 * {@link OAuth2AuthorizedClientRepository} beans here as required by the
 * official Spring Security override pattern.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomOidcUserService customOidcUserService;

    public SecurityConfig(CustomOidcUserService customOidcUserService) {
        this.customOidcUserService = customOidcUserService;
    }

    /**
     * Provides a {@link BCryptPasswordEncoder} as the application-wide {@link PasswordEncoder}.
     *
     * @return a BCrypt password encoder bean
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Provides an {@link AuthenticationFailureHandler} for OAuth2 login failures.
     *
     * <p>Redirects the user to {@code /login?error=oauth_unregistered} when an
     * OAuth2 authentication attempt fails (e.g. the Google account is not yet
     * registered in KSH, or Google sign-in is currently disabled in the admin
     * panel).</p>
     *
     * @return an {@link AuthenticationFailureHandler} that redirects to the login error page
     */
    @Bean
    public AuthenticationFailureHandler oauthFailureHandler() {
        return (request, response, exception) ->
                response.sendRedirect("/login?error=oauth_unregistered");
    }

    @Bean
    public AuthenticationFailureHandler formFailureHandler(
            LoginAttemptThrottle throttle) {
        SimpleUrlAuthenticationFailureHandler delegate =
                new SimpleUrlAuthenticationFailureHandler("/login?error");
        return (request, response, exception) -> {
            throttle.recordFailure(
                    request.getParameter("username"), request.getRemoteAddr());
            delegate.onAuthenticationFailure(request, response, exception);
        };
    }

    @Bean
    public AuthenticationSuccessHandler formSuccessHandler(
            LoginAttemptThrottle throttle,
            RoleAwareAuthenticationSuccessHandler roleAwareSuccessHandler) {
        return (request, response, authentication) -> {
            throttle.recordSuccess(request.getParameter("username"));
            roleAwareSuccessHandler.onAuthenticationSuccess(request, response, authentication);
        };
    }

    @Bean
    public RequestCache requestCache() {
        HttpSessionRequestCache cache = new HttpSessionRequestCache();
        // The shared header polls these endpoints in the background. Saving one
        // as the post-login destination turns a normal sign-in into a redirect
        // to a JSON response (for example /my/notifications/unread-count).
        cache.setRequestMatcher(request -> {
            String path = request.getRequestURI();
            boolean ajax = "XMLHttpRequest".equalsIgnoreCase(
                    request.getHeader("X-Requested-With"));
            return "GET".equalsIgnoreCase(request.getMethod())
                    && !ajax
                    && !path.endsWith("/unread-count")
                    && !path.endsWith("/recent");
        });
        return cache;
    }

    @Bean
    public RoleAwareAuthenticationSuccessHandler roleAwareSuccessHandler(
            RequestCache requestCache) {
        return new RoleAwareAuthenticationSuccessHandler(requestCache);
    }

    /**
     * In-memory store for {@link org.springframework.security.oauth2.client.OAuth2AuthorizedClient}
     * instances. Required when a custom {@link ClientRegistrationRepository}
     * disables Spring Boot's OAuth2 client auto-configuration.
     *
     * @param clientRegistrationRepository the registration repository (DB-backed)
     * @return an in-memory authorized-client service
     */
    @Bean
    public OAuth2AuthorizedClientService authorizedClientService(
            ClientRegistrationRepository clientRegistrationRepository) {
        return new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);
    }

    /**
     * Per-request repository that retrieves authorized clients via the
     * provided service. Required alongside the custom
     * {@link ClientRegistrationRepository}.
     *
     * @param authorizedClientService the in-memory client service
     * @return a request-scoped authorized-client repository
     */
    @Bean
    public OAuth2AuthorizedClientRepository authorizedClientRepository(
            OAuth2AuthorizedClientService authorizedClientService) {
        return new AuthenticatedPrincipalOAuth2AuthorizedClientRepository(authorizedClientService);
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    /**
     * Configures the main {@link SecurityFilterChain} for the application.
     *
     * <p>Authorization rules:</p>
     * <ul>
     *   <li>Static resources plus the controller-backed avatar/exam upload namespaces are public.</li>
     *   <li>{@code /login}, {@code /forgot-password}, and {@code /reset-password} are public.</li>
     *   <li>{@code /lecturer/**} requires {@code LECTURER}, {@code LEADER}, or {@code ADMIN} role.</li>
     *   <li>{@code /admin/**} requires the {@code ADMIN} role.</li>
     *   <li>All other requests require an authenticated user.</li>
     * </ul>
     *
     * <p>Google OAuth2 login is always wired in. Whether it is actually
     * available is decided per-request by {@code DbClientRegistrationRepository}.</p>
     *
     * @param http the {@link HttpSecurity} to configure
     * @return the built {@link SecurityFilterChain}
     * @throws Exception if an error occurs while building the filter chain
     */
    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            LoginAttemptThrottle loginAttemptThrottle,
            @Qualifier("formFailureHandler")
            AuthenticationFailureHandler formFailureHandler,
            @Qualifier("formSuccessHandler")
            AuthenticationSuccessHandler formSuccessHandler,
            RoleAwareAuthenticationSuccessHandler roleAwareSuccessHandler,
            PermissionResolver permissionResolver,
            RequestCache requestCache) throws Exception {
        http
                // Allow same-origin framing so the in-app PDF.js / docx
                // viewer iframes render (default is DENY). See decision 0010.
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/fonts/**", "/favicon.ico").permitAll()
                        .requestMatchers("/webjars/**").permitAll()
                        // Raw upload routing is fail-closed. Only the
                        // controller-backed public image namespaces are allowed;
                        // Practice material must use its authorized controller.
                        .requestMatchers(
                                "/uploads/avatars/**",
                                "/uploads/exams/**",
                                "/uploads/flashcards/**"
                        ).permitAll()
                        .requestMatchers("/uploads/**").denyAll()
                        .requestMatchers("/login", "/forgot-password", "/reset-password").permitAll()
                        .requestMatchers("/public/view/**").permitAll()
                        .requestMatchers("/s/**").permitAll()
                        .requestMatchers("/practice/manage/**").hasRole(Roles.LECTURER)
                        .requestMatchers("/practice/preferences/**")
                        .hasAnyRole(Roles.STUDENT, Roles.LECTURER)
                        .requestMatchers("/practice/progress", "/practice/profile").hasRole(Roles.STUDENT)
                        .requestMatchers("/lecturer/**").hasAnyRole(Roles.LECTURER, Roles.LEADER, Roles.ADMIN)
                        .requestMatchers("/leader/**").hasRole(Roles.LEADER)
                        .requestMatchers("/admin/**").hasRole(Roles.ADMIN)
                        // WebSocket STOMP handshake rides the HTTP session; require auth.
                        .requestMatchers("/ws/**").authenticated()
                        .requestMatchers("/my/**").authenticated()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        // Resume saved requests only when they fit the newly authenticated role. A stale
                        // lecturer/admin URL from the previous account is
                        // discarded by the role-aware success handler.
                        .successHandler(formSuccessHandler)
                        .failureHandler(formFailureHandler)
                        .permitAll()
                )
                .requestCache(cache -> cache.requestCache(requestCache))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                )
                .oauth2Login(oauth -> oauth
                        .loginPage("/login")
                        .userInfoEndpoint(ui -> ui.oidcUserService(customOidcUserService))
                        .failureHandler(oauthFailureHandler())
                        // Keep form login and Google sign-in on the same
                        // role-aware saved-request policy.
                        .successHandler(roleAwareSuccessHandler)
                )
                .sessionManagement(session -> session
                        .sessionConcurrency(concurrency -> concurrency
                                .maximumSessions(-1)
                                .sessionRegistry(sessionRegistry())))
                .addFilterBefore(
                        new LoginThrottleFilter(loginAttemptThrottle),
                        UsernamePasswordAuthenticationFilter.class)
                // PERM_* authorities are a login snapshot. Retire the session at
                // the nearest override expiry before authorization can reuse it.
                .addFilterBefore(
                        new PermissionExpiryFilter(permissionResolver),
                        AuthorizationFilter.class)
                // Eagerly materialize CSRF token before the view starts rendering.
                // Without this, the deferred CSRF lookup happens deep inside Thymeleaf's
                // form rendering, after the response buffer has already been flushed —
                // which makes HttpServletRequest.getSession(true) throw
                // "Cannot create a session after the response has been committed".
                .addFilterAfter(new CsrfTokenEagerFilter(), CsrfFilter.class);

        return http.build();
    }

    /**
     * Filter that resolves the deferred CSRF token at the beginning of the request,
     * forcing Spring Security to create the HttpSession (if needed) and persist the
     * token before any view rendering begins.
     *
     * <p>Spring Security 6 defers CSRF token loading until the first call to
     * {@code CsrfToken.getToken()}/{@code getParameterName()}. In our setup the first
     * such call happens inside Thymeleaf when it renders a form. By that time the
     * response buffer (default 8KB in Tomcat) may already have been flushed for
     * large pages, which makes {@code request.getSession(true)} throw
     * {@code IllegalStateException: Cannot create a session after the response has
     * been committed}.</p>
     *
     * <p>Resolving the token early here keeps the session creation safely before
     * any output is written.</p>
     */
    private static final class CsrfTokenEagerFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                // Touch the token so the underlying repository persists it (and
                // creates the HttpSession if needed) before any view rendering
                // begins and the response buffer might get flushed.
                token.getToken();
            }
            chain.doFilter(request, response);
        }
    }
}
