package com.mindahun.auth.service;

import com.mindahun.auth.models.User;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@Setter
public class CustomUserDetails implements UserDetails, OAuth2User, OidcUser {

    private Map<String, Object> attributes;
    private final User user;
    private OidcIdToken idToken;
    private OidcUserInfo userInfo;

    public CustomUserDetails(User user) {
        this.user = user;
    }

    public static CustomUserDetails from(User user, Map<String, Object> attributes) {
        CustomUserDetails details = new CustomUserDetails(user);
        details.setAttributes(attributes);
        return details;
    }

    public static CustomUserDetails from(User user, Map<String, Object> attributes, OidcIdToken idToken, OidcUserInfo userInfo) {
        CustomUserDetails details = new CustomUserDetails(user);
        details.setAttributes(attributes);
        details.setIdToken(idToken);
        details.setUserInfo(userInfo);
        return details;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return user.getRoles() !=null ? user.getRoles().stream().map(role->new SimpleGrantedAuthority(role.getName())).collect(Collectors.toSet()) : Collections.emptyList() ;
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isEnabled() {
        return user.isEnabled();
    }

    @Override
    public String getName() {
        return user.getEmail();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }


    @Override
    public Map<String, Object> getClaims() {
        return this.getAttributes();
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return this.userInfo;
    }

    @Override
    public OidcIdToken getIdToken() {
        return this.idToken;
    }
}
