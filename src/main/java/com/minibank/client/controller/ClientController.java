package com.minibank.client.controller;

import com.minibank.client.dto.ConfirmRegistrationRequest;
import com.minibank.client.dto.ConfirmRegistrationResponse;
import com.minibank.client.dto.RegisterRequest;
import com.minibank.client.dto.RegisterResponse;
import com.minibank.client.service.ClientRegistrationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/clients")
public class ClientController {

    private final ClientRegistrationService registrationService;

    public ClientController(ClientRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return registrationService.register(request);
    }

    @PostMapping("/register/{registrationId}/confirm")
    public ConfirmRegistrationResponse confirm(@PathVariable UUID registrationId,
                                                @Valid @RequestBody ConfirmRegistrationRequest request) {
        return registrationService.confirmRegistration(registrationId, request);
    }
}
