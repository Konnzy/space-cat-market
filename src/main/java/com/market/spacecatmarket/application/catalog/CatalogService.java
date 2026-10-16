package com.market.spacecatmarket.application.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.market.spacecatmarket.application.catalog.dto.CatalogProductDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.resilience.retry.MethodRetryPredicate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

@Service
public class CatalogService {
    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);
    private static final int MAX_RETRIES = 2;
    private static final long RETRY_DELAY_MS = 200;
    private static final String INVALID_RESPONSE_MESSAGE = "External catalog returned an invalid product.";
    private final RestClient restClient;

    public CatalogService(@Qualifier("catalogRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Retryable(predicate = CatalogRetryPredicate.class, maxRetries = MAX_RETRIES,
            delay = RETRY_DELAY_MS, multiplier = 2)
    public CatalogProductDto findById(int externalId) {
        long started = System.nanoTime();
        log.debug("Requesting external catalog product externalId={}", externalId);
        try {
            DummyJsonProduct response = restClient.get().uri("/products/{id}", externalId)
                    .retrieve().body(DummyJsonProduct.class);
            validateResponse(response, externalId);
            log.debug("Retrieved external catalog product: externalId={}, elapsedMs={}",
                    externalId, (System.nanoTime() - started) / 1_000_000);
            return new CatalogProductDto(response.id(), response.title(), response.description(), response.price());
        } catch (HttpClientErrorException.NotFound exception) {
            log.debug("External catalog product not found: externalId={}, elapsedMs={}",
                    externalId, (System.nanoTime() - started) / 1_000_000);
            throw new CatalogProductNotFoundException(externalId);
        } catch (RestClientException exception) {
            log.warn("External catalog request failed: externalId={}, cause={}, elapsedMs={}",
                    externalId, exception.getClass().getSimpleName(), (System.nanoTime() - started) / 1_000_000);
            throw new CatalogUnavailableException("External catalog is temporarily unavailable.", exception);
        }
    }

    private void validateResponse(DummyJsonProduct response, int expectedId) {
        if (response == null || response.id() == null || response.id() != expectedId) {
            log.warn("Invalid external catalog response: externalId={}, invalidField=id", expectedId);
            throw new CatalogUnavailableException(INVALID_RESPONSE_MESSAGE);
        }
        if (response.title() == null || response.title().isBlank() || response.description() == null) {
            log.warn("Invalid external catalog response: externalId={}, invalidField=title/description", expectedId);
            throw new CatalogUnavailableException(INVALID_RESPONSE_MESSAGE);
        }
        if (response.price() == null || response.price().signum() < 0) {
            log.warn("Invalid external catalog response: externalId={}, invalidField=price", expectedId);
            throw new CatalogUnavailableException(INVALID_RESPONSE_MESSAGE);
        }
    }

    public static class CatalogRetryPredicate implements MethodRetryPredicate {
        @Override
        public boolean shouldRetry(Method method, Throwable failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof ResourceAccessException) {
                    return true;
                }
                if (cause instanceof HttpServerErrorException serverError) {
                    return switch (serverError.getStatusCode().value()) {
                        case 500, 502, 503, 504 -> true;
                        default -> false;
                    };
                }
            }
            return false;
        }

        @Override
        public Predicate<Throwable> forMethod(Method method) {
            AtomicInteger failedAttempts = new AtomicInteger();
            return failure -> {
                if (!shouldRetry(method, failure)) {
                    return false;
                }
                int failedAttempt = failedAttempts.incrementAndGet();
                if (failedAttempt <= MAX_RETRIES) {
                    log.warn("Retrying external catalog request: nextAttempt={}, maxAttempts={}, delayMs={}",
                            failedAttempt + 1, MAX_RETRIES + 1, RETRY_DELAY_MS * (1L << (failedAttempt - 1)));
                }
                return true;
            };
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DummyJsonProduct(Integer id, String title, String description, BigDecimal price) {
    }
}
