package com.minibank.auth.controller;

import com.minibank.auth.dto.LoginConfirmRequest;
import com.minibank.auth.dto.LoginConfirmResponse;
import com.minibank.auth.dto.LoginInitiateRequest;
import com.minibank.auth.dto.LoginInitiateResponse;
import com.minibank.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login/initiate")
    public LoginInitiateResponse initiate(@Valid @RequestBody LoginInitiateRequest request) {
        return authService.initiate(request);
    }

    @PostMapping("/login/confirm")
    public LoginConfirmResponse confirm(@Valid @RequestBody LoginConfirmRequest request) {
        return authService.confirm(request);
    }
}
