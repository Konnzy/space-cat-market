package com.market.spacecatmarket.api.dto;

import com.market.spacecatmarket.api.validation.CosmicWordCheck;
import com.market.spacecatmarket.domain.Category;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(
        @NotNull @NotBlank @Size(min = 3, max = 100) @CosmicWordCheck String name,
        @NotNull @NotBlank @Size(min = 1, max = 1000) String description,
        @NotNull @DecimalMin(value = "0", inclusive = false, message = "must be greater than 0")
        @Digits(integer = 10, fraction = 2, message = "must have at most 10 integer digits and 2 decimal places")
        BigDecimal price,
        @NotNull Category category,
        @NotNull @NotBlank @Email @Size(max = 254) String sellerEmail) {
}
