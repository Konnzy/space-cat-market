package com.market.spacecatmarket.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class CosmicWordValidator implements ConstraintValidator<CosmicWordCheck, String> {
    private static final Pattern COSMIC_WORD = Pattern.compile("\\b(star|galaxy|comet)\\b", Pattern.CASE_INSENSITIVE);

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || COSMIC_WORD.matcher(value).find();
    }
}
