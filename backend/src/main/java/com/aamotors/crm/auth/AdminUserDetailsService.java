package com.aamotors.crm.auth;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminUserRepository users;

    public AdminUserDetailsService(AdminUserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AdminUser u = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return User.withUsername(u.getUsername())
                .password(u.getPasswordHash())
                .roles(u.getRole())
                .disabled(!u.isEnabled())
                .build();
    }
}
