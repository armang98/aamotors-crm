package com.aamotors.crm.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;

import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Forces the deferred CSRF token to be loaded on every request so the
 * XSRF-TOKEN cookie is actually written (Spring Security 6 defers token
 * generation until something reads it).
 */

public class CsrfCookieFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            token.getToken();
        }
        chain.doFilter(request, response);
    }
}
