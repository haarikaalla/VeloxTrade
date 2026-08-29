package com.veloxtrade.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 320) String email,
        @NotBlank @Size(max = 100) String password) {
}
