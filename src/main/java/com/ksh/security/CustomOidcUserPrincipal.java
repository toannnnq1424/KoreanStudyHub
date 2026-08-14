package com.ksh.security;

import com.ksh.entities.User;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Map;

/**
 * OIDC principal which also is a {@link KshUserDetails}, so controllers that
 * accept the local form-login principal work for Google-authenticated users too.
 * Delegates OIDC attribute/id-token/user-info calls to the underlying Google user.
 */
public class CustomOidcUserPrincipal extends KshUserDetails implements OidcUser {

    private final OidcUser delegate;

    public CustomOidcUserPrincipal(OidcUser delegate, User user) {
        super(user);
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
