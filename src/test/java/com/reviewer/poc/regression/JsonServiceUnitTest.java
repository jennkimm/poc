package com.reviewer.poc.regression;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.reviewer.poc.JsonService;
import com.reviewer.poc.model.Product;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * JsonService 직접 유닛 테스트 — SubAgent3에서 발견된 미커버 메서드 4개 커버.
 * parseFromString / parseListFromString / parseToTree / toJsonString
 */
@DisplayName("JsonService 유닛 테스트")
class JsonServiceUnitTest {

    private JsonService service;
    private File tempFile;

    @BeforeEach
    void setUp() throws IOException {
        service = new JsonService();
        tempFile = Files.createTempFile("json_service_test_", ".json").toFile();
        tempFile.deleteOnExit();
    }

    @AfterEach
    void tearDown() {
        tempFile.delete();
    }

    // ══════════════════════════════════════════════════════════════════════
    // parseFromString
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("JS-01: JSON 문자열 → 단일 객체 파싱 — 모든 필드 정확히 매핑")
    void parseFromStringMapsAllFields() throws IOException {
        String json = """
                {"id":1,"name":"노트북","price":1299000,"category":"전자제품","in_stock":true,"tags":["laptop","premium"]}
                """;
        Product p = service.parseFromString(json, Product.class);
        assertThat(p.getId()).isEqualTo(1);
        assertThat(p.getName()).isEqualTo("노트북");
        assertThat(p.getPrice()).isEqualTo(1_299_000);
        assertThat(p.getCategory()).isEqualTo("전자제품");
        assertThat(p.isInStock()).isTrue();
        assertThat(p.getTags()).containsExactly("laptop", "premium");
    }

    @Test
    @DisplayName("JS-02: in_stock 스네이크케이스 → isInStock() @JsonProperty 매핑")
    void parseFromStringMapsJsonPropertyAnnotation() throws IOException {
        String json = """
                {"id":1,"name":"test","price":0,"category":"cat","in_stock":false}
                """;
        Product p = service.parseFromString(json, Product.class);
        assertThat(p.isInStock()).isFalse();
    }

    @Test
    @DisplayName("JS-03: 알 수 없는 필드 포함 JSON — @JsonIgnoreProperties 로 무시")
    void parseFromStringIgnoresUnknownFields() throws IOException {
        String json = """
                {"id":1,"name":"test","price":0,"category":"cat","in_stock":true,"extra":"ignored"}
                """;
        assertThatCode(() -> service.parseFromString(json, Product.class))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("JS-04: parseFromString → toJsonString 라운드트립 정합성")
    void parseFromStringRoundTrip() throws IOException {
        String original = """
                {"id":7,"name":"라운드트립","price":99.9,"category":"test","in_stock":false,"tags":["a","b"]}
                """.trim();
        Product p = service.parseFromString(original, Product.class);
        String serialized = service.toJsonString(p);
        Product restored = service.parseFromString(serialized, Product.class);
        assertThat(restored.getId()).isEqualTo(7);
        assertThat(restored.getName()).isEqualTo("라운드트립");
        assertThat(restored.getTags()).containsExactly("a", "b");
    }

    // ══════════════════════════════════════════════════════════════════════
    // parseListFromString
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("JS-05: JSON 배열 문자열 → 리스트 파싱 — 건수 및 순서 일치")
    void parseListFromStringReturnsList() throws IOException {
        String json = """
                [
                  {"id":1,"name":"노트북","price":1299000,"category":"전자제품","in_stock":true},
                  {"id":2,"name":"마우스","price":35000,"category":"주변기기","in_stock":false}
                ]
                """;
        List<Product> list = service.parseListFromString(json, new TypeReference<>() {});
        assertThat(list).hasSize(2);
        assertThat(list).extracting(Product::getName).containsExactly("노트북", "마우스");
        assertThat(list).extracting(Product::getId).containsExactly(1, 2);
    }

    @Test
    @DisplayName("JS-06: 빈 JSON 배열 문자열 → 빈 리스트 반환")
    void parseListFromStringEmptyArray() throws IOException {
        List<Product> list = service.parseListFromString("[]", new TypeReference<>() {});
        assertThat(list).isEmpty();
    }

    @Test
    @DisplayName("JS-07: 단일 요소 JSON 배열 → 1건 리스트")
    void parseListFromStringSingleElement() throws IOException {
        String json = """
                [{"id":1,"name":"단건","price":1000,"category":"cat","in_stock":true}]
                """;
        List<Product> list = service.parseListFromString(json, new TypeReference<>() {});
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getName()).isEqualTo("단건");
    }

    // ══════════════════════════════════════════════════════════════════════
    // parseToTree
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("JS-08: JSON 문자열 → JsonNode 트리 탐색 — 기본 타입 접근")
    void parseToTreeAccessesPrimitiveFields() throws IOException {
        String json = """
                {"store":"Seoul","floor":3,"open":true}
                """;
        JsonNode node = service.parseToTree(json);
        assertThat(node.path("store").asText()).isEqualTo("Seoul");
        assertThat(node.path("floor").asInt()).isEqualTo(3);
        assertThat(node.path("open").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("JS-09: 존재하지 않는 필드 path() 접근 — MissingNode 반환 (NPE 없음)")
    void parseToTreeMissingFieldReturnsMissingNode() throws IOException {
        JsonNode node = service.parseToTree("{\"a\":1}");
        assertThat(node.path("nonexistent").isMissingNode()).isTrue();
    }

    @Test
    @DisplayName("JS-10: 중첩 JSON 구조 트리 탐색")
    void parseToTreeNestedStructure() throws IOException {
        String json = """
                {"product":{"name":"노트북","specs":{"ram":16,"ssd":512}}}
                """;
        JsonNode node = service.parseToTree(json);
        assertThat(node.path("product").path("name").asText()).isEqualTo("노트북");
        assertThat(node.path("product").path("specs").path("ram").asInt()).isEqualTo(16);
        assertThat(node.path("product").path("specs").path("ssd").asInt()).isEqualTo(512);
    }

    @Test
    @DisplayName("JS-11: JSON 배열 루트 → 배열 노드 접근")
    void parseToTreeRootArray() throws IOException {
        JsonNode node = service.parseToTree("[1,2,3]");
        assertThat(node.isArray()).isTrue();
        assertThat(node.size()).isEqualTo(3);
        assertThat(node.get(0).asInt()).isEqualTo(1);
    }

    // ══════════════════════════════════════════════════════════════════════
    // toJsonString
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("JS-12: 객체 → JSON 문자열 직렬화 — 핵심 필드 포함 확인")
    void toJsonStringContainsExpectedFields() throws IOException {
        Product p = new Product();
        p.setId(1);
        p.setName("노트북");
        p.setPrice(1_299_000);
        p.setCategory("전자제품");
        p.setInStock(true);
        p.setTags(List.of("laptop", "premium"));

        String json = service.toJsonString(p);
        assertThat(json).contains("\"name\" : \"노트북\"");
        assertThat(json).contains("\"in_stock\" : true");
        assertThat(json).contains("\"laptop\"");
        assertThat(json).contains("\"premium\"");
    }

    @Test
    @DisplayName("JS-13: toJsonString — in_stock 필드명(스네이크케이스) 직렬화 확인")
    void toJsonStringUsesSnakeCaseFieldName() throws IOException {
        Product p = new Product();
        p.setName("test"); p.setInStock(false);
        String json = service.toJsonString(p);
        assertThat(json).contains("\"in_stock\" : false");
        assertThat(json).doesNotContain("\"inStock\"");
    }

    @Test
    @DisplayName("JS-14: toJsonString — null 태그 포함 객체 직렬화 시 예외 없음")
    void toJsonStringNullTagsDoesNotThrow() throws IOException {
        Product p = new Product();
        p.setName("test"); p.setPrice(0); p.setCategory("cat");
        p.setTags(null);
        assertThatCode(() -> service.toJsonString(p)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("JS-15: toJsonString → parseListFromString 리스트 라운드트립")
    void toJsonStringListRoundTrip() throws IOException {
        Product p1 = new Product(); p1.setId(1); p1.setName("A"); p1.setPrice(100); p1.setCategory("c");
        Product p2 = new Product(); p2.setId(2); p2.setName("B"); p2.setPrice(200); p2.setCategory("c");
        List<Product> original = List.of(p1, p2);

        String json = service.toJsonString(original);
        List<Product> restored = service.parseListFromString(json, new TypeReference<>() {});
        assertThat(restored).hasSize(2);
        assertThat(restored).extracting(Product::getName).containsExactly("A", "B");
    }
}
