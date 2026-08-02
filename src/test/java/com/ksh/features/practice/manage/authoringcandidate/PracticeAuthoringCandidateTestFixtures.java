package com.ksh.features.practice.manage.authoringcandidate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ksh.entities.PracticeDraft;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceKind;
import com.ksh.features.practice.manage.authoringcandidate.PracticeAuthoringCandidateModels.SourceOperation;

import java.time.LocalDateTime;

final class PracticeAuthoringCandidateTestFixtures {

    static final String CANDIDATE_ID =
            "11111111-1111-4111-8111-111111111111";
    static final String SOURCE_DIGEST = "1".repeat(64);
    static final String CONTENT_DIGEST = "a".repeat(64);

    private PracticeAuthoringCandidateTestFixtures() {
    }

    static ArrayNode readingGroups(ObjectMapper mapper, boolean approved) {
        try {
            return (ArrayNode) mapper.readTree("""
                    [{
                      "candidateGroupId":"reading_group_1",
                      "groupOrder":1,
                      "label":"Đọc hiểu",
                      "instruction":"Đọc và chọn đáp án.",
                      "stimulus":{
                        "schemaVersion":"practice-stimulus-v2",
                        "type":"READING_PASSAGE",
                        "instruction":"Đọc và chọn đáp án.",
                        "passageText":"도서관은 아홉 시에 문을 엽니다.",
                        "transcriptText":"",
                        "mediaReference":null,
                        "provenance":{
                          "source":"QUICK_EXCEL",
                          "approved":%s,
                          "sourceRefs":[{
                            "kind":"SHEET_ROW","sourceId":"row-4",
                            "sheet":"QUICK_QUESTIONS","row":4
                          }]
                        }
                      },
                      "sourceRefs":[{
                        "kind":"SHEET_ROW","sourceId":"row-4",
                        "sheet":"QUICK_QUESTIONS","row":4
                      }],
                      "questions":[{
                        "candidateQuestionId":"reading_q_1",
                        "questionOrder":1,
                        "questionType":"SINGLE_CHOICE",
                        "prompt":"도서관은 몇 시에 문을 엽니까?",
                        "points":1,
                        "explanationVi":"Thư viện mở lúc chín giờ.",
                        "explanationStrategy":{
                          "registryVersion":"rl-explanation-strategy-registry-v2",
                          "strategyCode":"EXACT_EVIDENCE_ONLY",
                          "strategyVersion":"v1"
                        },
                        "questionContent":{
                          "schemaVersion":"question-content-v3",
                          "options":[
                            {"id":"opt_A","text":"여덟 시"},
                            {"id":"opt_B","text":"아홉 시"}
                          ],
                          "blanks":[],
                          "languageTag":"ko"
                        },
                        "answerSpec":{
                          "schemaVersion":"answer-spec-v1",
                          "questionType":"SINGLE_CHOICE",
                          "correctOptionIds":["opt_B"],
                          "correctValue":null,
                          "blanks":[],
                          "scoringPolicyCode":"ALL_OR_NOTHING"
                        },
                        "reviewState":"ACCEPTED",
                        "sourceRefs":[{
                          "kind":"SHEET_ROW","sourceId":"row-4",
                          "sheet":"QUICK_QUESTIONS","row":4
                        }]
                      }]
                    }]
                    """.formatted(approved));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    static ObjectNode candidateEnvelope(
            ObjectMapper mapper, boolean approved) {
        ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion", "practice-authoring-candidate-v1");
        root.put("candidateId", CANDIDATE_ID);
        root.put("ownerId", 101L);
        ObjectNode source = root.putObject("source");
        source.put("kind", "QUICK_EXCEL");
        source.put("contractVersion", "practice-quick-excel-v1");
        source.put("sourceDigest", "sha256:" + SOURCE_DIGEST);
        source.put("sourceRevision", "upload-1");
        ObjectNode target = root.putObject("target");
        target.put("draftId", 5001L);
        target.put("baseDraftVersion", 0);
        target.put("testNo", 1);
        target.put("skill", "READING");
        target.put("lessonCode", "R1");
        root.put("state", "READY_TO_APPLY");
        root.put("normalizerVersion",
                PracticeAuthoringCandidate.NORMALIZER_VERSION);
        root.put("validatorVersion",
                PracticeAuthoringCandidate.VALIDATOR_VERSION);
        root.set("groups", readingGroups(mapper, approved));
        root.putArray("issues");
        root.put("contentDigest", "sha256:" + CONTENT_DIGEST);
        root.put("warningsAcknowledged", true);
        root.put("createdAt", "2026-08-02T00:00:00Z");
        root.put("expiresAt", "2026-08-09T00:00:00Z");
        root.putNull("applied");
        return root;
    }

    static PracticeAuthoringCandidate readyCandidate(ObjectMapper mapper) {
        LocalDateTime created = LocalDateTime.of(2026, 8, 2, 0, 0);
        ObjectNode envelope = candidateEnvelope(mapper, true);
        String json = envelope.toString();
        PracticeAuthoringCandidate candidate = new PracticeAuthoringCandidate(
                CANDIDATE_ID, 101L, SourceKind.QUICK_EXCEL,
                "practice-quick-excel-v1", SOURCE_DIGEST, "upload-1",
                "reading.xlsx", SourceOperation.NONE,
                5001L, 1, "READING", "R1", 0,
                json, CONTENT_DIGEST, created, created.plusDays(7));
        candidate.markNormalized(json, CONTENT_DIGEST, created);
        candidate.markValidated(json, CONTENT_DIGEST, created);
        candidate.beginReview(json, created);
        candidate.markReady(json, 101L, false, created);
        return candidate;
    }

    static PracticeDraft targetDraft(int version) {
        PracticeDraft draft = new PracticeDraft(
                "Reading", "", "GLOBAL", null, "DRAFT", 101L,
                """
                {
                  "schemaVersion":"practice-draft-v3",
                  "document":{"title":"Reading"},
                  "tests":[{
                    "clientId":"test-1","testNo":1,"title":"Test 1",
                    "description":"","estimatedMinutes":null
                  }],
                  "sections":[{
                    "clientId":"section-1","testNo":1,
                    "testClientId":"test-1","lessonCode":"R1",
                    "title":"Reading","skill":"READING",
                    "durationMinutes":60,"groups":[]
                  }],
                  "warnings":[],"materials":[]
                }
                """);
        draft.setVersion(version);
        return draft;
    }
}
