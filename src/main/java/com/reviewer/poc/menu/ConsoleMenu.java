package com.reviewer.poc.menu;

import com.reviewer.poc.exception.DataNotFoundException;
import com.reviewer.poc.model.Product;
import com.reviewer.poc.repository.ProductRepository;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

public class ConsoleMenu {

    private static final String SEPARATOR = "─".repeat(52);
    private static final String HEADER    = "═".repeat(52);

    private final ProductRepository repository;
    private final Scanner scanner;

    public ConsoleMenu(ProductRepository repository) {
        this.repository = repository;
        this.scanner = new Scanner(System.in, "UTF-8");
    }

    public void run() {
        printBanner();
        while (true) {
            printMainMenu();
            String choice = readLine("선택");
            if (choice.isEmpty() && !scanner.hasNextLine()) {
                printLine("프로그램을 종료합니다.");
                return;
            }
            switch (choice) {
                case "1" -> handleCreate();
                case "2" -> handleRead();
                case "3" -> handleUpdate();
                case "4" -> handleDelete();
                case "0" -> { printLine("프로그램을 종료합니다."); return; }
                default  -> printLine("[오류] 올바른 메뉴 번호를 입력하세요.");
            }
        }
    }

    // ── Create ───────────────────────────────────────────────────────────
    private void handleCreate() {
        printSection("상품 등록 (Create)");
        try {
            Product p = new Product();
            p.setName(readLine("상품명"));
            p.setPrice(readDouble("가격"));
            p.setCategory(readLine("카테고리"));
            p.setInStock(readBoolean("재고 여부 (y/n)"));

            String tagsInput = readLine("태그 (쉼표 구분, 없으면 Enter)");
            if (!tagsInput.isBlank()) {
                p.setTags(Arrays.stream(tagsInput.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList());
            }

            Product saved = repository.save(p);
            printLine("[완료] 등록되었습니다. (ID: " + saved.getId() + ")");
            printProduct(saved);
        } catch (IOException e) {
            printLine("[오류] 저장 실패: " + e.getMessage());
        }
    }

    // ── Read ─────────────────────────────────────────────────────────────
    private void handleRead() {
        printSection("상품 조회 (Read)");
        printLine("  1. 전체 목록 보기");
        printLine("  2. ID로 검색");
        printLine("  3. 이름 키워드로 검색");
        printLine("  4. 카테고리로 검색");
        String choice = readLine("선택");
        try {
            switch (choice) {
                case "1" -> printProducts(repository.findAll());
                case "2" -> {
                    int id = readInt("검색할 ID");
                    Optional<Product> result = repository.findById(id);
                    if (result.isPresent()) printProduct(result.get());
                    else printLine("[결과] ID " + id + " 에 해당하는 상품이 없습니다.");
                }
                case "3" -> {
                    String kw = readLine("검색 키워드");
                    printProducts(repository.findByName(kw));
                }
                case "4" -> {
                    String cat = readLine("카테고리명");
                    printProducts(repository.findByCategory(cat));
                }
                default -> printLine("[오류] 올바른 번호를 입력하세요.");
            }
        } catch (IOException e) {
            printLine("[오류] 조회 실패: " + e.getMessage());
        }
    }

    // ── Update ───────────────────────────────────────────────────────────
    private void handleUpdate() {
        printSection("상품 수정 (Update)");
        try {
            int id = readInt("수정할 상품 ID");
            Optional<Product> opt = repository.findById(id);
            if (opt.isEmpty()) {
                printLine("[오류] ID " + id + " 에 해당하는 상품이 없습니다.");
                return;
            }

            Product existing = opt.get();
            printLine("[현재 정보]");
            printProduct(existing);
            printLine("");
            printLine("수정할 필드를 선택하세요 (변경 없으면 Enter 입력):");

            Product updated = new Product();
            String name = readLine("상품명 [" + existing.getName() + "]");
            updated.setName(name.isBlank() ? existing.getName() : name);

            String priceStr = readLine("가격 [" + existing.getPrice() + "]");
            updated.setPrice(priceStr.isBlank() ? existing.getPrice() : Double.parseDouble(priceStr));

            String cat = readLine("카테고리 [" + existing.getCategory() + "]");
            updated.setCategory(cat.isBlank() ? existing.getCategory() : cat);

            String stockStr = readLine("재고 여부 (y/n) [" + (existing.isInStock() ? "y" : "n") + "]");
            updated.setInStock(stockStr.isBlank() ? existing.isInStock() : stockStr.equalsIgnoreCase("y"));

            String tagsDisplay = existing.getTags() != null ? String.join(", ", existing.getTags()) : "";
            String tagsInput = readLine("태그 (쉼표 구분) [" + tagsDisplay + "]");
            if (tagsInput.isBlank()) {
                updated.setTags(existing.getTags());
            } else {
                updated.setTags(Arrays.stream(tagsInput.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList());
            }

            repository.update(id, updated);
            printLine("[완료] 수정되었습니다.");
            printProduct(updated);
        } catch (DataNotFoundException e) {
            printLine("[오류] " + e.getMessage());
        } catch (NumberFormatException e) {
            printLine("[오류] 숫자 형식이 올바르지 않습니다.");
        } catch (IOException e) {
            printLine("[오류] 수정 실패: " + e.getMessage());
        }
    }

    // ── Delete ───────────────────────────────────────────────────────────
    private void handleDelete() {
        printSection("상품 삭제 (Delete)");
        try {
            int id = readInt("삭제할 상품 ID");
            Optional<Product> opt = repository.findById(id);
            if (opt.isEmpty()) {
                printLine("[오류] ID " + id + " 에 해당하는 상품이 없습니다.");
                return;
            }

            printLine("[삭제 대상]");
            printProduct(opt.get());
            String confirm = readLine("정말 삭제하시겠습니까? (y/n)");
            if (confirm.equalsIgnoreCase("y")) {
                repository.deleteById(id);
                printLine("[완료] ID " + id + " 상품이 삭제되었습니다.");
            } else {
                printLine("[취소] 삭제가 취소되었습니다.");
            }
        } catch (DataNotFoundException e) {
            printLine("[오류] " + e.getMessage());
        } catch (IOException e) {
            printLine("[오류] 삭제 실패: " + e.getMessage());
        }
    }

    // ── 출력 헬퍼 ────────────────────────────────────────────────────────
    private void printBanner() {
        System.out.println(HEADER);
        System.out.println("   상품 관리 CRUD 콘솔 애플리케이션");
        System.out.println("   데이터 저장소: JSON 파일");
        System.out.println(HEADER);
    }

    private void printMainMenu() {
        System.out.println("\n" + SEPARATOR);
        System.out.println("  [1] Create  - 상품 등록");
        System.out.println("  [2] Read    - 상품 조회");
        System.out.println("  [3] Update  - 상품 수정");
        System.out.println("  [4] Delete  - 상품 삭제");
        System.out.println("  [0] 종료");
        System.out.println(SEPARATOR);
    }

    private void printSection(String title) {
        System.out.println("\n" + SEPARATOR);
        System.out.println("  ▶ " + title);
        System.out.println(SEPARATOR);
    }

    private void printProduct(Product p) {
        System.out.printf("  ID: %-4d | 이름: %-12s | 가격: %,8.0f원 | 카테고리: %-10s | 재고: %s%n",
                p.getId(), p.getName(), p.getPrice(), p.getCategory(),
                p.isInStock() ? "있음" : "없음");
        if (p.getTags() != null && !p.getTags().isEmpty()) {
            System.out.println("         태그: " + String.join(", ", p.getTags()));
        }
    }

    private void printProducts(List<Product> products) {
        if (products.isEmpty()) {
            printLine("[결과] 조회된 상품이 없습니다.");
            return;
        }
        printLine("[결과] " + products.size() + "건");
        System.out.println(SEPARATOR);
        products.forEach(this::printProduct);
        System.out.println(SEPARATOR);
    }

    private void printLine(String msg) {
        System.out.println("  " + msg);
    }

    // ── 입력 헬퍼 ────────────────────────────────────────────────────────
    private String readLine(String prompt) {
        System.out.print("  " + prompt + ": ");
        if (!scanner.hasNextLine()) {
            System.out.println();
            return "";
        }
        return scanner.nextLine().trim();
    }

    private int readInt(String prompt) {
        while (true) {
            String input = readLine(prompt);
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                printLine("[오류] 숫자를 입력해주세요.");
            }
        }
    }

    private double readDouble(String prompt) {
        while (true) {
            String input = readLine(prompt);
            try {
                return Double.parseDouble(input);
            } catch (NumberFormatException e) {
                printLine("[오류] 숫자를 입력해주세요.");
            }
        }
    }

    private boolean readBoolean(String prompt) {
        String input = readLine(prompt);
        return input.equalsIgnoreCase("y");
    }
}
