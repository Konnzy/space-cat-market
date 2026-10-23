package com.market.spacecatmarket.api;

import com.market.spacecatmarket.api.error.GlobalExceptionHandler;
import com.market.spacecatmarket.application.InvalidCursorException;
import com.market.spacecatmarket.application.ProductNotFoundException;
import com.market.spacecatmarket.application.ProductPage;
import com.market.spacecatmarket.application.ProductService;
import com.market.spacecatmarket.application.catalog.CatalogProductNotFoundException;
import com.market.spacecatmarket.application.catalog.CatalogService;
import com.market.spacecatmarket.application.catalog.CatalogUnavailableException;
import com.market.spacecatmarket.application.catalog.dto.CatalogProductDto;
import com.market.spacecatmarket.application.dto.PaginationRequest;
import com.market.spacecatmarket.domain.Category;
import com.market.spacecatmarket.domain.Product;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Справжні контролер, мапер і валідація; підміняємо лише сервіси.
@WebMvcTest(ProductController.class)
@Import({ProductMapperImpl.class, GlobalExceptionHandler.class})
class ProductControllerTest {
    private static final String BASE = "/api/v1/products";
    private static final String ERROR_BASE = "https://cosmo-cats.market/errors/";
    private static final UUID ID = new UUID(0, 1);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private CatalogService catalogService;

    @Test
    void create_whenRequestIsValid_returnsCreatedProductAndLocation() throws Exception {
        Product product = sampleProduct();
        when(productService.create(any(Product.class))).thenReturn(product);

        mockMvc.perform(post(BASE).contentType(APPLICATION_JSON).content(validJson()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", BASE + "/" + ID))
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().json(objectMapper.writeValueAsString(product)));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productService).create(captor.capture());
        Product submitted = captor.getValue();
        assertNotNull(submitted.id());
        assertEquals(new Product(submitted.id(), product.name(), product.description(),
                product.price(), product.category(), product.sellerEmail()), submitted);
        verifyNoInteractions(catalogService);
    }

    @Test
    void findById_whenProductExists_returnsProduct() throws Exception {
        Product product = sampleProduct();
        when(productService.findById(ID)).thenReturn(product);

        mockMvc.perform(get(BASE + "/{id}", ID))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().json(objectMapper.writeValueAsString(product)));

        verify(productService).findById(ID);
        verifyNoInteractions(catalogService);
    }

    @Test
    void findAll_whenParametersAreMissing_usesDefaults() throws Exception {
        PaginationRequest pagination = new PaginationRequest(0, 20, null);
        ProductPage page = new ProductPage(List.of(sampleProduct()), 0, 20, 1, null);
        when(productService.findAll(pagination)).thenReturn(page);

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().json(objectMapper.writeValueAsString(page)));

        verify(productService).findAll(pagination);
        verifyNoInteractions(catalogService);
    }

    @Test
    void findAll_whenParametersAreProvided_passesThemToServiceAndReturnsPage() throws Exception {
        UUID cursor = new UUID(0, 2);
        PaginationRequest pagination = new PaginationRequest(2, 1, cursor);
        ProductPage page = new ProductPage(List.of(sampleProduct()), 0, 1, 5, ID);
        when(productService.findAll(pagination)).thenReturn(page);

        mockMvc.perform(get(BASE).param("page", "2").param("size", "1").param("cursor", cursor.toString()))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(page)));

        verify(productService).findAll(pagination);
        verifyNoInteractions(catalogService);
    }

    @Test
    void update_whenRequestIsValid_returnsUpdatedProduct() throws Exception {
        Product product = sampleProduct();
        when(productService.update(product)).thenReturn(product);

        mockMvc.perform(put(BASE + "/{id}", ID).contentType(APPLICATION_JSON).content(validJson()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().json(objectMapper.writeValueAsString(product)));

        verify(productService).update(product);
        verifyNoInteractions(catalogService);
    }

    @Test
    void delete_whenProductExists_returnsNoContent() throws Exception {
        mockMvc.perform(delete(BASE + "/{id}", ID))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(productService).deleteById(ID);
        verifyNoInteractions(catalogService);
    }

    @Test
    void findExternalProduct_whenProductExists_returnsCatalogProduct() throws Exception {
        CatalogProductDto product = new CatalogProductDto(7, "Galaxy bowl", "A bowl", new BigDecimal("4.20"));
        when(catalogService.findById(7)).thenReturn(product);

        mockMvc.perform(get(BASE + "/external/7"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(content().json(objectMapper.writeValueAsString(product)));

        verify(catalogService).findById(7);
        verifyNoInteractions(productService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Star toy", "GALAXY bowl", "comet collar", "ComEt toy"})
    void create_whenCosmicWordIsPresent_acceptsNameRegardlessOfCase(String name) throws Exception {
        Map<String, Object> body = validBody();
        body.put("name", name);
        when(productService.create(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post(BASE).contentType(APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name));

        verify(productService).create(any(Product.class));
        verifyNoInteractions(catalogService);
    }

    @ParameterizedTest
    @EnumSource(Category.class)
    void create_whenCategoryIsSupported_acceptsCategory(Category category) throws Exception {
        Map<String, Object> body = validBody();
        body.put("category", category.name());
        when(productService.create(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post(BASE).contentType(APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value(category.name()));

        verify(productService).create(any(Product.class));
        verifyNoInteractions(catalogService);
    }

    @Test
    void create_whenFieldsHaveMaximumAllowedLengths_acceptsRequest() throws Exception {
        Map<String, Object> body = validBody();
        body.put("name", "star " + "x".repeat(95));
        body.put("description", "x".repeat(1000));
        body.put("price", new BigDecimal("9999999999.99"));
        when(productService.create(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post(BASE).contentType(APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(body.get("name")))
                .andExpect(jsonPath("$.description").value(body.get("description")))
                .andExpect(jsonPath("$.price").value(9999999999.99));

        verify(productService).create(any(Product.class));
        verifyNoInteractions(catalogService);
    }

    @ParameterizedTest(name = "{index}: {0}, invalid {1}")
    @MethodSource("invalidWriteRequests")
    void write_whenFieldIsInvalid_returnsValidationProblemWithoutCallingServices(
            String method, String field, Object value) throws Exception {
        Map<String, Object> body = validBody();
        body.put(field, value);

        mockMvc.perform(writeRequest(method).content(objectMapper.writeValueAsString(body)))
                .andExpect(validationProblem(field, writePath(method)));

        verifyNoInteractions(productService, catalogService);
    }

    static Stream<Arguments> invalidWriteRequests() {
        return Stream.of(
                new InvalidField("name", null),
                new InvalidField("name", ""),
                new InvalidField("name", "   "),
                new InvalidField("name", "ab"),
                new InvalidField("name", "star " + "x".repeat(96)),
                new InvalidField("description", null),
                new InvalidField("description", ""),
                new InvalidField("description", "   "),
                new InvalidField("description", "x".repeat(1001)),
                new InvalidField("price", null),
                new InvalidField("price", BigDecimal.ZERO),
                new InvalidField("price", new BigDecimal("-1")),
                new InvalidField("price", new BigDecimal("1.001")),
                new InvalidField("price", new BigDecimal("10000000000")),
                new InvalidField("price", "not-a-number"),
                new InvalidField("category", null),
                new InvalidField("category", "UNKNOWN"),
                new InvalidField("sellerEmail", null),
                new InvalidField("sellerEmail", ""),
                new InvalidField("sellerEmail", "   "),
                new InvalidField("sellerEmail", "not-an-email"),
                new InvalidField("sellerEmail", "x".repeat(243) + "@example.com"))
                .flatMap(testCase -> Stream.of("POST", "PUT")
                        .map(method -> Arguments.of(method, testCase.field(), testCase.value())));
    }

    @ParameterizedTest(name = "{0}: name={1}")
    @MethodSource("nonCosmicNames")
    void write_whenNameHasNoWholeCosmicWord_returnsCosmicWordError(String method, String name) throws Exception {
        Map<String, Object> body = validBody();
        body.put("name", name);

        mockMvc.perform(writeRequest(method).content(objectMapper.writeValueAsString(body)))
                .andExpect(validationProblem("name", writePath(method)))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')].message",
                        hasItem("must contain a cosmic word: star, galaxy or comet")));

        verifyNoInteractions(productService, catalogService);
    }

    static Stream<Arguments> nonCosmicNames() {
        return Stream.of("Cosmic cat toy", "starlight toy", "galaxies toy", "comets toy")
                .flatMap(name -> Stream.of("POST", "PUT").map(method -> Arguments.of(method, name)));
    }

    @ParameterizedTest(name = "{index}: {0}, malformed or missing body")
    @MethodSource("invalidBodies")
    void write_whenJsonIsInvalidOrMissing_returnsValidationProblem(String method, String body) throws Exception {
        mockMvc.perform(writeRequest(method).content(body))
                .andExpect(validationProblem("body", writePath(method)));

        verifyNoInteractions(productService, catalogService);
    }

    static Stream<Arguments> invalidBodies() {
        return Stream.of("", "not-json", "[]", "null")
                .flatMap(body -> Stream.of("POST", "PUT").map(method -> Arguments.of(method, body)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT"})
    void write_whenContentTypeIsUnsupported_returns415(String method) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), writePath(method))
                        .contentType(TEXT_PLAIN).content(validJson()))
                .andExpect(problem(415, "unsupported-media-type", "Unsupported Media Type", writePath(method)));

        verifyNoInteractions(productService, catalogService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PUT", "DELETE"})
    void request_whenProductIdIsNotUuid_returnsValidationProblem(String method) throws Exception {
        String path = BASE + "/not-a-uuid";
        MockHttpServletRequestBuilder builder = request(HttpMethod.valueOf(method), path);
        if (method.equals("PUT")) {
            builder.contentType(APPLICATION_JSON).content(validJson());
        }

        mockMvc.perform(builder).andExpect(validationProblem("id", path));

        verifyNoInteractions(productService, catalogService);
    }

    @ParameterizedTest
    @CsvSource({"page,-1", "page,text", "page,2147483648", "size,0", "size,-1", "size,101", "size,text"})
    void findAll_whenPaginationIsInvalid_returnsValidationProblem(String field, String value) throws Exception {
        mockMvc.perform(get(BASE).param(field, value))
                .andExpect(validationProblem(field, BASE));

        verifyNoInteractions(productService, catalogService);
    }

    @ParameterizedTest
    @CsvSource({"0,1", "0,100", "2147483647,20"})
    void findAll_whenPaginationIsWithinBounds_acceptsParameters(int page, int size) throws Exception {
        PaginationRequest pagination = new PaginationRequest(page, size, null);
        ProductPage result = new ProductPage(List.of(), page, size, 0, null);
        when(productService.findAll(pagination)).thenReturn(result);

        mockMvc.perform(get(BASE).param("page", Integer.toString(page)).param("size", Integer.toString(size)))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(result)));

        verify(productService).findAll(pagination);
        verifyNoInteractions(catalogService);
    }

    @Test
    void findAll_whenCursorIsNotUuid_returnsValidationProblem() throws Exception {
        mockMvc.perform(get(BASE).param("cursor", "not-a-uuid"))
                .andExpect(validationProblem("cursor", BASE));

        verifyNoInteractions(productService, catalogService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "text", "2147483648"})
    void findExternalProduct_whenIdIsInvalid_returnsValidationProblem(String externalId) throws Exception {
        String path = BASE + "/external/" + externalId;

        mockMvc.perform(get(path)).andExpect(validationProblem("externalId", path));

        verifyNoInteractions(productService, catalogService);
    }

    @Test
    void findById_whenProductIsMissing_returns404() throws Exception {
        when(productService.findById(ID)).thenThrow(new ProductNotFoundException(ID));

        mockMvc.perform(get(BASE + "/{id}", ID))
                .andExpect(problem(404, "not-found", "Product not found", BASE + "/" + ID))
                .andExpect(jsonPath("$.detail").value("Product '%s' was not found.".formatted(ID)));

        verify(productService).findById(ID);
        verifyNoInteractions(catalogService);
    }

    @Test
    void update_whenProductIsMissing_returns404() throws Exception {
        Product product = sampleProduct();
        when(productService.update(product)).thenThrow(new ProductNotFoundException(ID));

        mockMvc.perform(put(BASE + "/{id}", ID).contentType(APPLICATION_JSON).content(validJson()))
                .andExpect(problem(404, "not-found", "Product not found", BASE + "/" + ID));

        verify(productService).update(product);
        verifyNoInteractions(catalogService);
    }

    @Test
    void delete_whenProductIsMissing_returns404() throws Exception {
        doThrow(new ProductNotFoundException(ID)).when(productService).deleteById(ID);

        mockMvc.perform(delete(BASE + "/{id}", ID))
                .andExpect(problem(404, "not-found", "Product not found", BASE + "/" + ID));

        verify(productService).deleteById(ID);
        verifyNoInteractions(catalogService);
    }

    @Test
    void findAll_whenCursorIsUnknown_returnsValidationProblem() throws Exception {
        UUID cursor = new UUID(0, 99);
        PaginationRequest pagination = new PaginationRequest(0, 20, cursor);
        when(productService.findAll(pagination)).thenThrow(new InvalidCursorException(cursor));

        mockMvc.perform(get(BASE).param("cursor", cursor.toString()))
                .andExpect(validationProblem("cursor", BASE))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("must refer to an existing product; cursor '%s' is unknown".formatted(cursor)));

        verify(productService).findAll(pagination);
        verifyNoInteractions(catalogService);
    }

    @Test
    void findExternalProduct_whenProductIsMissing_returns404() throws Exception {
        when(catalogService.findById(7)).thenThrow(new CatalogProductNotFoundException(7));

        mockMvc.perform(get(BASE + "/external/7"))
                .andExpect(problem(404, "not-found", "Product not found", BASE + "/external/7"))
                .andExpect(jsonPath("$.detail").value("External catalog product '7' was not found."));

        verify(catalogService).findById(7);
        verifyNoInteractions(productService);
    }

    @Test
    void findExternalProduct_whenCatalogIsUnavailable_returns502() throws Exception {
        when(catalogService.findById(7)).thenThrow(new CatalogUnavailableException("Catalog unavailable"));

        mockMvc.perform(get(BASE + "/external/7"))
                .andExpect(problem(502, "external-catalog", "External catalog unavailable", BASE + "/external/7"))
                .andExpect(jsonPath("$.detail").value("Catalog unavailable"));

        verify(catalogService).findById(7);
        verifyNoInteractions(productService);
    }

    @Test
    void findById_whenUnexpectedFailureOccurs_returnsSanitized500() throws Exception {
        when(productService.findById(ID)).thenThrow(new IllegalStateException("Private implementation detail"));

        mockMvc.perform(get(BASE + "/{id}", ID))
                .andExpect(problem(500, "internal", "Internal server error", BASE + "/" + ID))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred. Please try again later."))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());

        verify(productService).findById(ID);
        verifyNoInteractions(catalogService);
    }

    private ResultMatcher validationProblem(String field, String path) {
        return result -> {
            problem(400, "validation", "Validation failed", path).match(result);
            jsonPath("$.errors[*].field", hasItem(field)).match(result);
            jsonPath("$.errors[*].message", everyItem(not(blankOrNullString()))).match(result);
        };
    }

    private ResultMatcher problem(int expectedStatus, String type, String title, String path) {
        return result -> {
            status().is(expectedStatus).match(result);
            content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON).match(result);
            jsonPath("$.status").value(expectedStatus).match(result);
            jsonPath("$.type").value(ERROR_BASE + type).match(result);
            jsonPath("$.title").value(title).match(result);
            jsonPath("$.instance").value(path).match(result);
            jsonPath("$.detail", not(blankOrNullString())).match(result);
        };
    }

    private MockHttpServletRequestBuilder writeRequest(String method) {
        return request(HttpMethod.valueOf(method), writePath(method)).contentType(APPLICATION_JSON);
    }

    private static String writePath(String method) {
        return method.equals("POST") ? BASE : BASE + "/" + ID;
    }

    private String validJson() {
        return objectMapper.writeValueAsString(validBody());
    }

    private Map<String, Object> validBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Star cat toy");
        body.put("description", "A toy for an intergalactic cat");
        body.put("price", new BigDecimal("12.50"));
        body.put("category", "TOYS");
        body.put("sellerEmail", "seller@example.com");
        return body;
    }

    private Product sampleProduct() {
        return new Product(ID, "Star cat toy", "A toy for an intergalactic cat",
                new BigDecimal("12.50"), Category.TOYS, "seller@example.com");
    }

    private record InvalidField(String field, Object value) {
    }
}
