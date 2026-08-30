package com.ksh.security;

import com.ksh.entities.User;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * OidcUser decorator that maps the KSH user's role into Spring Security authorities.
 * Delegates attribute/id-token/user-info calls to the underlying Google OidcUser.
 */
public class CustomOidcUserPrincipal extends KshUserDetails implements OidcUser {

    private final OidcUser delegate;

    public CustomOidcUserPrincipal(OidcUser delegate, User user) {
        this(delegate, user, List.of());
    }

    public CustomOidcUserPrincipal(OidcUser delegate, User user,
                                   Collection<String> featureKeys) {
        this(delegate, user, featureKeys, null);
    }

    public CustomOidcUserPrincipal(OidcUser delegate, User user,
                                   Collection<String> featureKeys,
                                   LocalDateTime permissionAuthoritiesValidUntil) {
        super(user, featureKeys, permissionAuthoritiesValidUntil);
        this.delegate = delegate;
    }

    // â”€â”€ OidcUser delegation â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Override public Map<String, Object> getClaims() { return delegate.getClaims(); }
    @Override public OidcUserInfo getUserInfo() { return delegate.getUserInfo(); }
    @Override public OidcIdToken getIdToken() { return delegate.getIdToken(); }
    @Override public Map<String, Object> getAttributes() { return delegate.getAttributes(); }

    /**
     * Returns the user's email address rather than the Google subject id.
     *
     * <p>{@code Authentication.getName()} is the cross-cutting identifier used
     * by Spring Security audit code, the home controller, etc. The default
     * OIDC implementation returns the {@code sub} claim (a numeric Google
     * subject), which is opaque to the rest of the application. The form-login
     * principal ({@code KshUserDetails}) returns the email here, so we mirror
     * that for OIDC to keep callers like {@code HomeController.home()} simple.
     *
     * @return the user's email address
     */
    @Override public String getName() { return getUsername(); }
}
