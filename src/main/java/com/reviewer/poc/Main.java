package com.reviewer.poc;

import com.reviewer.poc.menu.ConsoleMenu;
import com.reviewer.poc.repository.ProductRepository;

public class Main {

    private static final String DATA_FILE = "data/products.json";

    public static void main(String[] args) {
        JsonService jsonService = new JsonService();
        ProductRepository repository = new ProductRepository(jsonService, DATA_FILE);
        ConsoleMenu menu = new ConsoleMenu(repository);
        menu.run();
    }
}
