package com.reviewer.poc.regression;

import com.reviewer.poc.JsonService;
import com.reviewer.poc.exception.DataNotFoundException;
import com.reviewer.poc.model.Product;
import com.reviewer.poc.repository.ProductRepository;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Regression Test — 기존 CRUD 기능이 코드 변경 후에도 동일하게 동작하는지 검증.
 * 각 테스트는 독립된 임시 파일을 사용하므로 순서에 무관하게 실행 가능.
 */
@DisplayName("Regression Test: CRUD 기본 기능")
class CrudRegressionTest {

    private ProductRepository repo;
    private File tempFile;

    @BeforeEach
    void setUp() throws IOException {
        tempFile = Files.createTempFile("products_regression_", ".json").toFile();
        tempFile.deleteOnExit();
        repo = new ProductRepository(new JsonService(), tempFile.getAbsolutePath());
    }

    @AfterEach
    void tearDown() {
        tempFile.delete();
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────
    private Product product(String name, double price, String category, boolean inStock) {
        Product p = new Product();
        p.setName(name);
        p.setPrice(price);
        p.setCategory(category);
        p.setInStock(inStock);
        p.setTags(List.of("tag1", "tag2"));
        return p;
    }

    // ══════════════════════════════════════════════════════════════════════
    // CREATE
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("RT-C01: 첫 번째 상품 등록 시 ID = 1 할당")
    void createFirstProductGetsId1() throws IOException {
        Product saved = repo.save(product("노트북", 1_200_000, "전자제품", true));
        assertThat(saved.getId()).isEqualTo(1);
    }

    @Test
    @DisplayName("RT-C02: 여러 상품 등록 시 ID 순차 증가")
    void createMultipleProductsAutoIncrementId() throws IOException {
        repo.save(product("노트북", 1_200_000, "전자제품", true));
        repo.save(product("마우스", 35_000, "주변기기", true));
        repo.save(product("키보드", 89_000, "주변기기", false));

        List<Product> all = repo.findAll();
        assertThat(all).hasSize(3);
        assertThat(all).extracting(Product::getId).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("RT-C03: 등록된 모든 필드가 저장 파일에 정확히 보존")
    void createPreservesAllFields() throws IOException {
        Product p = product("노트북", 1_299_000, "전자제품", true);
        p.setTags(List.of("laptop", "premium"));

        Product saved = repo.save(p);

        assertThat(saved.getName()).isEqualTo("노트북");
        assertThat(saved.getPrice()).isEqualTo(1_299_000);
        assertThat(saved.getCategory()).isEqualTo("전자제품");
        assertThat(saved.isInStock()).isTrue();
        assertThat(saved.getTags()).containsExactly("laptop", "premium");
    }

    @Test
    @DisplayName("RT-C04: 중간 ID 삭제 후 신규 등록 시 max+1 ID 부여")
    void createAfterDeleteContinuesFromMax() throws IOException {
        repo.save(product("A", 1000, "cat", true));  // ID 1
        repo.save(product("B", 2000, "cat", true));  // ID 2
        repo.save(product("C", 3000, "cat", true));  // ID 3
        repo.deleteById(2);

        Product newProduct = repo.save(product("D", 4000, "cat", true));
        assertThat(newProduct.getId()).isEqualTo(4);
    }

    // ══════════════════════════════════════════════════════════════════════
    // READ
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("RT-R01: 빈 저장소에서 전체 조회 시 빈 리스트 반환")
    void findAllReturnsEmptyListWhenNoData() throws IOException {
        assertThat(repo.findAll()).isEmpty();
    }

    @Test
    @DisplayName("RT-R02: 등록된 개수만큼 전체 목록 반환")
    void findAllReturnsAllSavedProducts() throws IOException {
        repo.save(product("A", 1000, "cat", true));
        repo.save(product("B", 2000, "cat", true));
        assertThat(repo.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("RT-R03: 존재하는 ID로 단건 조회 성공")
    void findByIdReturnsProductWhenExists() throws IOException {
        repo.save(product("노트북", 1_200_000, "전자제품", true));
        Optional<Product> result = repo.findById(1);
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("노트북");
    }

    @Test
    @DisplayName("RT-R04: 존재하지 않는 ID 조회 시 Optional.empty 반환")
    void findByIdReturnsEmptyWhenNotExists() throws IOException {
        Optional<Product> result = repo.findById(999);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("RT-R05: 이름 키워드 검색 — 부분 일치, 대소문자 무시")
    void findByNameIsCaseInsensitivePartialMatch() throws IOException {
        repo.save(product("MacBook Pro", 2_000_000, "전자제품", true));
        repo.save(product("MacBook Air", 1_500_000, "전자제품", true));
        repo.save(product("마우스", 35_000, "주변기기", true));

        List<Product> result = repo.findByName("macbook");
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Product::getName)
                .containsExactlyInAnyOrder("MacBook Pro", "MacBook Air");
    }

    @Test
    @DisplayName("RT-R06: 이름 검색 결과 없으면 빈 리스트")
    void findByNameReturnsEmptyWhenNoMatch() throws IOException {
        repo.save(product("노트북", 1_200_000, "전자제품", true));
        assertThat(repo.findByName("존재하지않는상품")).isEmpty();
    }

    @Test
    @DisplayName("RT-R07: 카테고리 검색 — 해당 카테고리 상품만 반환")
    void findByCategoryFiltersCorrectly() throws IOException {
        repo.save(product("노트북", 1_200_000, "전자제품", true));
        repo.save(product("마우스", 35_000, "주변기기", true));
        repo.save(product("키보드", 89_000, "주변기기", false));

        List<Product> result = repo.findByCategory("주변기기");
        assertThat(result).hasSize(2)
                .extracting(Product::getName)
                .containsExactlyInAnyOrder("마우스", "키보드");
    }

    // ══════════════════════════════════════════════════════════════════════
    // UPDATE
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("RT-U01: 존재하는 ID 수정 시 모든 필드 반영")
    void updateExistingProductChangesFields() throws IOException {
        repo.save(product("노트북", 1_200_000, "전자제품", true));

        Product updated = product("노트북 Pro", 1_500_000, "전자제품", false);
        repo.update(1, updated);

        Product found = repo.findById(1).orElseThrow();
        assertThat(found.getName()).isEqualTo("노트북 Pro");
        assertThat(found.getPrice()).isEqualTo(1_500_000);
        assertThat(found.isInStock()).isFalse();
    }

    @Test
    @DisplayName("RT-U02: 수정 후에도 ID 유지")
    void updatePreservesProductId() throws IOException {
        repo.save(product("노트북", 1_200_000, "전자제품", true));
        Product updated = product("수정됨", 999, "기타", false);
        Product result = repo.update(1, updated);
        assertThat(result.getId()).isEqualTo(1);
    }

    @Test
    @DisplayName("RT-U03: 수정 후 전체 건수 변화 없음")
    void updateDoesNotChangeCount() throws IOException {
        repo.save(product("A", 1000, "cat", true));
        repo.save(product("B", 2000, "cat", true));
        repo.update(1, product("A-수정", 9999, "cat", true));
        assertThat(repo.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("RT-U04: 존재하지 않는 ID 수정 시 DataNotFoundException 발생")
    void updateNonExistentIdThrowsException() {
        assertThatThrownBy(() -> repo.update(999, product("X", 1000, "cat", true)))
                .isInstanceOf(DataNotFoundException.class);
    }

    // ══════════════════════════════════════════════════════════════════════
    // DELETE
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("RT-D01: 존재하는 ID 삭제 후 목록에서 제거됨")
    void deleteRemovesProductFromList() throws IOException {
        repo.save(product("A", 1000, "cat", true));
        repo.save(product("B", 2000, "cat", true));

        repo.deleteById(1);

        assertThat(repo.findAll()).hasSize(1);
        assertThat(repo.findById(1)).isEmpty();
    }

    @Test
    @DisplayName("RT-D02: 삭제 후 나머지 데이터 무결성 유지")
    void deletePreservesRemainingProducts() throws IOException {
        repo.save(product("A", 1000, "cat", true));
        repo.save(product("B", 2000, "cat", true));
        repo.save(product("C", 3000, "cat", true));

        repo.deleteById(2);

        List<Product> remaining = repo.findAll();
        assertThat(remaining).hasSize(2);
        assertThat(remaining).extracting(Product::getId).containsExactly(1, 3);
        assertThat(remaining).extracting(Product::getName).containsExactly("A", "C");
    }

    @Test
    @DisplayName("RT-D03: 존재하지 않는 ID 삭제 시 DataNotFoundException 발생")
    void deleteNonExistentIdThrowsException() {
        assertThatThrownBy(() -> repo.deleteById(999))
                .isInstanceOf(DataNotFoundException.class);
    }

    // ══════════════════════════════════════════════════════════════════════
    // 데이터 영속성
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("RT-P01: 저장 후 새 Repository 인스턴스로 재로드해도 데이터 동일")
    void dataPersistedAcrossRepositoryInstances() throws IOException {
        repo.save(product("노트북", 1_200_000, "전자제품", true));
        repo.save(product("마우스", 35_000, "주변기기", true));

        // 동일 파일을 바라보는 새 인스턴스
        ProductRepository reloaded = new ProductRepository(new JsonService(), tempFile.getAbsolutePath());
        List<Product> all = reloaded.findAll();

        assertThat(all).hasSize(2);
        assertThat(all).extracting(Product::getName)
                .containsExactly("노트북", "마우스");
    }

    @Test
    @DisplayName("RT-P02: Create → Update → Delete → Read 전체 흐름 정합성")
    void fullCrudFlowMaintainsConsistency() throws IOException {
        // Create 3건
        repo.save(product("A", 1000, "cat1", true));
        repo.save(product("B", 2000, "cat2", true));
        repo.save(product("C", 3000, "cat1", false));

        // Update ID:2
        repo.update(2, product("B-수정", 2500, "cat2", false));

        // Delete ID:3
        repo.deleteById(3);

        // 최종 검증
        List<Product> all = repo.findAll();
        assertThat(all).hasSize(2);
        assertThat(repo.findById(2).orElseThrow().getName()).isEqualTo("B-수정");
        assertThat(repo.findById(2).orElseThrow().getPrice()).isEqualTo(2500);
        assertThat(repo.findById(3)).isEmpty();
    }
}
