package com.reviewer.poc.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.reviewer.poc.JsonService;
import com.reviewer.poc.exception.DataNotFoundException;
import com.reviewer.poc.model.Product;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProductRepository {

    private final JsonService jsonService;
    private final File dataFile;

    public ProductRepository(JsonService jsonService, String dataFilePath) {
        this.jsonService = jsonService;
        this.dataFile = new File(dataFilePath);
    }

    // ── Create ───────────────────────────────────────────────────────────
    public Product save(Product product) throws IOException {
        List<Product> products = findAll();
        int nextId = products.stream()
                .mapToInt(Product::getId)
                .max()
                .orElse(0) + 1;
        product.setId(nextId);
        products.add(product);
        flush(products);
        return product;
    }

    // ── Read: 전체 목록 ──────────────────────────────────────────────────
    public List<Product> findAll() throws IOException {
        if (!dataFile.exists() || dataFile.length() == 0) {
            return new ArrayList<>();
        }
        return jsonService.parseListFromFile(dataFile, new TypeReference<>() {});
    }

    // ── Read: ID 검색 ────────────────────────────────────────────────────
    public Optional<Product> findById(int id) throws IOException {
        return findAll().stream()
                .filter(p -> p.getId() == id)
                .findFirst();
    }

    // ── Read: 이름 키워드 검색 ───────────────────────────────────────────
    public List<Product> findByName(String keyword) throws IOException {
        String lower = keyword.toLowerCase();
        return findAll().stream()
                .filter(p -> p.getName().toLowerCase().contains(lower))
                .toList();
    }

    // ── Read: 카테고리 검색 ──────────────────────────────────────────────
    public List<Product> findByCategory(String category) throws IOException {
        String lower = category.toLowerCase();
        return findAll().stream()
                .filter(p -> p.getCategory().toLowerCase().contains(lower))
                .toList();
    }

    // ── Update ───────────────────────────────────────────────────────────
    public Product update(int id, Product updated) throws IOException {
        List<Product> products = findAll();
        boolean found = false;
        for (int i = 0; i < products.size(); i++) {
            if (products.get(i).getId() == id) {
                updated.setId(id);
                products.set(i, updated);
                found = true;
                break;
            }
        }
        if (!found) {
            throw new DataNotFoundException("ID " + id + " 에 해당하는 상품을 찾을 수 없습니다.");
        }
        flush(products);
        return updated;
    }

    // ── Delete ───────────────────────────────────────────────────────────
    public void deleteById(int id) throws IOException {
        List<Product> products = findAll();
        boolean removed = products.removeIf(p -> p.getId() == id);
        if (!removed) {
            throw new DataNotFoundException("ID " + id + " 에 해당하는 상품을 찾을 수 없습니다.");
        }
        flush(products);
    }

    private void flush(List<Product> products) throws IOException {
        jsonService.saveToFile(products, dataFile);
    }
}
