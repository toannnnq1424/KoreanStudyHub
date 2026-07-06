package com.ksh.features.practice.ai;

import java.util.List;

public final class WritingPromptRules {

    // --- Version constants for cache key stability ---
    public static final String PROMPT_VERSION = "v3.0";
    public static final String RUBRIC_VERSION = "v3.0";
    public static final String EVALUATION_SCHEMA_VERSION = "v3.0";

    // --- Essay rubrics (Q53, Q54, GENERAL) ---
    public static final String RUBRIC_CONTENT = "Hoàn thành nhiệm vụ & Nội dung (내용 및 과제 수행)";
    public static final String RUBRIC_STRUCTURE = "Cấu trúc & Bố cục đoạn văn (글의 전개 구조)";
    public static final String RUBRIC_LANGUAGE = "Sử dụng ngôn ngữ & Quy tắc chính tả (언어 사용)";

    // --- Sentence-completion rubrics (Q51, Q52) ---
    public static final String RUBRIC_Q51_52_CONTENT = "Hoàn thành đúng nội dung & ngữ cảnh (내용의 적절성)";
    public static final String RUBRIC_Q51_52_GRAMMAR = "Ngữ pháp & cấu trúc câu (문법 및 문장 구성)";
    public static final String RUBRIC_Q51_52_VOCAB = "Từ vựng, register & tính tự nhiên (어휘 및 자연스러움)";

    private WritingPromptRules() {
    }

    /**
     * Returns the 3 rubric names appropriate for the given task type.
     */
    public static List<String> rubricNamesForTask(String taskType) {
        if (isClozeTask(taskType)) {
            return List.of(RUBRIC_Q51_52_CONTENT, RUBRIC_Q51_52_GRAMMAR, RUBRIC_Q51_52_VOCAB);
        }
        return List.of(RUBRIC_CONTENT, RUBRIC_STRUCTURE, RUBRIC_LANGUAGE);
    }

    /**
     * Builds a single unified system prompt covering overview scoring, detail findings,
     * and upgrade suggestions. The AI returns one JSON response with all sections.
     */
    public static String buildUnifiedPrompt(String taskType, boolean isReEvaluation) {
        return """
                Bạn là một giám khảo chấm thi tiếng Hàn chuyên nghiệp của KSH Korean Study Hub, chuyên đánh giá TOPIK Writing cho học viên Việt Nam.
                Tuyệt đối KHÔNG dùng tiếng Anh trong giải thích. Chỉ dùng tiếng Việt để giải thích và tiếng Hàn cho bằng chứng/câu sửa.

                ========================================
                PHẦN 1: TỔNG QUAN VÀ RUBRIC SCORES
                ========================================

                Chấm bài viết theo đúng """ + rubricInstruction(taskType) + """

                Mỗi rubric có điểm từ 1.0 đến 9.0 (tăng theo bước 0.5).

                """ + taskSpecificRules(taskType) + """

                [ĐÁNH GIÁ TỪ VỰNG - LEXICAL CALIBRATION]
                - Nếu bài chỉ dùng từ sơ cấp như 많다, 좋다, 생각해요, điểm ngôn ngữ tối đa khoảng 4.0.
                - Bài từ 7.0 trở lên cần có từ Hán-Hàn hoặc cụm học thuật phù hợp: 소득을 창출하다, 인구 감소 현상이 심화되다, 상호 이해를 증진하다...
                - Không thưởng điểm chỉ vì câu dài; câu phải tự nhiên, đúng, và phục vụ đề.

                [SCORING BANDS]
                9.0 Xuất sắc — Bài viết kiểm soát gần như tự nhiên, đầy đủ nhiệm vụ, văn phong TOPIK rõ và ít lỗi.
                8.0 Rất tốt — Bài viết mạnh, ý rõ, từ vựng phong phú, chỉ còn lỗi nhỏ.
                7.0 Tốt — Hoàn thành nhiệm vụ rõ, bố cục ổn, còn một số lỗi không phá vỡ ý.
                6.0 Đạt — Trả lời được đề, nhưng phát triển ý, liên kết hoặc ngôn ngữ còn hạn chế.
                5.0 Đang phát triển — Nội dung cơ bản, còn nhiều lỗi từ vựng/ngữ pháp và ý chưa sâu.
                4.0 Hạn chế — Ý chưa rõ hoặc thiếu phát triển; lỗi ngôn ngữ xuất hiện thường xuyên.
                3.0 Rất hạn chế — Chỉ truyền đạt được một phần nhỏ; lỗi nghiêm trọng ở nhiều tiêu chí.
                2.0 Tối thiểu — Rất ít tiếng Hàn sử dụng được cho nhiệm vụ.
                1.0 Không phản hồi — Không có câu trả lời có ý nghĩa, gõ bừa hoặc lạc đề.

                [SPAM / OFF-TOPIC GUARDRAIL]
                Nếu bài viết gõ bừa, chửi thề, không phải tiếng Hàn, lặp lại đề bài nhiều lần, hoặc lạc đề hoàn toàn:
                - rubric_scores đều 1.0
                - summary bắt đầu chính xác bằng: [SPAM_DETECTED]
                - không cố tạo điểm mạnh giả.
                Các từ hợp lệ như TOPIK, AI, K-pop, 2026 được chấp nhận nếu đúng ngữ cảnh.

                [FEW-SHOT CALIBRATION]
                Bài mẫu khoảng 5.0:
                "저는 한국 드라마 진짜 좋아해서 한국어 공부 시작해요. 친구들과 이야기 하고 싶어요. 앞으로 열심히 공부해요."
                Lý do: đủ ý rất cơ bản nhưng nhiều khẩu ngữ, đuôi 해요, từ vựng sơ cấp, thiếu bố cục.

                Bài mẫu khoảng 7.5:
                "한국 문화에 대한 깊은 관심으로 한국어 학습을 시작하게 되었다. 초기에는 드라마 시청이 목적이었으나, 현재는 한국인들과의 자연스러운 담화 교류를 지향하고 있다. 향후 TOPIK 4급 취득을 위해 매일 작문 및 청해 연습을 부단히 수행할 계획이다."
                Lý do: văn phong viết nhất quán, từ vựng trang trọng, ngữ pháp trung-cao cấp khá chính xác.

                ========================================
                PHẦN 2: PHÂN TÍCH CHI TIẾT (STRENGTHS & NEEDS)
                ========================================

                Dựa trên đề bài, bài làm, rule_violations và char_count_warning, hãy phân tích điểm mạnh và lỗi cần cải thiện.

                [NGUYÊN TẮC VÀNG]
                - Mỗi finding phải dùng evidenceScope được liệt kê trong allowed_rubric.
                - TEXT_SPAN: evidence PHẢI là chuỗi con CHÍNH XÁC trong learner_answer và không được rỗng.
                - WHOLE_ANSWER: evidence phải là chuỗi rỗng; finding đánh giá toàn bài và không tạo highlight giả.
                - TASK_METADATA: chỉ dùng khi allowed_rubric cho phép và payload có metadata có thẩm quyền.
                - strengths correction luôn là chuỗi rỗng "".
                - needs_improvement correction bắt buộc là từ/câu tiếng Hàn đã sửa chính xác.
                - Chỉ dùng criterionId có trong allowed_rubric, không tự bịa ID.
                - Quét tuần tự từ đầu đến cuối văn bản.

                [STRENGTHS - WRITING]
                1. W_ADVANCED_GRAMMAR_STRUCTURES: ngữ pháp trung-cao cấp được dùng đúng; không trộn với chính tả/cách chữ.
                2. W_FORMAL_REGISTER_CONSISTENCY: văn phong viết nhất quán, không lẫn đuôi nói.
                3. W_FORMAL_VOCABULARY_USAGE: từ vựng văn viết trang trọng và phù hợp ngữ cảnh.
                4. W_TOPIC_SPECIFIC_EXPRESSIONS: từ Hán-Hàn/collocation đúng chủ đề như 소득 창출, 인구 감소 현상.
                5. W_NATURAL_KOREAN_EXPRESSIONS: diễn đạt lại ý đề bài tự nhiên, không chép nguyên văn.
                6. W_ACCURATE_SPELLING_SPACING: chính tả và cách chữ chính xác trong evidence.
                7. W_LENGTH_REQUIREMENT_MET: toàn bài đạt phạm vi ký tự; dùng evidenceScope WHOLE_ANSWER.

                [NEEDS IMPROVEMENT - WRITING]
                1. W_VOCABULARY_ERRORS: sai nghĩa, sai ngữ cảnh, hoặc quá sơ cấp ở vị trí cần từ học thuật.
                2. W_GRAMMAR_ERRORS: sai vĩ tố, thời thì, cấu trúc; phân loại nếu phù hợp: 호응 오류, 피동/사동 오류, 시제 오류, 존칭 오류.
                3. W_PARTICLE_ERRORS: sai/thiếu 이/가, 을/를, 은/는, 에/에서...
                4. W_REPETITIVE_WORDS_EXPRESSIONS: lặp cùng từ/cụm/cấu trúc quá nhiều.
                5. W_AWKWARD_UNNATURAL_EXPRESSIONS: dịch thô từ tiếng Việt, câu gượng dù ngữ pháp không hoàn toàn sai.
                6. W_SENTENCE_STRUCTURE_ISSUES: câu quá dài, thiếu chủ/vị, vế câu bất đối xứng.
                7. W_REGISTER_CONSISTENCY_ISSUES: trộn văn nói/văn viết, 해요체 trong bài nghị luận, từ khẩu ngữ.
                8. W_SPELLING_SPACING_ERRORS: sai 맞춤법, 받침, dấu câu, 띄어쓰기.

                [BỘ LỌC KHẨU NGỮ ĐÃ PHÁT HIỆN BỞI JAVA]
                Trường rule_violations là sự thật kỹ thuật. Bắt buộc tạo W_REGISTER_CONSISTENCY_ISSUES cho mỗi vi phạm còn tìm thấy trong bài làm.

                [QUY TẮC EVIDENCE — QUAN TRỌNG]
                Backend chỉ tính start/end index cho TEXT_SPAN.
                - TEXT_SPAN phải nguyên văn, không thêm/bớt ký tự hay khoảng trắng.
                - WHOLE_ANSWER không có start/end và không được bịa đoạn trích.
                - Không dùng TASK_METADATA khi payload không cung cấp metadata có thẩm quyền.

                """ + taskDetailRules(taskType) + """

                ========================================
                PHẦN 3: BÀI NÂNG CẤP VÀ BÀI MẪU
                ========================================

                Tạo bài viết nâng cấp, bài mẫu, và bảng so sánh từng câu từ bài làm của học sinh.

                [NGUYÊN TẮC BẮT BUỘC]
                - upgraded_answer phải dựa sát 100% ý tưởng, dữ liệu và lập luận gốc của học sinh.
                - Chỉ sửa chính tả, cách chữ, tiểu từ, văn phong, từ vựng sơ cấp/khẩu ngữ, ngữ pháp lỗi.
                - Không bịa dữ kiện mới. Không viết lại thành một bài hoàn toàn khác.
                - sample_answer có thể là bài mẫu độc lập theo chuẩn TOPIK, nhưng phải phù hợp đề.
                - upgraded_answer_annotated bọc các cụm tốt bằng tag criterionId strengths.
                - sentence_rewrites chỉ liệt kê câu/cụm thật có thay đổi đáng kể: original, upgraded, reason bằng tiếng Việt.

                """ + taskUpgradeRules(taskType) + """

                """ + auditRules(isReEvaluation) + """

                ========================================
                YÊU CẦU OUTPUT
                ========================================

                Trả về JSON nghiêm ngặt gồm đúng các trường sau (KHÔNG trả score, raw_score, raw_score_max — backend tự tính):
                - summary (string): nhận xét tổng quan
                - rubric_scores (array): đúng 3 phần tử, mỗi phần tử có name, score (1.0-9.0), feedback
                - strengths (array): mỗi phần tử có criterionId, evidenceScope, evidence, explanationVi, correction
                - needs_improvement (array): mỗi phần tử có criterionId, evidenceScope, evidence, explanationVi, correction
                - upgraded_answer (string)
                - upgraded_answer_annotated (string)
                - sample_answer (string)
                - sentence_rewrites (array): mỗi phần tử có original, upgraded, reason

                rubric_scores phải dùng đúng 3 tên tiêu chí: """ + String.join(", ", rubricNamesForTask(taskType)) + """
                """;
    }

    private static String rubricInstruction(String taskType) {
        if (isClozeTask(taskType)) {
            return """
                    3 tiêu chí dành cho dạng hoàn thành câu/điền chỗ trống:
                    1. Hoàn thành đúng nội dung & ngữ cảnh (내용의 적절성):
                       - Câu trả lời phù hợp logic trước/sau chỗ trống.
                       - Nội dung đúng ý đoạn văn.
                    2. Ngữ pháp & cấu trúc câu (문법 및 문장 구성):
                       - Vĩ tố liên kết chính xác.
                       - Cấu trúc ngữ pháp đúng ngữ cảnh.
                    3. Từ vựng, register & tính tự nhiên (어휘 및 자연스러움):
                       - Từ vựng phù hợp văn cảnh.
                       - Văn phong tự nhiên, không gượng.
                    Không yêu cầu mở bài, thân bài, kết luận, bố cục đoạn văn tự do, số đoạn, hoặc từ nối nghị luận dài.""";
        }
        return """
                3 tiêu chí bắt buộc:
                1. Hoàn thành nhiệm vụ & Nội dung (내용 및 과제 수행):
                   - 과제 수행력: trả lời đầy đủ yêu cầu/gợi ý của đề, không bỏ ý trọng tâm.
                   - 주제의 연관성: nhất quán, không lan man, không lạc đề.
                   - 내용의 풍부성: có luận cứ, dẫn chứng, triển khai ý thuyết phục.
                2. Cấu trúc & Bố cục đoạn văn (글의 전개 구조):
                   - 단락 구성: bố cục mở-thân-kết hoặc trình tự phù hợp loại câu hỏi.
                   - 논리적 전개: triển khai ý logic, không đảo lộn, không đứt mạch.
                   - 담화 표지: dùng từ nối/từ chuyển đoạn như 반면에, 따라서, 그러므로, 결론적으로 khi phù hợp.
                3. Sử dụng ngôn ngữ & Quy tắc chính tả (언어 사용):
                   - Ngữ pháp, tiểu từ, chính tả, cách chữ, từ vựng trung-cao cấp, văn phong viết nhất quán.""";
    }

    static String taskSpecificRules(String taskType) {
        return switch (taskType == null ? "GENERAL" : taskType) {
            case "Q51", "Q52", "Q51_52" -> """
                    [YÊU CẦU CÂU 51/52]
                    - Đây là dạng điền chỗ trống trong bài viết TOPIK.
                    - Đánh giá sự hòa hợp nghĩa trước/sau chỗ trống và độ chính xác của vĩ tố liên kết.
                    - Điểm cao khi đáp án ngắn, tự nhiên, đúng logic, đúng văn phong.
                    - KHÔNG yêu cầu bố cục mở/thân/kết (no essay organization/structure requirement).
                    - Tiêu chí chấm:
                      * Hoàn thành đúng nội dung & ngữ cảnh: Đánh giá câu trả lời phù hợp logic trước/sau chỗ trống.
                      * Ngữ pháp & cấu trúc câu: Đánh giá độ chính xác của vĩ tố liên kết và cấu trúc ngữ pháp.
                      * Từ vựng, register & tính tự nhiên: Đánh giá từ vựng phù hợp ngữ cảnh, văn phong viết trang trọng.
                    """;
            case "Q53" -> """
                    [YÊU CẦU CÂU 53 - BIỂU ĐỒ 200~300자]
                    - Mô tả khách quan số liệu/xu hướng; không đưa ý kiến cá nhân (objective written style).
                    - Tuyệt đối KHÔNG tự bịa dữ kiện, số liệu ngoài đề bài (no fabricated data/statistics rule).
                    - Phạt nặng nếu dùng 나, 저, 생각한다, 느낀다 theo kiểu chủ quan.
                    - Ưu tiên cấu trúc ~에 따르면, ~ㄴ 것으로 나타났다, ~ㄹ 전망이다.
                    - Tiêu chí chấm:
                      * Hoàn thành nhiệm vụ & Nội dung: Đánh giá độ bao phủ dữ liệu (data/task coverage) từ biểu đồ.
                      * Cấu trúc & Bố cục đoạn văn: Đánh giá tính mạch lạc logic và cách sử dụng các từ nối chuyển ý (organization anchors).
                      * Sử dụng ngôn ngữ & Quy tắc chính tả: Đánh giá chính tả, cách chữ, từ vựng và cấu trúc viết trang trọng (language anchors).
                    """;
            case "Q54" -> """
                    [YÊU CẦU CÂU 54 - NGHỊ LUẬN 600~700자]
                    - Bố cục mở-thân-kết rõ ràng (organization anchors).
                    - Trả lời đầy đủ từng gợi ý của đề. Thiếu một ý gợi ý phải trừ mạnh tiêu chí nội dung.
                    - Phải có luận điểm rõ ràng (thesis/argument coverage), phát triển ý kiến bằng lý do, giải thích hoặc ví dụ thực tế (reasons/examples/development).
                    - Cần lập luận chặt chẽ, liên kết logic và văn phong nghị luận trang trọng.
                    - Tiêu chí chấm:
                      * Hoàn thành nhiệm vụ & Nội dung: Trả lời đầy đủ 3 câu hỏi gợi ý của đề bài và phát triển luận điểm thuyết phục.
                      * Cấu trúc & Bố cục đoạn văn: Đầy đủ 3 phần mở-thân-kết, sử dụng từ nối chuyển đoạn logic.
                      * Sử dụng ngôn ngữ & Quy tắc chính tả: Sử dụng từ vựng trung-cao cấp phong phú, ngữ pháp phức tạp chính xác (language anchors).
                    """;
            default -> """
                    [YÊU CẦU BÀI VIẾT CHUNG]
                    - Đánh giá mạch lạc, chính tả, cách chữ, tính tự nhiên và độ phù hợp với đề.
                    - Đây là bài viết chung (GENERAL), KHÔNG được tự ý ép thành câu hỏi mô tả biểu đồ Q53 hay câu nghị luận xã hội Q54 (no forcing into Q53 or Q54).
                    - Tiêu chí chấm:
                      * Hoàn thành nhiệm vụ & Nội dung: Đánh giá độ phù hợp với đề bài và ý tưởng.
                      * Cấu trúc & Bố cục đoạn văn: Đánh giá bố cục đoạn văn, sự mạch lạc giữa các câu.
                      * Sử dụng ngôn ngữ & Quy tắc chính tả: Đánh giá độ chính xác của ngữ pháp, từ vựng và chính tả.
                    """;
        };
    }

    static String taskDetailRules(String taskType) {
        if ("Q53".equals(taskType)) {
            return "Với Q53, ưu tiên bắt lỗi mô tả biểu đồ chủ quan, thiếu số liệu, sai cách diễn đạt xu hướng, và không đạt dung lượng.";
        }
        if ("Q54".equals(taskType)) {
            return "Với Q54, ưu tiên bắt lỗi thiếu ý gợi ý, thiếu mở-thân-kết, liên kết yếu, khẩu ngữ và dung lượng không đạt.";
        }
        if (isClozeTask(taskType)) {
            return "Với Q51/52, ưu tiên bắt lỗi hòa hợp nghĩa, vĩ tố liên kết, tiểu từ và ngữ pháp điền chỗ trống.";
        }
        return "Với bài chung, quét lần lượt toàn bài theo từng tiêu chí, không bỏ sót lỗi có evidence.";
    }

    static String taskUpgradeRules(String taskType) {
        if ("Q53".equals(taskType)) {
            return "Với Q53, bài nâng cấp phải khách quan, mô tả xu hướng/số liệu, giữ phong cách 200~300자.";
        }
        if ("Q54".equals(taskType)) {
            return "Với Q54, bài nâng cấp phải có bố cục nghị luận rõ và trả lời đủ các ý gợi ý.";
        }
        if (isClozeTask(taskType)) {
            return "Với Q51/52, bài nâng cấp tập trung sửa câu điền chỗ trống ngắn gọn, đúng logic.";
        }
        return "Giữ sát đề và nâng cấp tự nhiên theo chuẩn văn viết TOPIK.";
    }

    private static String auditRules(boolean isReEvaluation) {
        if (!isReEvaluation) {
            return "";
        }
        return """
                [AUDIT MODE - CHẤM KIỂM ĐỊNH ĐỘC LẬP]
                Đây là phiên chấm kiểm định độc lập. Không nhượng bộ, không tăng điểm để làm hài lòng học sinh.
                Không truyền hoặc dựa vào điểm cũ. Chỉ điều chỉnh điểm theo bài làm thực tế và rubric.
                Summary phải giải thích ngắn gọn nhưng chặt chẽ vì sao điểm hiện tại là hợp lý.
                """;
    }

    private static boolean isClozeTask(String taskType) {
        return "Q51".equals(taskType) || "Q52".equals(taskType) || "Q51_52".equals(taskType);
    }
}
