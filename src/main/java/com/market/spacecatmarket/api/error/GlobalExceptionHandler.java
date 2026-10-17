package com.market.spacecatmarket.api.error;

import com.market.spacecatmarket.application.InvalidCursorException;
import com.market.spacecatmarket.application.ProductNotFoundException;
import com.market.spacecatmarket.application.catalog.CatalogProductNotFoundException;
import com.market.spacecatmarket.application.catalog.CatalogUnavailableException;
import com.market.spacecatmarket.domain.Category;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERROR_BASE = "https://cosmo-cats.market/errors/";

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ValidationError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ValidationError(error.getField(), error.isBindingFailure()
                        ? "has an invalid value for the documented type"
                        : Objects.requireNonNullElse(error.getDefaultMessage(), "is invalid"))).toList();
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
        ResponseEntity<Object> response = super.handleTypeMismatch(exception, headers, status, request);
        if (response == null || !(response.getBody() instanceof ProblemDetail body)) {
            return response;
        }
        String field = exception instanceof MethodArgumentTypeMismatchException mismatch
                ? mismatch.getName() : Objects.requireNonNullElse(exception.getPropertyName(), "parameter");
        body.setType(URI.create(ERROR_BASE + "validation"));
        body.setTitle("Validation failed");
        body.setProperty("errors", List.of(new ValidationError(field,
                Objects.requireNonNullElse(body.getDetail(), "has an invalid value"))));
        log.warn("Request parameter conversion failed: path={}, field={}", requestPath(request), field);
        return response;
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = exception.getBody();
        body.setType(URI.create(ERROR_BASE + "unsupported-media-type"));
        body.setDetail("Content-Type '%s' is not supported.".formatted(exception.getContentType()));
        log.warn("Unsupported request content type: path={}, contentType={}",
                requestPath(request), exception.getContentType());
        return handleExceptionInternal(exception, body, headers, status, request);
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
                message = "must be one of: " + Arrays.stream(Category.values())
                        .map(Category::name).collect(Collectors.joining(", "));
            }
        }
        return validation(List.of(new ValidationError(field, message)), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException exception, WebRequest request) {
        List<ValidationError> errors = exception.getConstraintViolations().stream().map(violation -> {
            String path = violation.getPropertyPath().toString();
            String field = path.substring(path.lastIndexOf('.') + 1);
            return new ValidationError(field, violation.getMessage());
        }).toList();
        return validation(errors, request);
    }

    @ExceptionHandler({ProductNotFoundException.class, CatalogProductNotFoundException.class})
    public ResponseEntity<Object> handleNotFound(RuntimeException exception, WebRequest request) {
        log.info("Resource not found: path={}, detail={}", requestPath(request), exception.getMessage());
        return problem(HttpStatus.NOT_FOUND, "not-found", "Product not found", exception.getMessage(), request);
    }

    @ExceptionHandler(CatalogUnavailableException.class)
    public ResponseEntity<Object> handleCatalogUnavailable(CatalogUnavailableException exception, WebRequest request) {
        log.error("External catalog request failed: path={}", requestPath(request), exception);
        return problem(HttpStatus.BAD_GATEWAY, "external-catalog", "External catalog unavailable",
                exception.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception exception, WebRequest request) {
        log.error("Unexpected request failure: path={}", requestPath(request), exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal", "Internal server error",
                "An unexpected error occurred. Please try again later.", request);
    }

    @ExceptionHandler(InvalidCursorException.class)
    public ResponseEntity<Object> handleInvalidCursor(InvalidCursorException exception, WebRequest request) {
        return validation(List.of(new ValidationError("cursor", exception.getMessage())), request);
    }

    private ResponseEntity<Object> validation(List<ValidationError> errors, WebRequest request) {
        List<ValidationError> sortedErrors = errors.stream().distinct()
                .sorted(Comparator.comparing(ValidationError::field).thenComparing(ValidationError::message)).toList();
        log.warn("Request validation failed: path={}, errors={}", requestPath(request), sortedErrors);
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
        body.setInstance(URI.create(requestPath(request)));
        return body;
    }

    private String requestPath(WebRequest request) {
        return ((ServletWebRequest) request).getRequest().getRequestURI();
    }
}
