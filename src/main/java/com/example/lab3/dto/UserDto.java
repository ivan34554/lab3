package com.example.lab3.dto;

import java.time.Instant;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class UserDto {
    private UserDto() {}

    public record CreateRequest(
            @NotBlank @Size(max = 50) String username,
            @NotBlank @Email @Size(max = 100) String email) {}

    public record UpdateRequest(
            @Size(min = 1, max = 50) String username,
            @Email @Size(max = 100) String email) {}

    public record Response(Long id, String username, String email, Instant createdAt) {}
}
