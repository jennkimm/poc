package com.reviewer.poc.integration;

import com.reviewer.poc.JsonService;
import com.reviewer.poc.menu.ConsoleMenu;
import com.reviewer.poc.repository.ProductRepository;
import org.junit.jupiter.api.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.*;

/**
 * ConsoleMenu 통합 테스트 — SubAgent3에서 발견된 UI 레이어 미테스트 영역 커버.
 * System.setIn/setOut 을 이용해 stdin/stdout 을 시뮬레이션.
 *
 * 주의: ConsoleMenu 생성자가 new Scanner(System.in) 을 호출하므로
 *       반드시 System.setIn() 을 ConsoleMenu 생성 *전* 에 수행해야 한다.
 */
@DisplayName("ConsoleMenu 통합 테스트")
class ConsoleMenuIntegrationTest {

    private static final PrintStream ORIGINAL_OUT = System.out;
    private static final InputStream ORIGINAL_IN  = System.in;

    private File tempFile;
    private ByteArrayOutputStream capturedOut;

    @BeforeEach
    void setUp() throws IOException {
        tempFile = Files.createTempFile("console_menu_test_", ".json").toFile();
        tempFile.deleteOnExit();

        capturedOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOut, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void tearDown() {
        System.setIn(ORIGINAL_IN);
        System.setOut(ORIGINAL_OUT);
        tempFile.delete();
    }

    /** stdin 문자열을 설정하고 ConsoleMenu 를 생성해 run() 을 실행. */
    private String runMenu(String input) {
        System.setIn(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
        ProductRepository repo = new ProductRepository(new JsonService(), tempFile.getAbsolutePath());
        new ConsoleMenu(repo).run();
        return capturedOut.toString(StandardCharsets.UTF_8);
    }

    // ══════════════════════════════════════════════════════════════════════
    // CM-01: 종료 메뉴 (0 입력)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CM-01: 메뉴 0 입력 → 프로그램 종료 메시지 출력")
    void menuZeroExitsProgram() {
        String out = runMenu("0\n");
        assertThat(out).contains("프로그램을 종료합니다");
    }

    // ══════════════════════════════════════════════════════════════════════
    // CM-02: EOF/빈 stdin → 자동 종료
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CM-02: stdin EOF (빈 입력) → 프로그램 종료")
    void eofExitsProgram() {
        String out = runMenu("");
        assertThat(out).contains("프로그램을 종료합니다");
    }

    // ══════════════════════════════════════════════════════════════════════
    // CM-03: 잘못된 메뉴 번호 입력
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CM-03: 잘못된 메뉴 번호 입력 → 오류 메시지 후 계속")
    void invalidMenuChoiceShowsError() {
        String out = runMenu("9\n0\n");
        assertThat(out).contains("[오류] 올바른 메뉴 번호를 입력하세요");
    }

    // ══════════════════════════════════════════════════════════════════════
    // CM-04: Create 성공 경로
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CM-04: 메뉴 1 (Create) → 상품 등록 성공 후 ID 출력")
    void createProductSuccess() {
        String input = "1\n노트북\n1299000\n전자제품\ny\nlaptop, premium\n0\n";
        String out = runMenu(input);
        assertThat(out).contains("[완료] 등록되었습니다");
        assertThat(out).contains("ID:");
        assertThat(out).contains("노트북");
    }

    // ══════════════════════════════════════════════════════════════════════
    // CM-05: Create — 가격에 음수 입력 후 재입력
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CM-05: Create 가격에 음수 입력 → 오류 후 유효한 값으로 재입력 성공")
    void createNegativePriceRetry() {
        // 음수 → 오류 → 양수 재입력
        String input = "1\n마우스\n-100\n35000\n주변기기\ny\n\n0\n";
        String out = runMenu(input);
        assertThat(out).contains("[오류] 가격은 0 이상이어야 합니다");
        assertThat(out).contains("[완료] 등록되었습니다");
    }

    // ══════════════════════════════════════════════════════════════════════
    // CM-06: Create — 가격에 문자 입력 후 재입력
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CM-06: Create 가격에 문자 입력 → 숫자 오류 후 재입력 성공")
    void createInvalidPriceStringRetry() {
        String input = "1\n키보드\nabc\n55000\n주변기기\nn\n\n0\n";
        String out = runMenu(input);
        assertThat(out).contains("[오류] 숫자를 입력해주세요");
        assertThat(out).contains("[완료] 등록되었습니다");
    }

    // ══════════════════════════════════════════════════════════════════════
    // CM-07: Read — 전체 목록 (비어있음)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CM-07: Read 전체 목록 — 비어있을 때 조회된 상품 없음 메시지")
    void readAllEmpty() {
        String out = runMenu("2\n1\n0\n");
        assertThat(out).contains("[결과] 조회된 상품이 없습니다");
    }

    // ══════════════════════════════════════════════════════════════════════
    // CM-08: Read — 전체 목록 (데이터 있음)
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CM-08: Read 전체 목록 — 등록 후 조회 시 건수 출력")
    void readAllAfterCreate() {
        String input = "1\n노트북\n1299000\n전자제품\ny\n\n"  // Create
                     + "2\n1\n"                               // Read > 전체
                     + "0\n";
        String out = runMenu(input);
        assertThat(out).contains("[결과] 1건");
    }

    // ══════════════════════════════════════════════════════════════════════
    // CM-09: Read — ID 검색 성공
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CM-09: Read ID 검색 — 존재하는 ID 검색 시 상품 출력")
    void readById() {
        String input = "1\n노트북\n1299000\n전자제품\ny\n\n"  // Create (ID=1)
                     + "2\n2\n1\n"                            // Read > ID 검색
                     + "0\n";
        String out = runMenu(input);
        assertThat(out).contains("노트북");
        assertThat(out).doesNotContain("해당하는 상품이 없습니다");
    }

    // ══════════════════════════════════════════════════════════════════════
    // CM-10: Read — 존재하지 않는 ID 검색
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CM-10: Read ID 검색 — 존재하지 않는 ID → 없음 메시지")
    void readByIdNotFound() {
        String out = runMenu("2\n2\n999\n0\n");
        assertThat(out).contains("에 해당하는 상품이 없습니다");
    }

    // ══════════════════════════════════════════════════════════════════════
    // CM-11: Read — 이름 키워드 검색
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CM-11: Read 이름 검색 — 부분 일치 키워드로 결과 반환")
    void readByKeyword() {
        String input = "1\n노트북\n1299000\n전자제품\ny\n\n"  // Create
                     + "2\n3\n노트\n"                         // Read > 이름 검색
                     + "0\n";
        String out = runMenu(input);
        assertThat(out).contains("노트북");
    }

    // ══════════════════════════════════════════════════════════════════════
    // CM-12: Update 성공 경로
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CM-12: Update — 기존 상품 이름 수정 후 완료 메시지")
    void updateProductName() {
        // Create → Update (이름만 변경, 나머지 Enter 유지)
        String input = "1\n노트북\n1299000\n전자제품\ny\n\n"  // Create (ID=1)
                     + "3\n1\n새노트북\n\n\n\n\n"             // Update (이름만 변경)
                     + "0\n";
        String out = runMenu(input);
        assertThat(out).contains("[완료] 수정되었습니다");
    }

    // ══════════════════════════════════════════════════════════════════════
    // CM-13: Update — 존재하지 않는 ID
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CM-13: Update — 존재하지 않는 ID → 오류 메시지")
    void updateNotFoundId() {
        String out = runMenu("3\n999\n0\n");
        assertThat(out).contains("에 해당하는 상품이 없습니다");
    }

    // ══════════════════════════════════════════════════════════════════════
    // CM-14: Delete 성공 경로
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CM-14: Delete — 상품 등록 후 삭제 확인(y) → 삭제 완료")
    void deleteProductConfirmed() {
        String input = "1\n노트북\n1299000\n전자제품\ny\n\n"  // Create (ID=1)
                     + "4\n1\ny\n"                            // Delete > confirm y
                     + "0\n";
        String out = runMenu(input);
        assertThat(out).contains("[완료]");
        assertThat(out).contains("삭제되었습니다");
    }

    // ══════════════════════════════════════════════════════════════════════
    // CM-15: Delete 취소 경로
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CM-15: Delete — 삭제 확인에 n 입력 → 취소 메시지")
    void deleteProductCancelled() {
        String input = "1\n노트북\n1299000\n전자제품\ny\n\n"  // Create (ID=1)
                     + "4\n1\nn\n"                            // Delete > confirm n
                     + "0\n";
        String out = runMenu(input);
        assertThat(out).contains("[취소] 삭제가 취소되었습니다");
    }

    // ══════════════════════════════════════════════════════════════════════
    // CM-16: Delete — 존재하지 않는 ID
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CM-16: Delete — 존재하지 않는 ID → 없음 메시지")
    void deleteNotFoundId() {
        String out = runMenu("4\n999\n0\n");
        assertThat(out).contains("에 해당하는 상품이 없습니다");
    }

    // ══════════════════════════════════════════════════════════════════════
    // CM-17: Read > 잘못된 서브메뉴 번호
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CM-17: Read 서브메뉴에 잘못된 번호 입력 → 오류 메시지")
    void readSubMenuInvalidChoice() {
        String out = runMenu("2\n9\n0\n");
        assertThat(out).contains("[오류] 올바른 번호를 입력하세요");
    }
}
