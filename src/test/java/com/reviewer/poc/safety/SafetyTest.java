package com.reviewer.poc.safety;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.reviewer.poc.JsonService;
import com.reviewer.poc.exception.DataNotFoundException;
import com.reviewer.poc.model.Product;
import com.reviewer.poc.repository.ProductRepository;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

/**
 * Safety Test — 못된 사용자 시나리오: 비정상 입력, 경계값, 파일 손상 등으로
 * 시스템이 크래시 없이 안전하게 처리하는지 검증.
 */
@DisplayName("Safety Test: 비정상 입력 및 경계값 안정성")
class SafetyTest {

    private ProductRepository repo;
    private File tempFile;

    @BeforeEach
    void setUp() throws IOException {
        tempFile = Files.createTempFile("products_safety_", ".json").toFile();
        tempFile.deleteOnExit();
        repo = new ProductRepository(new JsonService(), tempFile.getAbsolutePath());
    }

    @AfterEach
    void tearDown() {
        tempFile.delete();
    }

    private Product makeProduct(String name, double price, String category) {
        Product p = new Product();
        p.setName(name);
        p.setPrice(price);
        p.setCategory(category);
        p.setInStock(true);
        return p;
    }

    // ══════════════════════════════════════════════════════════════════════
    // CREATE — 비정상 필드값
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("ST-C01: 빈 문자열 이름으로 등록 — 저장은 되어야 함 (필터링은 UI 몫)")
    void createWithEmptyNameDoesNotCrash() throws IOException {
        Product saved = repo.save(makeProduct("", 1000, "cat"));
        assertThat(saved.getId()).isEqualTo(1);
        assertThat(repo.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("ST-C02: 공백만 있는 이름으로 등록 — 저장은 허용")
    void createWithBlankNameDoesNotCrash() throws IOException {
        Product saved = repo.save(makeProduct("   ", 1000, "cat"));
        assertThat(repo.findAll()).hasSize(1);
        assertThat(saved.getName()).isEqualTo("   ");
    }

    @Test
    @DisplayName("ST-C03: 음수 가격 — 저장은 되어야 함 (도메인 제약은 UI 몫)")
    void createWithNegativePrice() throws IOException {
        Product saved = repo.save(makeProduct("상품", -9999, "cat"));
        assertThat(repo.findById(saved.getId()).orElseThrow().getPrice()).isEqualTo(-9999);
    }

    @Test
    @DisplayName("ST-C04: 가격 0 — 허용")
    void createWithZeroPrice() throws IOException {
        Product saved = repo.save(makeProduct("무료", 0, "cat"));
        assertThat(saved.getPrice()).isEqualTo(0);
    }

    @Test
    @DisplayName("ST-C05: Double 최댓값 가격 — JSON 직렬화/역직렬화 후 동일")
    void createWithMaxDoublePrice() throws IOException {
        Product saved = repo.save(makeProduct("극단적 가격", Double.MAX_VALUE, "cat"));
        Product loaded = repo.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getPrice()).isEqualTo(Double.MAX_VALUE);
    }

    @Test
    @DisplayName("ST-C06: NaN 가격 — JSON에서 문자열로 직렬화, 저장 후 재로드 가능")
    void createWithNaNPrice() throws IOException {
        // NaN은 JSON 표준 외 값이지만 Jackson은 허용하므로 크래시 없어야 함
        assertThatCode(() -> {
            Product saved = repo.save(makeProduct("NaN가격", Double.NaN, "cat"));
            // 재로드 시도
            repo.findById(saved.getId());
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ST-C07: null 태그 리스트 — 저장 및 재로드 시 예외 없음")
    void createWithNullTags() throws IOException {
        Product p = makeProduct("상품", 1000, "cat");
        p.setTags(null);
        Product saved = repo.save(p);
        assertThat(repo.findById(saved.getId())).isPresent();
    }

    @Test
    @DisplayName("ST-C08: 빈 태그 리스트 — 저장 및 재로드 정상")
    void createWithEmptyTags() throws IOException {
        Product p = makeProduct("상품", 1000, "cat");
        p.setTags(Collections.emptyList());
        repo.save(p);
        Product loaded = repo.findAll().get(0);
        assertThat(loaded.getTags()).isEmpty();
    }

    @Test
    @DisplayName("ST-C09: XSS 페이로드 이름 — 그대로 저장/로드 (이스케이프는 출력 계층 몫)")
    void createWithXssPayloadInName() throws IOException {
        String xss = "<script>alert('xss')</script>";
        Product saved = repo.save(makeProduct(xss, 1000, "cat"));
        assertThat(repo.findById(saved.getId()).orElseThrow().getName()).isEqualTo(xss);
    }

    @Test
    @DisplayName("ST-C10: SQL Injection 문자열 이름 — 파일 기반이므로 안전, 그대로 저장")
    void createWithSqlInjectionInName() throws IOException {
        String sql = "'; DROP TABLE products; --";
        Product saved = repo.save(makeProduct(sql, 1000, "cat"));
        assertThat(repo.findById(saved.getId()).orElseThrow().getName()).isEqualTo(sql);
    }

    @Test
    @DisplayName("ST-C11: JSON 특수문자 포함 이름 (큰따옴표, 역슬래시) — Jackson이 이스케이프하여 저장")
    void createWithJsonSpecialCharsInName() throws IOException {
        String tricky = "test\"injection\\path\nnewline\ttab";
        Product saved = repo.save(makeProduct(tricky, 1000, "cat"));
        assertThat(repo.findById(saved.getId()).orElseThrow().getName()).isEqualTo(tricky);
    }

    @Test
    @DisplayName("ST-C12: 이모지 및 유니코드 이름 — UTF-8 저장/로드 정상")
    void createWithEmojiAndUnicode() throws IOException {
        String emoji = "💻 노트북 🎯 테스트 αβγδ 한글";
        Product saved = repo.save(makeProduct(emoji, 1000, "cat"));
        assertThat(repo.findById(saved.getId()).orElseThrow().getName()).isEqualTo(emoji);
    }

    @Test
    @DisplayName("ST-C13: 10,000자 이름 — 저장/로드 성공 (메모리/파일 한도 내)")
    void createWithVeryLongName() throws IOException {
        String longName = "가".repeat(10_000);
        Product saved = repo.save(makeProduct(longName, 1000, "cat"));
        assertThat(repo.findById(saved.getId()).orElseThrow().getName()).hasSize(10_000);
    }

    @Test
    @DisplayName("ST-C14: 100건 연속 등록 — ID 유일성 및 총 건수 정합")
    void createHundredItemsAllUniqueIds() throws IOException {
        for (int i = 0; i < 100; i++) {
            repo.save(makeProduct("상품" + i, i * 1000, "cat"));
        }
        List<Product> all = repo.findAll();
        assertThat(all).hasSize(100);
        long distinctIds = all.stream().map(Product::getId).distinct().count();
        assertThat(distinctIds).isEqualTo(100);
        assertThat(all).extracting(Product::getId)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 100).boxed().toList());
    }

    // ══════════════════════════════════════════════════════════════════════
    // READ — 경계값 및 비정상 키
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("ST-R01: ID = 0 조회 — Optional.empty 반환 (예외 없음)")
    void findByIdZeroReturnsEmpty() throws IOException {
        assertThat(repo.findById(0)).isEmpty();
    }

    @Test
    @DisplayName("ST-R02: 음수 ID 조회 — Optional.empty 반환")
    void findByIdNegativeReturnsEmpty() throws IOException {
        assertThat(repo.findById(-1)).isEmpty();
        assertThat(repo.findById(Integer.MIN_VALUE)).isEmpty();
    }

    @Test
    @DisplayName("ST-R03: Integer.MAX_VALUE ID 조회 — Optional.empty 반환")
    void findByIdMaxIntReturnsEmpty() throws IOException {
        assertThat(repo.findById(Integer.MAX_VALUE)).isEmpty();
    }

    @Test
    @DisplayName("ST-R04: 빈 문자열 키워드 이름 검색 — 빈 리스트 반환 (blank 가드 처리)")
    void findByNameEmptyStringReturnsEmpty() throws IOException {
        repo.save(makeProduct("A", 1000, "cat"));
        repo.save(makeProduct("B", 2000, "cat"));
        assertThat(repo.findByName("")).isEmpty();
    }

    @Test
    @DisplayName("ST-R05: null 키워드 이름 검색 — 빈 리스트 반환 (null 가드 처리)")
    void findByNameNullReturnsEmpty() throws IOException {
        assertThat(repo.findByName(null)).isEmpty();
    }

    @Test
    @DisplayName("ST-R06: null 카테고리 검색 — 빈 리스트 반환 (null 가드 처리)")
    void findByCategoryNullReturnsEmpty() throws IOException {
        assertThat(repo.findByCategory(null)).isEmpty();
    }

    @Test
    @DisplayName("ST-R07: 파일 없는 상태에서 findAll — 빈 리스트 반환 (NPE 없음)")
    void findAllWhenFileNotExistReturnsEmpty() throws IOException {
        tempFile.delete(); // 파일 강제 삭제
        assertThat(repo.findAll()).isEmpty();
    }

    // ══════════════════════════════════════════════════════════════════════
    // UPDATE — 비정상 조작
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("ST-U01: 없는 ID 수정 시 DataNotFoundException — 파일 변경 없음")
    void updateNonExistentIdDoesNotCorruptFile() throws IOException {
        repo.save(makeProduct("A", 1000, "cat"));
        long sizeBefore = tempFile.length();

        assertThatThrownBy(() -> repo.update(999, makeProduct("X", 9999, "cat")))
                .isInstanceOf(DataNotFoundException.class);

        assertThat(tempFile.length()).isEqualTo(sizeBefore);
        assertThat(repo.findAll()).hasSize(1); // 기존 데이터 보존
    }

    @Test
    @DisplayName("ST-U02: 음수 ID 수정 시 DataNotFoundException")
    void updateNegativeIdThrowsException() {
        assertThatThrownBy(() -> repo.update(-1, makeProduct("X", 1000, "cat")))
                .isInstanceOf(DataNotFoundException.class);
    }

    @Test
    @DisplayName("ST-U03: 수정 시 다른 ID로 덮어쓰기 불가 — 원래 ID 유지")
    void updateCannotChangeProductId() throws IOException {
        repo.save(makeProduct("A", 1000, "cat"));
        Product fake = makeProduct("사기", 1000, "cat");
        fake.setId(999); // 다른 ID 세팅 시도

        repo.update(1, fake);

        assertThat(repo.findById(1)).isPresent();    // 원래 ID 여전히 존재
        assertThat(repo.findById(999)).isEmpty();    // 가짜 ID로는 없음
    }

    // ══════════════════════════════════════════════════════════════════════
    // DELETE — 비정상 조작
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("ST-D01: 음수 ID 삭제 시 DataNotFoundException")
    void deleteNegativeIdThrowsException() {
        assertThatThrownBy(() -> repo.deleteById(-1))
                .isInstanceOf(DataNotFoundException.class);
    }

    @Test
    @DisplayName("ST-D02: 동일 ID 두 번 삭제 — 두 번째는 DataNotFoundException")
    void deleteSameIdTwiceThrowsOnSecond() throws IOException {
        repo.save(makeProduct("A", 1000, "cat"));
        repo.deleteById(1);
        assertThatThrownBy(() -> repo.deleteById(1))
                .isInstanceOf(DataNotFoundException.class);
    }

    @Test
    @DisplayName("ST-D03: 없는 ID 삭제 시 기존 데이터 무결성 유지")
    void deleteNonExistentDoesNotCorruptExistingData() throws IOException {
        repo.save(makeProduct("A", 1000, "cat"));
        repo.save(makeProduct("B", 2000, "cat"));

        assertThatThrownBy(() -> repo.deleteById(999))
                .isInstanceOf(DataNotFoundException.class);

        assertThat(repo.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("ST-D04: 전체 삭제 후 findAll — 빈 리스트 반환")
    void deleteAllItemsResultsInEmptyList() throws IOException {
        repo.save(makeProduct("A", 1000, "cat"));
        repo.save(makeProduct("B", 2000, "cat"));
        repo.deleteById(1);
        repo.deleteById(2);
        assertThat(repo.findAll()).isEmpty();
    }

    // ══════════════════════════════════════════════════════════════════════
    // 파일 손상 시나리오
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("ST-F01: 파일이 빈 내용일 때 findAll — dataFile.length()==0 체크로 빈 리스트 반환 (Jackson 파싱 우회)")
    void findAllOnEmptyFileReturnsEmptyList() throws IOException {
        Files.writeString(tempFile.toPath(), "", StandardCharsets.UTF_8);
        assertThat(repo.findAll()).isEmpty();
    }

    @Test
    @DisplayName("ST-F02: 파일이 완전히 깨진 JSON일 때 findAll — IOException 발생")
    void findAllOnCorruptedJsonThrowsException() throws IOException {
        Files.writeString(tempFile.toPath(), "{ INVALID JSON [[[", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> repo.findAll())
                .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("ST-F03: 파일이 JSON 객체 (배열 아님)일 때 findAll — MismatchedInputException 발생")
    void findAllOnObjectInsteadOfArrayThrowsException() throws IOException {
        Files.writeString(tempFile.toPath(), "{\"id\":1,\"name\":\"노트북\"}", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> repo.findAll())
                .isInstanceOf(MismatchedInputException.class);
    }

    @Test
    @DisplayName("ST-F04: 파일이 숫자만 있을 때 findAll — IOException 발생")
    void findAllOnNumericJsonThrowsException() throws IOException {
        Files.writeString(tempFile.toPath(), "12345", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> repo.findAll())
                .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("ST-F05: 파일 읽기 전용으로 변경 후 save — IOException 발생")
    void saveOnReadOnlyFileThrowsException() throws IOException {
        repo.save(makeProduct("기존", 1000, "cat")); // 한 번 정상 저장
        tempFile.setReadOnly();
        try {
            assertThatThrownBy(() -> repo.save(makeProduct("신규", 2000, "cat")))
                    .isInstanceOf(IOException.class);
        } finally {
            tempFile.setWritable(true); // tearDown을 위해 복원
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 연속/복합 시나리오
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("ST-X01: Create 후 즉시 Update 후 즉시 Delete — 최종 빈 리스트")
    void rapidCreateUpdateDeleteResultsInEmptyList() throws IOException {
        Product saved = repo.save(makeProduct("A", 1000, "cat"));
        repo.update(saved.getId(), makeProduct("A-수정", 2000, "cat"));
        repo.deleteById(saved.getId());
        assertThat(repo.findAll()).isEmpty();
    }

    @Test
    @DisplayName("ST-X02: 같은 이름 중복 등록 허용 — 별개 ID 부여")
    void duplicateNamesAreAllowed() throws IOException {
        repo.save(makeProduct("동일이름", 1000, "cat"));
        repo.save(makeProduct("동일이름", 1000, "cat"));
        List<Product> all = repo.findAll();
        assertThat(all).hasSize(2);
        assertThat(all.get(0).getId()).isNotEqualTo(all.get(1).getId());
    }

    @Test
    @DisplayName("ST-X03: 삭제 후 재등록 시 ID 재사용 없음 (단조 증가)")
    void idNeverReusedAfterDelete() throws IOException {
        repo.save(makeProduct("A", 1000, "cat")); // ID 1
        repo.save(makeProduct("B", 2000, "cat")); // ID 2
        repo.deleteById(1);
        Product reInserted = repo.save(makeProduct("C", 3000, "cat")); // ID 3 (1 재사용 안 함)
        assertThat(reInserted.getId()).isEqualTo(3);
        assertThat(repo.findById(1)).isEmpty();
    }
}
