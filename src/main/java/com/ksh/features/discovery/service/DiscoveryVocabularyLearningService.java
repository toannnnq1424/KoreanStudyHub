package com.ksh.features.discovery.service;

import com.ksh.features.discovery.dictionary.DictionaryEntry;
import com.ksh.features.discovery.dictionary.KoreanDictionaryClient;
import com.ksh.features.discovery.dto.DiscoveryLearningDtos.DictionaryLookup;
import com.ksh.features.discovery.dto.DiscoveryLearningDtos.SaveVocabularyRequest;
import com.ksh.features.discovery.dto.DiscoveryLearningDtos.SaveVocabularyResult;
import com.ksh.features.discovery.entity.NewsArticle;
import com.ksh.features.discovery.entity.NewsArticleStatus;
import com.ksh.features.discovery.entity.NewsVocabulary;
import com.ksh.features.discovery.repository.NewsArticleRepository;
import com.ksh.features.discovery.repository.NewsVocabularyRepository;
import com.ksh.features.flashcards.entity.Flashcard;
import com.ksh.features.flashcards.entity.FlashcardDeck;
import com.ksh.features.flashcards.repository.FlashcardDeckRepository;
import com.ksh.features.flashcards.repository.FlashcardRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class DiscoveryVocabularyLearningService {

    private static final Pattern HANGUL =
            Pattern.compile("[\\u1100-\\u11ff\\u3130-\\u318f\\uac00-\\ud7af]");
    private static final Pattern EDGE_PUNCTUATION =
            Pattern.compile("^[\\p{P}\\p{S}\\s]+|[\\p{P}\\p{S}\\s]+$");
    private static final Logger log =
            LoggerFactory.getLogger(DiscoveryVocabularyLearningService.class);

    private final KoreanDictionaryClient dictionaryClient;
    private final NewsArticleRepository articleRepository;
    private final NewsVocabularyRepository vocabularyRepository;
    private final FlashcardDeckRepository deckRepository;
    private final FlashcardRepository cardRepository;

    public DiscoveryVocabularyLearningService(
            KoreanDictionaryClient dictionaryClient,
            NewsArticleRepository articleRepository,
            NewsVocabularyRepository vocabularyRepository,
            FlashcardDeckRepository deckRepository,
            FlashcardRepository cardRepository
    ) {
        this.dictionaryClient = dictionaryClient;
        this.articleRepository = articleRepository;
        this.vocabularyRepository = vocabularyRepository;
        this.deckRepository = deckRepository;
        this.cardRepository = cardRepository;
    }

    @Transactional(readOnly = true)
    public DictionaryLookup lookup(Long articleId, String rawWord) {
        requireArticle(articleId);
        String word = normalizeWord(rawWord);
        Optional<DictionaryEntry> entry = resolveEntry(articleId, word);
        if (entry.isEmpty()) {
            return new DictionaryLookup(
                    false,
                    dictionaryClient.isConfigured(),
                    word,
                    null,
                    null,
                    null,
                    null
            );
        }
        DictionaryEntry value = entry.get();
        return new DictionaryLookup(
                true,
                dictionaryClient.isConfigured(),
                value.word(),
                value.pronunciation(),
                value.meaningVi(),
                value.partOfSpeech(),
                value.dictionaryUrl()
        );
    }

    @Transactional
    public SaveVocabularyResult save(
            Long articleId,
            Long userId,
            SaveVocabularyRequest request
    ) {
        NewsArticle article = requireArticle(articleId);
        String word = normalizeWord(request == null ? null : request.word());
        DictionaryEntry resolved = resolveEntry(articleId, word).orElse(null);

        String meaning = conciseMeaning(resolved == null
                ? normalizeRequired(request == null ? null : request.meaningVi(), "Nhập nghĩa tiếng Việt trước khi lưu")
                : resolved.meaningVi());
        String pronunciation = resolved == null
                ? normalizeOptional(request.pronunciation(), 180)
                : resolved.pronunciation();
        String partOfSpeech = resolved == null
                ? normalizeOptional(request.partOfSpeech(), 80)
                : resolved.partOfSpeech();
        String dictionaryUrl = resolved == null
                ? safeDictionaryUrl(request.dictionaryUrl())
                : resolved.dictionaryUrl();

        if (request == null || request.deckId() == null) {
            throw new IllegalArgumentException("Hãy chọn bộ thẻ muốn lưu.");
        }
        FlashcardDeck deck = deckRepository.findById(request.deckId())
                .filter(value -> value.getOwnerId().equals(userId))
                .orElseThrow(() -> new EntityNotFoundException(
                        "Không tìm thấy bộ thẻ thuộc tài khoản của bạn."));

        Optional<Flashcard> existing =
                cardRepository.findFirstByDeckIdAndFrontText(deck.getId(), word);
        if (existing.isPresent()) {
            return result(deck, existing.get(), true);
        }

        int sortOrder = Math.toIntExact(cardRepository.countByDeckId(deck.getId()));
        Flashcard card = cardRepository.save(new Flashcard(
                deck.getId(),
                word,
                meaning,
                sortOrder
        ));
        return result(deck, card, false);
    }

    private Optional<DictionaryEntry> resolveEntry(Long articleId, String word) {
        Optional<NewsVocabulary> cached =
                vocabularyRepository.findFirstByArticleIdAndKoreanWord(articleId, word);
        if (cached.isPresent()) {
            NewsVocabulary value = cached.get();
            return Optional.of(new DictionaryEntry(
                    value.getTargetCode(),
                    value.getKoreanWord(),
                    value.getPronunciation(),
                    value.getPartOfSpeech(),
                    value.getWordLevel(),
                    value.getMeaningVi(),
                    value.getDictionaryUrl()
            ));
        }
        if (!dictionaryClient.isConfigured()) {
            return Optional.empty();
        }
        try {
            return dictionaryClient.lookupVietnamese(word);
        } catch (RuntimeException exception) {
            log.warn("Dictionary lookup failed for selected word '{}'", word, exception);
            return Optional.empty();
        }
    }

    private NewsArticle requireArticle(Long articleId) {
        return articleRepository.findByIdAndStatus(articleId, NewsArticleStatus.PUBLISHED)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy bản tin"));
    }

    private static String normalizeWord(String value) {
        String word = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        word = EDGE_PUNCTUATION.matcher(word).replaceAll("");
        if (word.isBlank() || !HANGUL.matcher(word).find()) {
            throw new IllegalArgumentException("Hãy chọn một từ hoặc cụm từ tiếng Hàn");
        }
        if (word.length() > 120) {
            throw new IllegalArgumentException("Cụm từ đã chọn quá dài");
        }
        return word;
    }

    private static String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value, 1000);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String conciseMeaning(String value) {
        int separator = value.indexOf(" — ");
        return separator > 0 ? value.substring(0, separator).trim() : value;
    }

    private static String normalizeOptional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().replaceAll("\\s+", " ");
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength);
    }

    private static String safeDictionaryUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.startsWith("https://krdict.korean.go.kr/") ? normalized : null;
    }

    private static SaveVocabularyResult result(
            FlashcardDeck deck,
            Flashcard card,
            boolean alreadySaved
    ) {
        return new SaveVocabularyResult(
                deck.getId(),
                card.getId(),
                deck.getTitle(),
                "/my/flashcards/" + deck.getId(),
                alreadySaved
        );
    }
}
