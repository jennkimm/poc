package com.reviewer.poc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.reviewer.poc.model.Product;

import java.io.File;
import java.util.List;

public class Main {

    public static void main(String[] args) throws Exception {
        JsonService service = new JsonService();

        // ── 1. JSON 문자열 → 단일 객체 파싱 ──────────────────────────────
        String singleJson = """
                {
                  "id": 1,
                  "name": "노트북",
                  "price": 1299000,
                  "category": "전자제품",
                  "in_stock": true,
                  "tags": ["laptop", "premium", "new"]
                }
                """;

        Product product = service.parseFromString(singleJson, Product.class);
        System.out.println("[1] 단일 객체 파싱:");
        System.out.println("    " + product);

        // ── 2. JSON 문자열 → 리스트 파싱 ─────────────────────────────────
        String listJson = """
                [
                  {"id":2,"name":"마우스","price":35000,"category":"주변기기","in_stock":true,"tags":["wireless"]},
                  {"id":3,"name":"키보드","price":89000,"category":"주변기기","in_stock":false,"tags":["mechanical","rgb"]}
                ]
                """;

        List<Product> products = service.parseListFromString(listJson, new TypeReference<>() {});
        System.out.println("\n[2] 리스트 파싱 (" + products.size() + "건):");
        products.forEach(p -> System.out.println("    " + p));

        // ── 3. JsonNode 트리 탐색 (스키마 없이 파싱) ─────────────────────
        String rawJson = """
                {"store":"Seoul","floor":3,"open":true}
                """;
        JsonNode node = service.parseToTree(rawJson);
        System.out.println("\n[3] JsonNode 트리 탐색:");
        System.out.println("    store  = " + node.path("store").asText());
        System.out.println("    floor  = " + node.path("floor").asInt());
        System.out.println("    open   = " + node.path("open").asBoolean());

        // ── 4. 객체 → JSON 파일 저장 ──────────────────────────────────────
        File outputFile = new File("output/products.json");
        service.saveToFile(products, outputFile);
        System.out.println("\n[4] 파일 저장 완료: " + outputFile.getAbsolutePath());

        // ── 5. JSON 파일 → 리스트 다시 읽기 ──────────────────────────────
        List<Product> loaded = service.parseListFromFile(outputFile, new TypeReference<>() {});
        System.out.println("\n[5] 저장된 파일 재파싱 (" + loaded.size() + "건):");
        loaded.forEach(p -> System.out.println("    " + p));

        // ── 6. 단일 객체 JSON 문자열 출력 ────────────────────────────────
        System.out.println("\n[6] 객체 → JSON 문자열:");
        System.out.println(service.toJsonString(product));
    }
}
