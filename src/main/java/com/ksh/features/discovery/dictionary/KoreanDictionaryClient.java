package com.ksh.features.discovery.dictionary;

import com.ksh.features.discovery.ingestion.NewsHttpClient;
import com.ksh.features.dictionary.KoreanDictionarySettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

@Component
public class KoreanDictionaryClient {

    private final NewsHttpClient httpClient;
    private final KoreanDictionarySettingsService settingsService;
    private final String apiKey;
    private final String baseUrl;

    @Autowired
    public KoreanDictionaryClient(
            NewsHttpClient httpClient,
            @Value("${app.news.dictionary.api-key:}") String apiKey,
            @Value("${app.news.dictionary.base-url:https://krdict.korean.go.kr/api/search}")
            String baseUrl,
            KoreanDictionarySettingsService settingsService
    ) {
        this.httpClient = httpClient;
        this.settingsService = settingsService;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl;
    }

    KoreanDictionaryClient(NewsHttpClient httpClient, String apiKey, String baseUrl) {
        this.httpClient = httpClient;
        this.settingsService = null;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl;
    }

    public boolean isConfigured() {
        return !effectiveApiKey().isBlank();
    }

    public Optional<DictionaryEntry> lookupVietnamese(String koreanWord) {
        if (!isConfigured() || koreanWord == null || koreanWord.isBlank()) {
            return Optional.empty();
        }
        String url = UriComponentsBuilder.fromUriString(effectiveBaseUrl())
                .queryParam("key", effectiveApiKey())
                .queryParam("q", koreanWord)
                .queryParam("part", "word")
                .queryParam("method", "exact")
                .queryParam("translated", "y")
                .queryParam("trans_lang", "7")
                .queryParam("num", "10")
                .build()
                .encode()
                .toUriString();
        String xml = httpClient.get(url, MediaType.APPLICATION_XML);
        return parse(xml, koreanWord);
    }

    private String effectiveApiKey() {
        return settingsService == null ? apiKey : settingsService.apiKey(apiKey);
    }

    private String effectiveBaseUrl() {
        return settingsService == null ? baseUrl : settingsService.baseUrl(baseUrl);
    }

    Optional<DictionaryEntry> parse(String xml, String requestedWord) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setExpandEntityReferences(false);

            Document document = factory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml)));
            NodeList items = document.getElementsByTagName("item");
            Element fallback = null;
            Element selected = null;
            for (int index = 0; index < items.getLength(); index++) {
                Element item = (Element) items.item(index);
                if (fallback == null) {
                    fallback = item;
                }
                if (requestedWord.equals(text(item, "word"))) {
                    selected = item;
                    break;
                }
            }
            if (selected == null) {
                selected = fallback;
            }
            if (selected == null) {
                return Optional.empty();
            }

            Set<String> meanings = new LinkedHashSet<>();
            NodeList translations = selected.getElementsByTagName("translation");
            for (int index = 0; index < translations.getLength() && meanings.size() < 2; index++) {
                String meaning = translationMeaning((Element) translations.item(index));
                if (meaning != null) {
                    meanings.add(meaning);
                }
            }
            if (meanings.isEmpty()) {
                return Optional.empty();
            }

            String targetCode = text(selected, "target_code");
            String dictionaryUrl = text(selected, "link");
            if (dictionaryUrl == null || dictionaryUrl.isBlank()) {
                dictionaryUrl = "https://krdict.korean.go.kr/vie/dicSearch/SearchView"
                        + "?ParaWordNo=" + targetCode;
            }
            return Optional.of(new DictionaryEntry(
                    targetCode,
                    valueOr(text(selected, "word"), requestedWord),
                    text(selected, "pronunciation"),
                    text(selected, "pos"),
                    text(selected, "word_grade"),
                    String.join("; ", meanings),
                    dictionaryUrl
            ));
        } catch (Exception exception) {
            throw new IllegalStateException("Không đọc được phản hồi từ Korean Basic Dictionary", exception);
        }
    }

    private static String text(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        String value = nodes.item(0).getTextContent();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String translationMeaning(Element translation) {
        String translatedWord = text(translation, "trans_word");
        String definition = text(translation, "trans_dfn");
        if (translatedWord == null) {
            return definition;
        }
        if (definition == null) {
            return translatedWord;
        }
        if (definition.equalsIgnoreCase(translatedWord)) {
            return translatedWord;
        }
        return translatedWord + " — " + definition;
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
