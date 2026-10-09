package com.market.spacecatmarket.api.error;

import com.market.spacecatmarket.application.ProductNotFoundException;
import com.market.spacecatmarket.application.catalog.CatalogProductNotFoundException;
import com.market.spacecatmarket.application.catalog.CatalogUnavailableException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final String ERROR_BASE = "https://cosmo-cats.market/errors/";

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ValidationError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ValidationError(error.getField(), error.getDefaultMessage())).toList();
        return validation(errors, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (exception.isForReturnValue()) {
            return super.handleHandlerMethodValidationException(exception, headers, status, request);
        }
        List<ValidationError> errors = new ArrayList<>();
        exception.getParameterValidationResults().forEach(result -> {
            String field = Objects.requireNonNullElse(result.getMethodParameter().getParameterName(), "parameter");
            result.getResolvableErrors().forEach(error ->
                    errors.add(new ValidationError(field, error.getDefaultMessage())));
        });
        return validation(errors, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String field = exception instanceof MethodArgumentTypeMismatchException mismatch
                ? mismatch.getName() : Objects.requireNonNullElse(exception.getPropertyName(), "parameter");
        String message = exception.getRequiredType() == UUID.class
                ? "must be a valid UUID" : "must be a valid integer";
        return validation(List.of(new ValidationError(field, message)), request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String field = "body";
        String message = "must contain a valid JSON object with all required fields";
        if (exception.getMostSpecificCause() instanceof JacksonException jsonException
                && !jsonException.getPath().isEmpty()) {
            field = jsonException.getPath().stream().map(JacksonException.Reference::getPropertyName)
                    .filter(Objects::nonNull).collect(Collectors.joining("."));
            message = "must have the documented JSON type and value";
            if (field.equals("category")) {
                message = "must be one of: TOYS, FOOD, ACCESSORIES";
            }
        }
        return validation(List.of(new ValidationError(field, message)), request);
    }

    @ExceptionHandler({ProductNotFoundException.class, CatalogProductNotFoundException.class})
    public ResponseEntity<Object> handleNotFound(RuntimeException exception, WebRequest request) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Product not found", exception.getMessage(), request);
    }

    @ExceptionHandler(CatalogUnavailableException.class)
    public ResponseEntity<Object> handleCatalogUnavailable(CatalogUnavailableException exception, WebRequest request) {
        logger.warn("External catalog request failed", exception);
        return problem(HttpStatus.BAD_GATEWAY, "external-catalog", "External catalog unavailable",
                exception.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception exception, WebRequest request) {
        logger.error("Unexpected request failure", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal", "Internal server error",
                "An unexpected error occurred. Please try again later.", request);
    }

    private ResponseEntity<Object> validation(List<ValidationError> errors, WebRequest request) {
        List<ValidationError> sortedErrors = errors.stream().distinct()
                .sorted(Comparator.comparing(ValidationError::field).thenComparing(ValidationError::message)).toList();
        String detail = sortedErrors.stream()
                .map(error -> "Field '" + error.field() + "' " + error.message() + ".")
                .collect(Collectors.joining(" "));
        ProblemDetail body = newProblem(HttpStatus.BAD_REQUEST, "validation", "Validation failed", detail, request);
        body.setProperty("errors", sortedErrors);
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }

    private ResponseEntity<Object> problem(HttpStatus status, String type, String title,
                                            String detail, WebRequest request) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(newProblem(status, type, title, detail, request));
    }

    private ProblemDetail newProblem(HttpStatus status, String type, String title, String detail, WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setType(URI.create(ERROR_BASE + type));
        body.setTitle(title);
        body.setInstance(URI.create(((ServletWebRequest) request).getRequest().getRequestURI()));
        return body;
    }
}
