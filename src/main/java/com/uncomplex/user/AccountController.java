package com.uncomplex.user;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The authenticated user's own account. */
@RestController
@RequestMapping("/api/me")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * Erases the account and everything referencing it. The caller can only ever delete
     * themselves: the id comes from the verified JWT subject, never from the request.
     */
    @DeleteMapping
    public ResponseEntity<Void> deleteAccount(@AuthenticationPrincipal Jwt jwt) {
        accountService.deleteAccount(Long.valueOf(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }
}
