package com.reviewer.poc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class JsonService {

    private final ObjectMapper mapper;

    public JsonService() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /** JSON 문자열 → 객체 */
    public <T> T parseFromString(String json, Class<T> type) throws IOException {
        return mapper.readValue(json, type);
    }

    /** JSON 문자열 → 리스트 */
    public <T> List<T> parseListFromString(String json, TypeReference<List<T>> typeRef) throws IOException {
        return mapper.readValue(json, typeRef);
    }

    /** JSON 파일 → 객체 */
    public <T> T parseFromFile(File file, Class<T> type) throws IOException {
        return mapper.readValue(file, type);
    }

    /** JSON 파일 → 리스트 */
    public <T> List<T> parseListFromFile(File file, TypeReference<List<T>> typeRef) throws IOException {
        return mapper.readValue(file, typeRef);
    }

    /** JSON 문자열 → 트리(JsonNode)로 읽기 (스키마 없이 탐색) */
    public JsonNode parseToTree(String json) throws IOException {
        return mapper.readTree(json);
    }

    /** 객체 → JSON 파일 저장 */
    public void saveToFile(Object data, File file) throws IOException {
        file.getParentFile().mkdirs();
        mapper.writeValue(file, data);
    }

    /** 객체 → JSON 문자열 */
    public String toJsonString(Object data) throws IOException {
        return mapper.writeValueAsString(data);
    }
}
