package com.ksh.features.practice.manage.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.entities.PracticeDraft;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.practice.manage.service.PracticeDraftContractService;
import com.ksh.features.practice.manage.validator.PracticeDraftValidator;
import com.ksh.features.practice.repository.PracticeDraftRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Local/disposable-DB importer for the canonical TOPIK 35 seed bundle.
 *
 * <p>The only persisted target is an owned {@link PracticeDraft}. The service
 * never publishes a set, allocates an immutable published version, stores an
 * asset, or invokes an AI/provider port. A blocked package therefore produces
 * no write at all.</p>
 */
@Service
public class PracticeTopik35CandidateImporter {

    public static final String IMPORTER_VERSION =
            "practice-topik35-candidate-importer-v1";
    public static final String CREATION_METHOD = "CANONICAL_SEED";
    public static final String BUNDLE_ID = "topik35-v1";
    public static final String TEST_SEED_KEY = "topik35-v1-test-1";
    public static final String TIMING_VERIFIED =
            "MANUAL_AUDIO_QA_VERIFIED";

    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern LOGICAL_KEY = Pattern.compile(
            "^practice-seed/topik35-v1/(source/(document|audio|image)|"
                    + "derived/(audio-mp3|page-image|transcript)|review/artifact)/"
                    + "[0-9a-f]{64}\\.[a-z0-9]{1,10}$");
    private static final List<String> PACKAGE_FILES = List.of(
            "practice-topik35-canonical-seed-bundle.json",
            "practice-topik35-listening-import-package.json",
            "practice-topik35-listening-question-payload.json",
            "practice-topik35-listening-transcript-payload.json",
            "practice-topik35-listening-audio-qa.json",
            "practice-topik35-reading-question-payload.json",
            "practice-topik35-writing-import-audit.json");
    private static final Map<String, String> PACKAGE_SCHEMAS = Map.of(
            "practice-topik35-canonical-seed-bundle.json",
            "practice-topik35-canonical-seed-bundle-v1",
            "practice-topik35-listening-import-package.json",
            "practice-topik35-listening-import-package-v1",
            "practice-topik35-listening-question-payload.json",
            "practice-topik35-listening-question-payload-v1",
            "practice-topik35-listening-transcript-payload.json",
            "practice-topik35-listening-transcript-payload-v1",
            "practice-topik35-listening-audio-qa.json",
            "practice-topik35-listening-audio-qa-v1",
            "practice-topik35-reading-question-payload.json",
            "practice-topik35-reading-question-payload-v1",
            "practice-topik35-writing-import-audit.json",
            "practice-topik35-writing-import-audit-v1");
    private static final Map<String, String> PACKAGE_IDS = Map.of(
            "practice-topik35-listening-import-package.json",
            "topik35-v1-listening-v1",
            "practice-topik35-listening-question-payload.json",
            "topik35-v1-listening-question-payload-v1",
            "practice-topik35-listening-transcript-payload.json",
            "topik35-v1-listening-transcript-payload-v1",
            "practice-topik35-listening-audio-qa.json",
            "topik35-v1-listening-audio-qa-v1",
            "practice-topik35-reading-question-payload.json",
            "topik35-v1-reading-question-payload-v1",
            "practice-topik35-writing-import-audit.json",
            "topik35-v1-writing-import-audit-v1");

    private final ObjectMapper objectMapper;
    private final PracticeDraftRepository drafts;
    private final UserRepository users;
    private final PracticeDraftContractService draftContract;
    private final PracticeDraftValidator draftValidator;

    public PracticeTopik35CandidateImporter(
            ObjectMapper objectMapper,
            PracticeDraftRepository drafts,
            UserRepository users,
            PracticeDraftContractService draftContract,
            PracticeDraftValidator draftValidator) {
        this.objectMapper = objectMapper;
        this.drafts = drafts;
        this.users = users;
        this.draftContract = draftContract;
        this.draftValidator = draftValidator;
    }

    public ImportResult dryRun(Path operationsDirectory) {
        return assess(load(operationsDirectory), ImportStatus.DRY_RUN);
    }

    /**
     * Creates or reuses exactly one disabled draft after every source gate is
     * green. Package assessment deliberately happens before actor locking so a
     * known-blocked bundle cannot mutate even a lock-visible database row.
     */
    @Transactional
    public ImportResult importCandidate(
            Path operationsDirectory, Long ownerId) {
        PackageSet packages = load(operationsDirectory);
        ImportResult assessment = assess(packages, ImportStatus.DRY_RUN);
        if (!assessment.blockers().isEmpty()) {
            return assessment.withStatus(ImportStatus.REJECTED);
        }
        if (ownerId == null || ownerId < 1) {
            throw new IllegalArgumentException("TOPIK35_IMPORT_OWNER_REQUIRED");
        }

        ObjectNode candidate = candidateDraft(packages, assessment);
        PracticeDraftContractService.NormalizedDraft normalized =
                draftContract.normalize(candidate, CREATION_METHOD);
        PracticeDraftValidator.ValidationResult validation =
                draftValidator.validate(normalized.json());
        if (validation.hasBlocking()) {
            List<String> blockers = validation.messages().stream()
                    .filter(message -> "BLOCKING".equals(message.type()))
                    .map(message -> "DRAFT_CONTRACT:" + message.code())
                    .distinct()
                    .sorted()
                    .toList();
            return assessment.withBlockers(blockers)
                    .withStatus(ImportStatus.REJECTED);
        }

        users.findByIdForUpdate(ownerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "TOPIK35_IMPORT_OWNER_NOT_FOUND"));
        PracticeDraft existing = drafts.findByOwnerIdOrderByUpdatedAtDesc(
                        ownerId).stream()
                .filter(this::isCandidateDraft)
                .filter(draft -> assessment.identityDigest().equals(
                        identityDigest(draft.getDraftJson())))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            return assessment.withCandidate(
                    ImportStatus.REUSED, existing.getId(), existing.getVersion());
        }

        PracticeDraft draft = new PracticeDraft(
                "TOPIK 35 canonical candidate",
                "Disabled local candidate; publication requires a separate release gate.",
                "GLOBAL", null, "DRAFT", ownerId, normalized.json());
        draft.setCreationMethod(CREATION_METHOD);
        draft.setDraftSchemaVersion(PracticeDraftContractService.SCHEMA_VERSION);
        PracticeDraft saved = drafts.saveAndFlush(draft);
        return assessment.withCandidate(
                ImportStatus.CREATED, saved.getId(), saved.getVersion());
    }

    PackageSet load(Path operationsDirectory) {
        Objects.requireNonNull(operationsDirectory, "operations directory");
        Map<String, PackageDocument> documents = new LinkedHashMap<>();
        for (String filename : PACKAGE_FILES) {
            Path path = operationsDirectory.resolve(filename).normalize();
            if (!path.getParent().equals(operationsDirectory.normalize())) {
                throw new IllegalArgumentException("TOPIK35_PACKAGE_PATH_INVALID");
            }
            try {
                byte[] bytes = Files.readAllBytes(path);
                JsonNode json = objectMapper.readTree(bytes);
                if (json == null || !json.isObject()) {
                    throw new IllegalArgumentException(
                            "TOPIK35_PACKAGE_NOT_OBJECT:" + filename);
                }
                documents.put(filename, new PackageDocument(
                        filename, digest(bytes), json));
            } catch (IOException exception) {
                throw new IllegalArgumentException(
                        "TOPIK35_PACKAGE_READ_FAILED:" + filename, exception);
            }
        }
        return new PackageSet(Map.copyOf(documents));
    }

    private ImportResult assess(PackageSet packages, ImportStatus status) {
        LinkedHashSet<String> blockers = new LinkedHashSet<>();
        List<String> discoveredLogicalKeys = new ArrayList<>();
        packages.documents().values().stream()
                .sorted(Comparator.comparing(PackageDocument::filename))
                .forEach(document -> validateDocument(
                        document, blockers, discoveredLogicalKeys));

        JsonNode reading = packages.json(
                "practice-topik35-reading-question-payload.json");
        JsonNode listening = packages.json(
                "practice-topik35-listening-import-package.json");
        JsonNode listeningQuestions = packages.json(
                "practice-topik35-listening-question-payload.json");
        JsonNode listeningTranscript = packages.json(
                "practice-topik35-listening-transcript-payload.json");
        JsonNode listeningAudio = packages.json(
                "practice-topik35-listening-audio-qa.json");
        JsonNode writing = packages.json(
                "practice-topik35-writing-import-audit.json");

        requireCount(reading.path("passageGroups"), 42,
                "READING_GROUP_COUNT", blockers);
        requireCount(reading.path("questions"), 50,
                "READING_QUESTION_COUNT", blockers);
        requireQuestionOwnership(reading, "passageGroups", reading, "R",
                1, 50, blockers);
        requireObjectiveProvenance(reading, "READING", blockers);

        requireCount(listening.path("groups"), 20,
                "LISTENING_GROUP_COUNT", blockers);
        requireCount(listeningQuestions.path("questions"), 50,
                "LISTENING_QUESTION_COUNT", blockers);
        requireQuestionOwnership(listening, "groups", listeningQuestions, "L",
                1, 50, blockers);
        requireObjectiveProvenance(
                listeningQuestions, "LISTENING", blockers);
        requireListeningBindings(
                listening, listeningQuestions, listeningTranscript,
                listeningAudio, blockers);
        requireListeningTiming(
                listening, listeningTranscript, listeningAudio, blockers);

        requireCount(writing.path("questions"), 4,
                "WRITING_QUESTION_COUNT", blockers);
        requireWritingOwnership(writing, blockers);
        requireWritingProvenance(writing, blockers);

        requireLoadReady(reading.path("loadPolicy"),
                "READING_LOAD_NOT_READY", blockers);
        requireLoadReady(listening.path("validationSummary"),
                "LISTENING_LOAD_NOT_READY", blockers);
        requireLoadReady(writing.path("loadPolicy"),
                "WRITING_LOAD_NOT_READY", blockers);
        if (!writing.path("qaBlockers").isEmpty()
                || !writing.path("targetContract")
                .path("candidateMaterialized").asBoolean(false)) {
            blockers.add("CANONICAL_VERSION_REFERENCES_INCOMPLETE");
        }
        packages.documents().values().forEach(document -> {
            if (hasRemainingPackageBlockers(document.json())) {
                blockers.add("PACKAGE_BLOCKERS_REMAIN:"
                        + document.filename());
            }
        });

        Set<String> uniqueLogicalKeys = new HashSet<>(discoveredLogicalKeys);
        String identityDigest = packages.identityDigest();
        return new ImportResult(
                status,
                BUNDLE_ID,
                identityDigest,
                reading.path("questions").size(),
                listeningQuestions.path("questions").size(),
                writing.path("questions").size(),
                reading.path("passageGroups").size(),
                listening.path("groups").size(),
                uniqueLogicalKeys.size(),
                List.copyOf(blockers),
                null,
                null,
                0L);
    }

    private void validateDocument(
            PackageDocument document,
            Set<String> blockers,
            List<String> logicalKeys) {
        JsonNode root = document.json();
        if (!PACKAGE_SCHEMAS.get(document.filename()).equals(
                root.path("schemaVersion").asText())) {
            blockers.add("PACKAGE_SCHEMA_MISMATCH:" + document.filename());
        }
        String packageId = PACKAGE_IDS.get(document.filename());
        if (packageId != null
                && !packageId.equals(root.path("packageId").asText())) {
            blockers.add("PACKAGE_ID_MISMATCH:" + document.filename());
        }
        if (!BUNDLE_ID.equals(root.path("bundleId").asText())) {
            blockers.add("BUNDLE_ID_MISMATCH:" + document.filename());
        }
        String raw = root.toString();
        if (raw.contains("/Users/") || raw.contains("file://")
                || raw.contains("s3://") || raw.contains("r2://")
                || raw.contains(".r2.cloudflarestorage.com")) {
            blockers.add("LOCAL_OR_DELIVERY_PATH_LEAK:" + document.filename());
        }
        walk(root, document.filename(), blockers, logicalKeys);
    }

    private void walk(JsonNode node,
                      String filename,
                      Set<String> blockers,
                      List<String> logicalKeys) {
        if (node.isObject()) {
            JsonNode sha = node.get("sha256");
            node.fields().forEachRemaining(entry -> {
                String field = entry.getKey();
                JsonNode value = entry.getValue();
                if (field.toLowerCase(java.util.Locale.ROOT)
                        .endsWith("sha256")
                        && (!value.isTextual()
                        || !SHA256.matcher(value.asText()).matches())) {
                    blockers.add("INVALID_SHA256:" + filename);
                }
                if (("logicalKey".equals(field)
                        || "audioLogicalKey".equals(field)
                        || "imageReference".equals(field))
                        && value.isTextual() && !value.asText().isBlank()) {
                    String key = value.asText();
                    if (!LOGICAL_KEY.matcher(key).matches()) {
                        blockers.add("INVALID_LOGICAL_KEY:" + filename);
                    } else {
                        logicalKeys.add(key);
                        if (sha != null && sha.isTextual()
                                && !key.contains("/" + sha.asText() + ".")) {
                            blockers.add("LOGICAL_KEY_DIGEST_MISMATCH:"
                                    + filename);
                        }
                    }
                }
                walk(value, filename, blockers, logicalKeys);
            });
        } else if (node.isArray()) {
            node.forEach(value -> walk(
                    value, filename, blockers, logicalKeys));
        }
    }

    private static void requireQuestionOwnership(
            JsonNode groupRoot,
            String groupField,
            JsonNode questionRoot,
            String prefix,
            int from,
            int to,
            Set<String> blockers) {
        Map<String, Set<Integer>> groupQuestions = new HashMap<>();
        Set<Integer> claimedQuestions = new LinkedHashSet<>();
        for (JsonNode group : groupRoot.path(groupField)) {
            String groupId = group.path("groupId").asText();
            Set<Integer> numbers = new LinkedHashSet<>();
            JsonNode explicit = group.path("questionNumbers");
            if (explicit.isArray()) {
                explicit.forEach(number -> numbers.add(number.asInt()));
            } else {
                for (int number = group.path("questionFrom").asInt();
                     number <= group.path("questionTo").asInt(); number++) {
                    numbers.add(number);
                }
            }
            if (!groupId.startsWith(prefix) || numbers.isEmpty()
                    || groupQuestions.put(groupId, numbers) != null
                    || numbers.stream().anyMatch(
                    number -> !claimedQuestions.add(number))) {
                blockers.add(prefix + "_GROUP_OWNERSHIP_INVALID");
            }
        }
        Set<Integer> seen = new LinkedHashSet<>();
        for (JsonNode question : questionRoot.path("questions")) {
            int number = question.path("questionNumber").asInt();
            String groupId = question.path("groupId").asText();
            String seedKey = question.path("seedKey").asText();
            if (!seen.add(number)
                    || !seedKey.matches("topik35-(reading|listening)-q[0-9]{2}")
                    || !groupQuestions.getOrDefault(groupId, Set.of())
                    .contains(number)) {
                blockers.add(prefix + "_QUESTION_VERSION_OWNERSHIP_INVALID");
            }
        }
        Set<Integer> expected = new LinkedHashSet<>();
        for (int number = from; number <= to; number++) expected.add(number);
        if (!seen.equals(expected) || !claimedQuestions.equals(expected)) {
            blockers.add(prefix + "_QUESTION_ORDER_INVALID");
        }
    }

    private static void requireListeningBindings(
            JsonNode importPackage,
            JsonNode questions,
            JsonNode transcripts,
            JsonNode audioQa,
            Set<String> blockers) {
        Set<String> importGroups = ids(importPackage.path("groups"), "groupId");
        Set<String> transcriptGroups = ids(transcripts.path("groups"), "groupId");
        Set<String> questionSeeds = ids(questions.path("questions"), "seedKey");
        Set<String> importQuestionSeeds = ids(
                importPackage.path("questions"), "seedKey");
        if (!importGroups.equals(transcriptGroups)
                || !questionSeeds.equals(importQuestionSeeds)
                || importGroups.size() != 20 || questionSeeds.size() != 50) {
            blockers.add("LISTENING_PACKAGE_BINDING_MISMATCH");
        }
        requireBinding(importPackage.path("questionPayloadBinding"),
                questions, blockers);
        requireBinding(importPackage.path("transcriptPayloadBinding"),
                transcripts, blockers);
        requireBinding(importPackage.path("audioQaBinding"),
                audioQa, blockers);
    }

    private static void requireBinding(
            JsonNode binding, JsonNode target, Set<String> blockers) {
        if (!binding.path("schemaVersion").asText().equals(
                target.path("schemaVersion").asText())
                || !binding.path("packageId").asText().equals(
                target.path("packageId").asText())) {
            blockers.add("LISTENING_PACKAGE_REFERENCE_MISMATCH");
        }
    }

    private static void requireListeningTiming(
            JsonNode importPackage,
            JsonNode transcripts,
            JsonNode audio,
            Set<String> blockers) {
        boolean pending = false;
        Map<String, TimingRange> importRanges = new LinkedHashMap<>();
        for (JsonNode group : importPackage.path("groups")) {
            JsonNode qa = group.path("timingQa");
            pending |= !TIMING_VERIFIED.equals(
                    qa.path("status").asText())
                    || !qa.path("startMs").canConvertToLong()
                    || !qa.path("endMs").canConvertToLong()
                    || qa.path("startMs").asLong() < 0
                    || qa.path("endMs").asLong() <= qa.path("startMs").asLong()
                    || !qa.path("firstAudibleCueMatched").asBoolean()
                    || !qa.path("finalAudibleCueMatched").asBoolean()
                    || !qa.path("repeatPlaybackAccountedFor").asBoolean()
                    || !qa.path("neighborBoundaryChecked").asBoolean()
                    || !qa.path("transcriptBoundaryChecked").asBoolean()
                    || qa.path("reviewerEvidenceId").asText().isBlank();
            importRanges.put(group.path("groupId").asText(),
                    new TimingRange(
                            qa.path("startMs").asLong(-1),
                            qa.path("endMs").asLong(-1)));
        }
        Map<String, TimingRange> transcriptRanges = new LinkedHashMap<>();
        for (JsonNode group : transcripts.path("groups")) {
            pending |= !TIMING_VERIFIED.equals(
                    group.path("timingStatus").asText())
                    || !group.path("startMs").canConvertToLong()
                    || !group.path("endMs").canConvertToLong()
                    || group.path("startMs").asLong() < 0
                    || group.path("endMs").asLong()
                    <= group.path("startMs").asLong();
            transcriptRanges.put(group.path("groupId").asText(),
                    new TimingRange(
                            group.path("startMs").asLong(-1),
                            group.path("endMs").asLong(-1)));
        }
        JsonNode manual = audio.path("manualBoundaryQa");
        pending |= !manual.path("auditoryReviewerAvailable").asBoolean();
        Map<String, TimingRange> audioRanges = new LinkedHashMap<>();
        for (JsonNode group : manual.path("groups")) {
            pending |= !TIMING_VERIFIED.equals(group.path("status").asText())
                    || !group.path("startMs").canConvertToLong()
                    || !group.path("endMs").canConvertToLong()
                    || group.path("startMs").asLong() < 0
                    || group.path("endMs").asLong()
                    <= group.path("startMs").asLong()
                    || group.path("reviewerEvidenceId").asText().isBlank();
            audioRanges.put(group.path("groupId").asText(),
                    new TimingRange(
                            group.path("startMs").asLong(-1),
                            group.path("endMs").asLong(-1)));
        }
        pending |= importPackage.path("validationSummary")
                .path("timingReadyGroupCount").asInt() != 20;
        pending |= audio.path("validationSummary")
                .path("boundaryReadyGroupCount").asInt() != 20;
        pending |= !importRanges.equals(transcriptRanges)
                || !importRanges.equals(audioRanges)
                || !orderedNonOverlapping(importRanges.values());
        if (pending) blockers.add("LISTENING_TIMING_PENDING_MANUAL_AUDIO_QA");
    }

    private static boolean orderedNonOverlapping(
            java.util.Collection<TimingRange> ranges) {
        long priorEnd = -1;
        for (TimingRange range : ranges) {
            if (range.startMs() < priorEnd || range.endMs() <= range.startMs()) {
                return false;
            }
            priorEnd = range.endMs();
        }
        return ranges.size() == 20;
    }

    private static void requireObjectiveProvenance(
            JsonNode payload, String skill, Set<String> blockers) {
        for (JsonNode question : payload.path("questions")) {
            JsonNode provenance = question.path("provenance");
            if (provenance.path("questionArtifactId").asText().isBlank()
                    || provenance.path("answerArtifactId").asText().isBlank()
                    || provenance.path("questionPdfPage").asInt() < 1
                    || provenance.path("answerPdfPage").asInt() < 1
                    || provenance.path("answerRowQuestion").asInt()
                    != question.path("questionNumber").asInt()) {
                blockers.add(skill + "_PROVENANCE_INCOMPLETE");
            }
        }
    }

    private static void requireWritingProvenance(
            JsonNode writing, Set<String> blockers) {
        JsonNode sourceBindings = writing.path("sourceBindings");
        if (!sourceBindings.path("questionDocument").isObject()
                || !sourceBindings.path("answerDocument").isObject()) {
            blockers.add("WRITING_PROVENANCE_INCOMPLETE");
        }
        for (JsonNode question : writing.path("questions")) {
            if (question.path("sourcePdfPage").asInt() < 1
                    || question.path("sourcePrintedPage").asInt() < 1
                    || question.path("answerExpectation")
                    .path("answerSourcePdfPage").asInt() < 1) {
                blockers.add("WRITING_PROVENANCE_INCOMPLETE");
            }
        }
    }

    private static boolean hasRemainingPackageBlockers(JsonNode root) {
        return nonEmptyArray(root.path("qaBlockers"))
                || nonEmptyArray(root.path("remainingLoadBlockers"));
    }

    private static boolean nonEmptyArray(JsonNode node) {
        return node.isArray() && !node.isEmpty();
    }

    private static void requireWritingOwnership(
            JsonNode writing, Set<String> blockers) {
        List<Integer> expected = List.of(51, 52, 53, 54);
        List<Integer> actual = new ArrayList<>();
        for (JsonNode question : writing.path("questions")) {
            int number = question.path("questionNumber").asInt();
            actual.add(number);
            if (!question.path("seedKey").asText()
                    .equals("topik35-writing-q" + number)
                    || !question.path("taskType").asText()
                    .equals("Q" + number)) {
                blockers.add("WRITING_QUESTION_VERSION_OWNERSHIP_INVALID");
            }
        }
        if (!actual.equals(expected)) {
            blockers.add("WRITING_QUESTION_ORDER_INVALID");
        }
    }

    private static void requireCount(JsonNode array,
                                     int expected,
                                     String code,
                                     Set<String> blockers) {
        if (!array.isArray() || array.size() != expected) blockers.add(code);
    }

    private static void requireLoadReady(
            JsonNode node, String code, Set<String> blockers) {
        if (!node.path("loadReady").asBoolean(false)) blockers.add(code);
    }

    private static Set<String> ids(JsonNode array, String field) {
        Set<String> result = new LinkedHashSet<>();
        if (array.isArray()) {
            array.forEach(node -> result.add(node.path(field).asText()));
        }
        return result;
    }

    private ObjectNode candidateDraft(
            PackageSet packages, ImportResult assessment) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", PracticeDraftContractService.SCHEMA_VERSION);
        ObjectNode document = root.putObject("document");
        document.put("title", "TOPIK 35 canonical candidate");
        document.put("description",
                "Local-only disabled candidate built from provenance-pinned packages.");
        ObjectNode seed = root.putObject("seedImport");
        seed.put("importerVersion", IMPORTER_VERSION);
        seed.put("bundleId", BUNDLE_ID);
        seed.put("identityDigest", assessment.identityDigest());
        seed.put("publicationAllowed", false);
        seed.put("immutableVersionAllocation", "PUBLISHER_GATE_ONLY");
        ArrayNode packageRefs = seed.putArray("packages");
        packages.documents().values().stream()
                .sorted(Comparator.comparing(PackageDocument::filename))
                .forEach(documentRef -> packageRefs.addObject()
                        .put("filename", documentRef.filename())
                        .put("sha256", documentRef.sha256()));

        root.putArray("tests").addObject()
                .put("clientId", TEST_SEED_KEY)
                .put("testNo", 1)
                .put("title", "TOPIK 35")
                .put("description", "R/L/W canonical candidate")
                .put("estimatedMinutes", 180);
        ArrayNode sections = root.putArray("sections");
        sections.add(readingSection(packages));
        sections.add(listeningSection(packages));
        sections.add(writingSection(packages));
        root.putArray("warnings");
        root.putArray("materials");
        return root;
    }

    private ObjectNode readingSection(PackageSet packages) {
        JsonNode payload = packages.json(
                "practice-topik35-reading-question-payload.json");
        return objectiveSection(
                payload.path("passageGroups"), payload.path("questions"),
                "READING", "R1", "topik35-v1-section-reading");
    }

    private ObjectNode listeningSection(PackageSet packages) {
        JsonNode transcript = packages.json(
                "practice-topik35-listening-transcript-payload.json");
        JsonNode payload = packages.json(
                "practice-topik35-listening-question-payload.json");
        ObjectNode section = objectiveSection(
                transcript.path("groups"), payload.path("questions"),
                "LISTENING", "L1", "topik35-v1-section-listening");
        ObjectNode delivery = section.putObject("sectionDelivery");
        delivery.put("schemaVersion", "practice-section-delivery-v1");
        ObjectNode listening = delivery.putObject("listeningDelivery");
        listening.putNull("checkAudioReference");
        listening.put("singleOrderedAudioProgram", true);
        listening.put("startOnce", true);
        listening.put("continuousPlayback", true);
        listening.put("seekAllowed", false);
        listening.put("replayAllowed", false);
        listening.put("timestampAutoNavigation", false);
        listening.put("timestampAutoHighlight", false);
        return section;
    }

    private ObjectNode objectiveSection(
            JsonNode groupSource,
            JsonNode questions,
            String skill,
            String lessonCode,
            String sectionId) {
        ObjectNode section = objectMapper.createObjectNode();
        section.put("clientId", sectionId);
        section.put("title", "READING".equals(skill) ? "읽기" : "듣기");
        section.put("skill", skill);
        section.put("testNo", 1);
        section.put("testClientId", TEST_SEED_KEY);
        section.put("lessonCode", lessonCode);
        section.put("durationMinutes", "READING".equals(skill) ? 70 : 60);
        ArrayNode groups = section.putArray("groups");
        Map<String, List<JsonNode>> byGroup = questionsByGroup(questions);
        int index = 0;
        for (JsonNode source : groupSource) {
            String groupId = source.path("groupId").asText();
            ObjectNode group = groups.addObject();
            group.put("clientId", BUNDLE_ID + "-" + groupId.toLowerCase());
            group.put("groupCode", lessonCode + "." + (++index));
            group.put("label", groupId);
            group.put("instruction", source.path("instruction").asText(""));
            ObjectNode stimulus = group.putObject("stimulus");
            stimulus.put("schemaVersion", "practice-stimulus-v2");
            stimulus.put("type", "READING".equals(skill)
                    ? "READING_PASSAGE" : "LISTENING_AUDIO");
            stimulus.put("instruction", source.path("instruction").asText(""));
            stimulus.put("passageText", source.path("passageText").asText(""));
            stimulus.put("transcriptText",
                    source.path("stimulus").path("transcriptText")
                            .asText(source.path("transcriptText").asText("")));
            stimulus.putNull("mediaReference");
            stimulus.putObject("provenance")
                    .put("source", "CANONICAL_SEED")
                    .put("groupId", groupId);
            ArrayNode targetQuestions = group.putArray("questions");
            for (JsonNode question : byGroup.getOrDefault(groupId, List.of())) {
                targetQuestions.add(objectiveQuestion(question));
            }
        }
        return section;
    }

    private ObjectNode objectiveQuestion(JsonNode source) {
        ObjectNode question = objectMapper.createObjectNode();
        question.put("clientId", source.path("seedKey").asText());
        question.put("questionNo", source.path("questionNumber").asInt());
        question.put("questionType", source.path("questionType").asText());
        question.put("prompt", source.path("prompt").asText());
        question.put("points", source.path("points").asInt(2));
        question.put("promptLanguageTag", "ko");
        question.set("questionContent", source.path("questionContent").deepCopy());
        question.set("answerSpec", source.path("answerSpec").deepCopy());
        ArrayNode options = question.putArray("options");
        source.path("questionContent").path("options")
                .forEach(option -> options.add(option.deepCopy()));
        ObjectNode strategy = question.putObject("explanationStrategy");
        strategy.put("registryVersion", "rl-explanation-strategy-registry-v2");
        strategy.put("strategyCode", "EXACT_EVIDENCE_ONLY");
        strategy.put("strategyVersion", "v1");
        question.put("explanationVi", "");
        question.put("importSource", CREATION_METHOD);
        question.set("seedProvenance", source.path("provenance").deepCopy());
        return question;
    }

    private ObjectNode writingSection(PackageSet packages) {
        JsonNode payload = packages.json(
                "practice-topik35-writing-import-audit.json");
        ObjectNode section = objectMapper.createObjectNode();
        section.put("clientId", "topik35-v1-section-writing");
        section.put("title", "쓰기");
        section.put("skill", "WRITING");
        section.put("testNo", 1);
        section.put("testClientId", TEST_SEED_KEY);
        section.put("lessonCode", "W1");
        section.put("durationMinutes", 50);
        ArrayNode groups = section.putArray("groups");
        int groupIndex = 0;
        for (JsonNode source : payload.path("questions")) {
            ObjectNode group = groups.addObject();
            int number = source.path("questionNumber").asInt();
            group.put("clientId", "topik35-v1-writing-group-" + number);
            group.put("groupCode", "W1." + (++groupIndex));
            group.put("label", "Q" + number);
            group.put("instruction", source.path("promptInstruction").asText());
            group.putObject("stimulus")
                    .put("schemaVersion", "practice-stimulus-v2")
                    .put("type", "WRITING_PROMPT")
                    .put("instruction", source.path("promptInstruction").asText())
                    .put("passageText", source.path("promptText").asText())
                    .put("transcriptText", "")
                    .putNull("mediaReference");
            ObjectNode question = group.putArray("questions").addObject();
            question.put("clientId", source.path("seedKey").asText());
            question.put("questionNo", number);
            question.put("questionType", "ESSAY");
            question.put("essayTaskType", source.path("taskType").asText());
            question.put("prompt", source.path("promptText").asText());
            question.put("points", source.path("points").asInt());
            question.put("promptLanguageTag", "ko");
            question.put("explanationVi", "");
            question.put("importSource", CREATION_METHOD);
            question.set("seedAnswerExpectation",
                    source.path("answerExpectation").deepCopy());
            question.set("seedProvenance", source.deepCopy());
            question.putArray("options");
        }
        return section;
    }

    private static Map<String, List<JsonNode>> questionsByGroup(
            JsonNode questions) {
        Map<String, List<JsonNode>> result = new LinkedHashMap<>();
        questions.forEach(question -> result.computeIfAbsent(
                question.path("groupId").asText(), ignored -> new ArrayList<>())
                .add(question));
        return result;
    }

    private boolean isCandidateDraft(PracticeDraft draft) {
        return draft != null
                && CREATION_METHOD.equals(draft.getCreationMethod())
                && "DRAFT".equals(draft.getStatus())
                && draft.getPublishedSetId() == null;
    }

    private String identityDigest(String draftJson) {
        try {
            return objectMapper.readTree(draftJson)
                    .path("seedImport").path("identityDigest").asText("");
        } catch (Exception exception) {
            return "";
        }
    }

    private static String digest(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private record TimingRange(long startMs, long endMs) {
    }

    record PackageDocument(String filename, String sha256, JsonNode json) {
    }

    record PackageSet(Map<String, PackageDocument> documents) {
        JsonNode json(String filename) {
            PackageDocument document = documents.get(filename);
            if (document == null) {
                throw new IllegalArgumentException(
                        "TOPIK35_PACKAGE_MISSING:" + filename);
            }
            return document.json();
        }

        String identityDigest() {
            StringBuilder canonical = new StringBuilder(
                    IMPORTER_VERSION).append('\n');
            documents.values().stream()
                    .sorted(Comparator.comparing(PackageDocument::filename))
                    .forEach(document -> canonical
                            .append(document.filename()).append(':')
                            .append(document.sha256()).append('\n'));
            return digest(canonical.toString()
                    .getBytes(StandardCharsets.UTF_8));
        }
    }

    public enum ImportStatus {
        DRY_RUN,
        REJECTED,
        CREATED,
        REUSED
    }

    public record ImportResult(
            ImportStatus status,
            String bundleId,
            String identityDigest,
            int readingQuestionCount,
            int listeningQuestionCount,
            int writingQuestionCount,
            int readingGroupCount,
            int listeningGroupCount,
            int logicalAssetKeyCount,
            List<String> blockers,
            Long candidateDraftId,
            Integer candidateDraftVersion,
            long providerCallCount) {

        public ImportResult {
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
        }

        ImportResult withStatus(ImportStatus next) {
            return new ImportResult(next, bundleId, identityDigest,
                    readingQuestionCount, listeningQuestionCount,
                    writingQuestionCount, readingGroupCount,
                    listeningGroupCount, logicalAssetKeyCount, blockers,
                    candidateDraftId, candidateDraftVersion,
                    providerCallCount);
        }

        ImportResult withBlockers(List<String> nextBlockers) {
            return new ImportResult(status, bundleId, identityDigest,
                    readingQuestionCount, listeningQuestionCount,
                    writingQuestionCount, readingGroupCount,
                    listeningGroupCount, logicalAssetKeyCount, nextBlockers,
                    candidateDraftId, candidateDraftVersion,
                    providerCallCount);
        }

        ImportResult withCandidate(
                ImportStatus nextStatus, Long draftId, Integer draftVersion) {
            return new ImportResult(nextStatus, bundleId, identityDigest,
                    readingQuestionCount, listeningQuestionCount,
                    writingQuestionCount, readingGroupCount,
                    listeningGroupCount, logicalAssetKeyCount, blockers,
                    draftId, draftVersion, providerCallCount);
        }
    }
}
