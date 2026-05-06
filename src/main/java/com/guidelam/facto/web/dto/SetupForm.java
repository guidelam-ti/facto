package com.guidelam.facto.web.dto;

import jakarta.validation.constraints.NotBlank;

public record SetupForm(
        @NotBlank(message = "Le client_id est requis")
        String clientId,

        @NotBlank(message = "Le client_secret est requis")
        String clientSecret
) {
}
