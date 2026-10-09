package com.market.spacecatmarket.api;

import com.market.spacecatmarket.application.catalog.CatalogProduct;
import com.market.spacecatmarket.application.catalog.CatalogProductNotFoundException;
import com.market.spacecatmarket.application.catalog.CatalogUnavailableException;
import com.market.spacecatmarket.application.catalog.ProductCatalogClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductApiTests {
    private static final String PRODUCTS = "/api/v1/products";
    private static final String MISSING_ID = "00000000-0000-0000-0000-000000000099";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductCatalogClient catalogClient;

    @Test
    void crudPersistsChangesAndPreservesTheProductId() throws Exception {
        String createdBody = mvc.perform(post(PRODUCTS).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Star Yarn"))
                .andExpect(jsonPath("$.description").value("Antigravity yarn for cats."))
                .andExpect(jsonPath("$.price").value(19.99))
                .andExpect(jsonPath("$.category").value("TOYS"))
                .andExpect(jsonPath("$.sellerEmail").value("seller@cosmo-cats.market"))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(createdBody).get("id").asString();
        assertThat(UUID.fromString(id)).isNotNull();

        mvc.perform(get(PRODUCTS + "/" + id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Star Yarn"));
        mvc.perform(get(PRODUCTS).param("page", "3").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content[0].id").value(id));

        Map<String, Object> update = validRequest();
        update.put("name", "Galaxy Milk");
        update.put("description", "Milk for an intergalactic journey.");
        update.put("price", new BigDecimal("8.50"));
        update.put("category", "FOOD");
        update.put("sellerEmail", "milk@cosmo-cats.market");
        mvc.perform(put(PRODUCTS + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Galaxy Milk"))
                .andExpect(jsonPath("$.description").value(update.get("description")))
                .andExpect(jsonPath("$.price").value(8.50))
                .andExpect(jsonPath("$.category").value("FOOD"))
                .andExpect(jsonPath("$.sellerEmail").value("milk@cosmo-cats.market"));
        mvc.perform(get(PRODUCTS + "/" + id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Galaxy Milk"));

        mvc.perform(delete(PRODUCTS + "/" + id))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(get(PRODUCTS + "/" + id)).andExpect(status().isNotFound());
        mvc.perform(get(PRODUCTS)).andExpect(jsonPath("$.totalElements").value(3));
        verifyNoInteractions(catalogClient);
    }

    @Test
    void creationReturnsTheResourceLocation() throws Exception {
        var response = mvc.perform(post(PRODUCTS).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andReturn().getResponse();
        String id = objectMapper.readTree(response.getContentAsString()).get("id").asString();
        assertThat(response.getHeader("Location")).isEqualTo(PRODUCTS + "/" + id);
        mvc.perform(delete(PRODUCTS + "/" + id)).andExpect(status().isNoContent());
    }

    @Test
    void paginationReturnsMockProductsAndAnEmptyPageBeyondTheEnd() throws Exception {
        mvc.perform(get(PRODUCTS))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get(PRODUCTS).param("page", "1").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Galaxy Cosmic Milk"))
                .andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get(PRODUCTS).param("page", "2147483647").param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(Integer.MAX_VALUE))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @ParameterizedTest
    @CsvSource({"page,-1", "size,0", "size,101", "page,abc", "size,1.5", "page,2147483648"})
    void invalidPaginationIdentifiesTheParameter(String parameter, String value) throws Exception {
        mvc.perform(get(PRODUCTS).param(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://cosmo-cats.market/errors/validation"))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value(PRODUCTS))
                .andExpect(jsonPath("$.errors[*].field", hasItem(parameter)));
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    void invalidProductFieldsAreReportedWithoutCreatingAProduct(String field, Object value) throws Exception {
        Map<String, Object> request = validRequest();
        request.put(field, value);
        mvc.perform(post(PRODUCTS).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value(PRODUCTS))
                .andExpect(jsonPath("$.errors[*].field", hasItem(field)));
        mvc.perform(get(PRODUCTS)).andExpect(jsonPath("$.totalElements").value(3));
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of("name", null), Arguments.of("name", " "),
                Arguments.of("name", "Ordinary yarn"), Arguments.of("name", "Starfish yarn"),
                Arguments.of("name", "Star " + "x".repeat(96)),
                Arguments.of("description", null), Arguments.of("description", " "),
                Arguments.of("description", "x".repeat(1001)),
                Arguments.of("price", null), Arguments.of("price", 0), Arguments.of("price", -1),
                Arguments.of("price", new BigDecimal("1.001")),
                Arguments.of("price", new BigDecimal("10000000000")),
                Arguments.of("category", null), Arguments.of("category", "UNKNOWN"),
                Arguments.of("sellerEmail", null), Arguments.of("sellerEmail", " "),
                Arguments.of("sellerEmail", "not-an-email"),
                Arguments.of("sellerEmail", "a".repeat(245) + "@cosmo.market"));
    }

    @Test
    void validationDetailMatchesTheRequestedRfc9457Example() throws Exception {
        Map<String, Object> request = validRequest();
        request.put("price", 0);
        mvc.perform(post(PRODUCTS).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://cosmo-cats.market/errors/validation"))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value("Field 'price' must be greater than 0."));
    }

    @Test
    void multipleViolationsIncludeEachField() throws Exception {
        Map<String, Object> request = validRequest();
        request.put("name", "Ordinary yarn");
        request.put("price", 0);
        request.put("sellerEmail", "invalid");
        mvc.perform(post(PRODUCTS).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[*].field", hasItem("name")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("price")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("sellerEmail")));
    }

    @Test
    void invalidUpdateLeavesTheStoredProductUnchanged() throws Exception {
        Map<String, Object> request = validRequest();
        request.put("price", 0);
        String path = PRODUCTS + "/00000000-0000-0000-0000-000000000001";
        mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.instance").value(path));
        mvc.perform(get(path)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Star Antigravity Yarn"))
                .andExpect(jsonPath("$.price").value(19.99));
    }

    @Test
    void missingProductsReturn404ForReadUpdateAndDelete() throws Exception {
        String path = PRODUCTS + "/" + MISSING_ID;
        mvc.perform(get(path)).andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://cosmo-cats.market/errors/not-found"))
                .andExpect(jsonPath("$.status").value(404)).andExpect(jsonPath("$.instance").value(path));
        mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isNotFound());
        mvc.perform(delete(path)).andExpect(status().isNotFound());
        mvc.perform(get(path)).andExpect(status().isNotFound());
    }

    @Test
    void invalidUuidIsAFieldValidationError() throws Exception {
        mvc.perform(get(PRODUCTS + "/not-a-uuid")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("id"))
                .andExpect(jsonPath("$.detail").value("Field 'id' must be a valid UUID."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "[]", "null", ""})
    void malformedOrMissingBodyUsesProblemDetails(String body) throws Exception {
        mvc.perform(post(PRODUCTS).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("body"));
    }

    @Test
    void unsupportedContentTypeUsesProblemDetails() throws Exception {
        mvc.perform(post(PRODUCTS).contentType(MediaType.TEXT_PLAIN).content("Star Yarn"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.instance").value(PRODUCTS));
    }

    @Test
    void externalLookupUsesTheCatalogPortWithoutPersistingTheResult() throws Exception {
        when(catalogClient.findById(1)).thenReturn(new CatalogProduct(1, "Provider title", "Description",
                new BigDecimal("9.99")));
        mvc.perform(get(PRODUCTS + "/external/1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1)).andExpect(jsonPath("$.title").value("Provider title"))
                .andExpect(jsonPath("$.price").value(9.99));
        mvc.perform(get(PRODUCTS)).andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void externalFailuresHaveDocumentedStatuses() throws Exception {
        when(catalogClient.findById(1)).thenThrow(new CatalogProductNotFoundException(1));
        when(catalogClient.findById(2)).thenThrow(new CatalogUnavailableException("External catalog is temporarily unavailable."));
        mvc.perform(get(PRODUCTS + "/external/1")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        mvc.perform(get(PRODUCTS + "/external/2")).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.type").value("https://cosmo-cats.market/errors/external-catalog"))
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.instance").value(PRODUCTS + "/external/2"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "invalid"})
    void invalidExternalIdDoesNotCallTheProvider(String id) throws Exception {
        mvc.perform(get(PRODUCTS + "/external/" + id)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("externalId"));
        verifyNoInteractions(catalogClient);
    }

    @Test
    void unexpectedFailuresDoNotExposeInternalDetails() throws Exception {
        when(catalogClient.findById(1)).thenThrow(new IllegalStateException("private provider detail"));
        mvc.perform(get(PRODUCTS + "/external/1")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred. Please try again later."))
                .andExpect(jsonPath("$.type").value("https://cosmo-cats.market/errors/internal"));
    }

    private static Map<String, Object> validRequest() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", "Star Yarn");
        request.put("description", "Antigravity yarn for cats.");
        request.put("price", new BigDecimal("19.99"));
        request.put("category", "TOYS");
        request.put("sellerEmail", "seller@cosmo-cats.market");
        return request;
    }
}
