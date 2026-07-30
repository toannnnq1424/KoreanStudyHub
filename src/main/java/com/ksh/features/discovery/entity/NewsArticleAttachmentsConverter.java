package com.ksh.features.discovery.entity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

@Converter
public class NewsArticleAttachmentsConverter
        implements AttributeConverter<List<NewsArticleAttachment>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<NewsArticleAttachment>> LIST_TYPE =
            new TypeReference<>() {
            };

    @Override
    public String convertToDatabaseColumn(List<NewsArticleAttachment> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attachments);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Không thể mã hóa attachment bài khám phá", exception);
        }
    }

    @Override
    public List<NewsArticleAttachment> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(OBJECT_MAPPER.readValue(json, LIST_TYPE));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Không thể đọc attachment bài khám phá", exception);
        }
    }
}
