package com.reviewer.poc.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Product {

    private int id;
    private String name;
    private double price;
    private String category;
    private boolean inStock;
    private List<String> tags;

    @JsonProperty("in_stock")
    public boolean isInStock() { return inStock; }
    public void setInStock(boolean inStock) { this.inStock = inStock; }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    @Override
    public String toString() {
        return "Product{id=" + id + ", name='" + name + "', price=" + price
                + ", category='" + category + "', inStock=" + inStock + ", tags=" + tags + "}";
    }
}
