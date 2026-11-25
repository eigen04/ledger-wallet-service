package com.eigen04.ledger.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAccountRequest {

    @NotBlank(message = "Account holder name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String accountHolderName;

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    private String email;

    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
    private String currency = "INR";
}
