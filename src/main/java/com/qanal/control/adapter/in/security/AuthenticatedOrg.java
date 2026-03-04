package com.qanal.control.adapter.in.security;

import com.qanal.control.domain.model.Organization;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security principal backed by an {@link Organization}.
 */
public record AuthenticatedOrg(Organization organization) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + organization.getPlan().name()));
    }

    @Override public String getPassword()              { return null; }
    @Override public String getUsername()              { return organization.getId(); }
    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return true; }
}
