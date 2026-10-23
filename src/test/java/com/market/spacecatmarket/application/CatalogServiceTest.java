package com.market.spacecatmarket.application;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.market.spacecatmarket.application.catalog.CatalogProductNotFoundException;
import com.market.spacecatmarket.application.catalog.CatalogService;
import com.market.spacecatmarket.application.catalog.CatalogUnavailableException;
import com.market.spacecatmarket.application.catalog.dto.CatalogProductDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@WireMockTest
class CatalogServiceTest {

    private AnnotationConfigApplicationContext context;
    private CatalogService service;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wireMock) {
        context = new AnnotationConfigApplicationContext();
        context.registerBean("catalogRestClient", RestClient.class,
                () -> RestClient.builder().baseUrl(wireMock.getHttpBaseUrl()).build());
        context.register(CatalogService.class, RetryTestConfiguration.class);
        context.refresh();

        service = context.getBean(CatalogService.class);
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void findById_whenResponseIsValid_returnsCatalogProduct() {
        stubFor(get(urlEqualTo("/products/1"))
                .willReturn(okJson("""
                        {
                          "id": 1,
                          "title": "Cosmic cat toy",
                          "description": "A toy for a cat",
                          "price": 12.50
                        }
                        """)));

        CatalogProductDto actual = service.findById(1);

        CatalogProductDto expected = new CatalogProductDto(
                1, "Cosmic cat toy", "A toy for a cat", new BigDecimal("12.50"));
        assertEquals(expected, actual);
        verify(1, getRequestedFor(urlEqualTo("/products/1")));
    }

    @Test
    void findById_whenProductDoesNotExist_throwsCatalogProductNotFoundExceptionWithoutRetry() {
        stubFor(get(urlEqualTo("/products/404")).willReturn(notFound()));

        CatalogProductNotFoundException exception = assertThrows(
                CatalogProductNotFoundException.class, () -> service.findById(404));

        assertEquals("External catalog product '404' was not found.", exception.getMessage());

        verify(1, getRequestedFor(urlEqualTo("/products/404")));
    }

    @Test
    void findById_whenResponseBodyIsEmpty_throwsCatalogUnavailableException() {
        stubFor(get(urlEqualTo("/products/1")).willReturn(okJson("")));

        CatalogUnavailableException exception = assertThrows(
                CatalogUnavailableException.class, () -> service.findById(1));

        assertEquals("External catalog returned an invalid product.", exception.getMessage());
        verify(1, getRequestedFor(urlEqualTo("/products/1")));
    }

    @Test
    void findById_whenResponseIdIsMissing_throwsCatalogUnavailableException() {
        stubFor(get(urlEqualTo("/products/2")).willReturn(okJson("""
                {"title":"title","description":"description","price":12.50}
                """)));

        CatalogUnavailableException exception = assertThrows(
                CatalogUnavailableException.class, () -> service.findById(2));

        assertEquals("External catalog returned an invalid product.", exception.getMessage());
        verify(1, getRequestedFor(urlEqualTo("/products/2")));
    }

    @Test
    void findById_whenResponseIdDoesNotMatchRequestedId_throwsCatalogUnavailableException() {
        stubFor(get(urlEqualTo("/products/3")).willReturn(okJson("""
                {"id":4,"title":"title","description":"description","price":12.50}
                """)));

        CatalogUnavailableException exception = assertThrows(
                CatalogUnavailableException.class, () -> service.findById(3));

        assertEquals("External catalog returned an invalid product.", exception.getMessage());
        verify(1, getRequestedFor(urlEqualTo("/products/3")));
    }

    @Test
    void findById_whenTitleIsMissing_throwsCatalogUnavailableException() {
        stubFor(get(urlEqualTo("/products/4")).willReturn(okJson("""
                {"id":4,"description":"description","price":12.50}
                """)));

        CatalogUnavailableException exception = assertThrows(
                CatalogUnavailableException.class, () -> service.findById(4));

        assertEquals("External catalog returned an invalid product.", exception.getMessage());
        verify(1, getRequestedFor(urlEqualTo("/products/4")));
    }

    @Test
    void findById_whenTitleIsBlank_throwsCatalogUnavailableException() {
        stubFor(get(urlEqualTo("/products/5")).willReturn(okJson("""
                {"id":5,"title":"  ","description":"description","price":12.50}
                """)));

        CatalogUnavailableException exception = assertThrows(
                CatalogUnavailableException.class, () -> service.findById(5));

        assertEquals("External catalog returned an invalid product.", exception.getMessage());
        verify(1, getRequestedFor(urlEqualTo("/products/5")));
    }

    @Test
    void findById_whenDescriptionIsMissing_throwsCatalogUnavailableException() {
        stubFor(get(urlEqualTo("/products/6")).willReturn(okJson("""
                {"id":6,"title":"title","price":12.50}
                """)));

        CatalogUnavailableException exception = assertThrows(
                CatalogUnavailableException.class, () -> service.findById(6));

        assertEquals("External catalog returned an invalid product.", exception.getMessage());
        verify(1, getRequestedFor(urlEqualTo("/products/6")));
    }

    @Test
    void findById_whenPriceIsMissing_throwsCatalogUnavailableException() {
        stubFor(get(urlEqualTo("/products/7")).willReturn(okJson("""
                {"id":7,"title":"title","description":"description"}
                """)));

        CatalogUnavailableException exception = assertThrows(
                CatalogUnavailableException.class, () -> service.findById(7));

        assertEquals("External catalog returned an invalid product.", exception.getMessage());
        verify(1, getRequestedFor(urlEqualTo("/products/7")));
    }

    @Test
    void findById_whenPriceIsNegative_throwsCatalogUnavailableException() {
        stubFor(get(urlEqualTo("/products/8")).willReturn(okJson("""
                {"id":8,"title":"title","description":"description","price":-1}
                """)));

        CatalogUnavailableException exception = assertThrows(
                CatalogUnavailableException.class, () -> service.findById(8));

        assertEquals("External catalog returned an invalid product.", exception.getMessage());
        verify(1, getRequestedFor(urlEqualTo("/products/8")));
    }

    @Test
    void findById_whenPriceIsZero_returnsCatalogProduct() {
        stubFor(get(urlEqualTo("/products/9")).willReturn(okJson("""
                {"id":9,"title":"title","description":"description","price":0}
                """)));

        CatalogProductDto catalogProduct = service.findById(9);

        assertEquals(new CatalogProductDto(9, "title", "description", BigDecimal.ZERO), catalogProduct);
        verify(1, getRequestedFor(urlEqualTo("/products/9")));
    }

    @Test
    void findById_whenResponseContainsInvalidJson_throwsCatalogUnavailableException() {
        stubFor(get(urlEqualTo("/products/10")).willReturn(okJson("""
                {"id":10,"title":"title","description":"description","price":}
                """)));

        CatalogUnavailableException exception = assertThrows(
                CatalogUnavailableException.class, () -> service.findById(10));

        assertEquals("External catalog is temporarily unavailable.", exception.getMessage());
        verify(1, getRequestedFor(urlEqualTo("/products/10")));
    }

    @Test
    void findById_whenClientErrorOccurs_throwsCatalogUnavailableExceptionWithoutRetry() {
        stubFor(get(urlEqualTo("/products/11")).willReturn(aResponse().withStatus(400)));

        assertThrows(CatalogUnavailableException.class, () -> service.findById(11));

        verify(1, getRequestedFor(urlEqualTo("/products/11")));
    }

    @Test
    void findById_whenServerErrorIsNotRetryable_throwsCatalogUnavailableExceptionWithoutRetry() {
        stubFor(get(urlEqualTo("/products/12")).willReturn(aResponse().withStatus(501)));

        assertThrows(CatalogUnavailableException.class, () -> service.findById(12));

        verify(1, getRequestedFor(urlEqualTo("/products/12")));
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 504})
    void findById_whenTemporaryServerErrorOccurs_retriesAndReturnsProduct(int status) {
        stubFor(get(urlEqualTo("/products/13"))
                .inScenario("catalog recovers")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(status))
                .willSetStateTo("available"));
        stubFor(get(urlEqualTo("/products/13"))
                .inScenario("catalog recovers")
                .whenScenarioStateIs("available")
                .willReturn(okJson("""
                        {"id":13,"title":"title","description":"description","price":12.50}
                        """)));

        CatalogProductDto actual = service.findById(13);

        assertEquals(new CatalogProductDto(13, "title", "description", new BigDecimal("12.50")), actual);
        verify(2, getRequestedFor(urlEqualTo("/products/13")));
    }

    @Test
    void findById_whenRetriesAreExhausted_throwsCatalogUnavailableExceptionAfterThreeRequests() {
        stubFor(get(urlEqualTo("/products/14")).willReturn(serverError()));

        assertThrows(CatalogUnavailableException.class, () -> service.findById(14));

        verify(3, getRequestedFor(urlEqualTo("/products/14")));
    }

    @Test
    void findById_whenConnectionFails_retriesAndThrowsCatalogUnavailableException() {
        stubFor(get(urlEqualTo("/products/15")).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThrows(CatalogUnavailableException.class, () -> service.findById(15));

        verify(3, getRequestedFor(urlEqualTo("/products/15")));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableResilientMethods
    static class RetryTestConfiguration {
    }
}
