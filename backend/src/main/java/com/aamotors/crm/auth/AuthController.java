package com.aamotors.crm.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Single ADMIN authentication")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Issue the CSRF cookie")
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf() {
        // CookieCsrfTokenRepository writes the XSRF-TOKEN cookie on any response once the token is loaded.
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Login with username and password")
    @PostMapping("/login")
    public AuthDtos.AdminProfile login(@Valid @RequestBody AuthDtos.LoginRequest request,
                                       HttpServletRequest http, HttpServletResponse response) {
        return AuthDtos.AdminProfile.from(authService.login(request, http, response));
    }

    @Operation(summary = "Logout and invalidate the session")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        authService.logout(http);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Current administrator profile")
    @GetMapping("/me")
    public AuthDtos.AdminProfile me(Authentication authentication) {
        return AuthDtos.AdminProfile.from(authService.currentUser(authentication));
    }

    @Operation(summary = "Store the administrator's UI language preference")
    @PutMapping("/me/language")
    public AuthDtos.AdminProfile language(Authentication authentication, @Valid @RequestBody AuthDtos.LanguageRequest request) {
        return AuthDtos.AdminProfile.from(authService.updateLanguage(authentication, request.language()));
    }

    @Operation(summary = "Change the administrator password")
    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(Authentication authentication, @Valid @RequestBody AuthDtos.ChangePasswordRequest request) {
        authService.changePassword(authentication, request);
        return ResponseEntity.noContent().build();
    }
}
