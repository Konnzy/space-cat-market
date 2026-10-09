package com.market.spacecatmarket.api;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiContractTests {

    @Test
    void contractIsReadableYamlWithCrudOperationsAndResolvableLocalReferences() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/api-specs/cosmo-cats-market.yml")) {
            assertThat(input).isNotNull();
            Map<String, Object> contract = new Yaml().load(input);
            assertThat(contract.get("openapi")).isEqualTo("3.0.3");
            Map<?, ?> paths = (Map<?, ?>) contract.get("paths");
            Map<?, ?> collection = (Map<?, ?>) paths.get("/api/v1/products");
            assertThat(collection.containsKey("get")).isTrue();
            assertThat(collection.containsKey("post")).isTrue();
            Map<?, ?> product = (Map<?, ?>) paths.get("/api/v1/products/{id}");
            assertThat(product.containsKey("get")).isTrue();
            assertThat(product.containsKey("put")).isTrue();
            assertThat(product.containsKey("delete")).isTrue();
            assertThat(paths.containsKey("/api/v1/products/external/{externalId}")).isTrue();
            checkReferences(contract, contract);
        }
    }

    private void checkReferences(Object node, Map<String, Object> root) {
        if (node instanceof Map<?, ?> map) {
            if (map.get("$ref") instanceof String reference) {
                assertThat(reference).startsWith("#/");
                Object target = root;
                for (String token : reference.substring(2).split("/")) {
                    assertThat(target).isInstanceOf(Map.class);
                    target = ((Map<?, ?>) target).get(token.replace("~1", "/").replace("~0", "~"));
                    assertThat(target).as("Reference %s must resolve", reference).isNotNull();
                }
            }
            map.values().forEach(value -> checkReferences(value, root));
        } else if (node instanceof List<?> list) {
            list.forEach(value -> checkReferences(value, root));
        }
    }
}
