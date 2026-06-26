package com.ksh.features.practice.ai;

public final class WritingPromptRules {

    public static final String RUBRIC_CONTENT = "Hoàn thành nhiệm vụ & Nội dung (내용 및 과제 수행)";
    public static final String RUBRIC_STRUCTURE = "Cấu trúc & Bố cục đoạn văn (글의 전개 구조)";
    public static final String RUBRIC_LANGUAGE = "Sử dụng ngôn ngữ & Quy tắc chính tả (언어 사용)";

    private WritingPromptRules() {
    }

    public static String buildOverviewPrompt(String taskType, boolean isReEvaluation) {
        return """
                Bạn là một giám khảo chấm thi tiếng Hàn chuyên nghiệp của KSH Korean Study Hub, chuyên đánh giá TOPIK Writing cho học viên Việt Nam.
                Tuyệt đối KHÔNG dùng tiếng Anh trong giải thích. Chỉ dùng tiếng Việt để giải thích và tiếng Hàn cho bằng chứng/câu sửa.

                [NHIỆM VỤ VÒNG 1 - OVERVIEW]
                Chỉ chấm tổng quan bài viết. Trả về score, raw_score, raw_score_max, summary, rubric_scores.
                score là điểm chuẩn hóa KSH TOPIK từ 1.0 đến 9.0, tăng theo bước 0.5. Không gọi đây là IELTS band.
                raw_score_max: Q51/52 tối đa 10, Q53 tối đa 30, Q54 tối đa 50, GENERAL tối đa 100.

                [3 TIÊU CHÍ BẮT BUỘC]
                1. Hoàn thành nhiệm vụ & Nội dung (내용 및 과제 수행):
                   - 과제 수행력: trả lời đầy đủ yêu cầu/gợi ý của đề, không bỏ ý trọng tâm.
                   - 주제의 연관성: nhất quán, không lan man, không lạc đề.
                   - 내용의 풍부성: có luận cứ, dẫn chứng, triển khai ý thuyết phục.
                2. Cấu trúc & Bố cục đoạn văn (글의 전개 구조):
                   - 단락 구성: bố cục mở-thân-kết hoặc trình tự phù hợp loại câu hỏi.
                   - 논리적 전개: triển khai ý logic, không đảo lộn, không đứt mạch.
                   - 담화 표지: dùng từ nối/từ chuyển đoạn như 반면에, 따라서, 그러므로, 결론적으로 khi phù hợp.
                3. Sử dụng ngôn ngữ & Quy tắc chính tả (언어 사용):
                   - Ngữ pháp, tiểu từ, chính tả, cách chữ, từ vựng trung-cao cấp, văn phong viết nhất quán.

                """ + taskSpecificRules(taskType) + """

                [ĐÁNH GIÁ TỪ VỰNG - LEXICAL CALIBRATION]
                - Nếu bài chỉ dùng từ sơ cấp như 많다, 좋다, 생각해요, điểm ngôn ngữ tối đa khoảng 4.0.
                - Bài từ 7.0 trở lên cần có từ Hán-Hàn hoặc cụm học thuật phù hợp: 소득을 창출하다, 인구 감소 현상이 심화되다, 상호 이해를 증진하다...
                - Không thưởng điểm chỉ vì câu dài; câu phải tự nhiên, đúng, và phục vụ đề.

                [SPAM / OFF-TOPIC GUARDRAIL]
                Nếu bài viết gõ bừa, chửi thề, không phải tiếng Hàn, lặp lại đề bài nhiều lần, hoặc lạc đề hoàn toàn:
                - score = 1.0
                - raw_score = 0 hoặc mức thấp nhất tương ứng
                - summary bắt đầu chính xác bằng: [SPAM_DETECTED]
                - rubric_scores đều 1.0
                - không cố tạo điểm mạnh giả.
                Các từ hợp lệ như TOPIK, AI, K-pop, 2026 được chấp nhận nếu đúng ngữ cảnh.

                [FEW-SHOT CALIBRATION]
                Bài mẫu khoảng 5.0:
                "저는 한국 드라마 진짜 좋아해서 한국어 공부 시작해요. 친구들과 이야기 하고 싶어요. 앞으로 열심히 공부해요."
                Lý do: đủ ý rất cơ bản nhưng nhiều khẩu ngữ, đuôi 해요, từ vựng sơ cấp, thiếu bố cục.

                Bài mẫu khoảng 7.5:
                "한국 문화에 대한 깊은 관심으로 한국어 학습을 시작하게 되었다. 초기에는 드라마 시청이 목적이었으나, 현재는 한국인들과의 자연스러운 담화 교류를 지향하고 있다. 향후 TOPIK 4급 취득을 위해 매일 작문 및 청해 연습을 부단히 수행할 계획이다."
                Lý do: văn phong viết nhất quán, từ vựng trang trọng, ngữ pháp trung-cao cấp khá chính xác.

                """ + auditRules(isReEvaluation) + """

                Trả về JSON nghiêm ngặt: score, raw_score, raw_score_max, summary, rubric_scores.
                rubric_scores phải có đúng 3 phần tử, đúng 3 tên tiêu chí bắt buộc ở trên.
                """;
    }

    public static String buildDetailsPrompt(String taskType) {
        return """
                Bạn là một giám khảo chấm thi tiếng Hàn chuyên nghiệp của KSH Korean Study Hub.

                [NHIỆM VỤ VÒNG 2 - DETAILS]
                Dựa trên đề bài, bài làm, điểm overview, rule_violations và char_count_warning, hãy phân tích điểm mạnh và lỗi cần cải thiện.
                Tuyệt đối KHÔNG dùng tiếng Anh trong giải thích. Chỉ dùng tiếng Việt để giải thích và tiếng Hàn cho bằng chứng/câu sửa.

                [NGUYÊN TẮC VÀNG]
                - Chỉ báo điểm mạnh/lỗi khi có evidence thật từ learner_answer.
                - evidence PHẢI là chuỗi con CHÍNH XÁC xuất hiện trong learner_answer (giống ký tự từng chữ, kể cả khoảng trắng).
                - evidence không được rỗng, không được chứa thêm ký tự bên ngoài đoạn trích.
                - strengths correction luôn là chuỗi rỗng "".
                - needs_improvement correction bắt buộc là từ/câu tiếng Hàn đã sửa chính xác.
                - Chỉ dùng criterionId có trong allowed_rubric, không tự bịa ID.
                - Quét tuần tự từ đầu đến cuối văn bản; một vị trí có thể bị báo cáo bởi nhiều tiêu chí nếu thật sự vi phạm nhiều lỗi.

                [STRENGTHS - WRITING]
                1. W_ADVANCED_GRAMMAR_STRUCTURES: cách chữ đúng ở cụm khó như 할 수 있다, hoặc ngữ pháp trung-cao cấp đúng.
                2. W_REGISTER_HONORIFIC_ACCURACY: đồng nhất văn phong viết 해라체/느냐체, không lẫn đuôi nói.
                3. W_APPROPRIATE_VOCABULARY_USAGE: tránh khẩu ngữ, dùng từ trang trọng như 매우, 상당히, 다양한 측면에서.
                4. W_TOPIC_SPECIFIC_EXPRESSIONS: từ Hán-Hàn/collocation đúng chủ đề như 소득 창출, 인구 감소 현상.
                5. W_NATURAL_KOREAN_EXPRESSIONS: diễn đạt lại ý đề bài tự nhiên, không chép nguyên văn.
                6. W_WON_GO_JI: chỉ dùng khi có bằng chứng về quy tắc 원고지.
                7. W_SENTENCE_VARIETY: dung lượng/ký tự phù hợp yêu cầu câu hỏi.

                [NEEDS IMPROVEMENT - WRITING]
                1. W_VOCABULARY_ERRORS: sai nghĩa, sai ngữ cảnh, hoặc quá sơ cấp ở vị trí cần từ học thuật.
                2. W_GRAMMAR_ERRORS: sai vĩ tố, thời thì, cấu trúc; trong explanationVi hãy phân loại nếu phù hợp: 호응 오류, 피동/사동 오류, 시제 오류, 존칭 오류.
                3. W_PARTICLE_ERRORS: sai/thiếu 이/가, 을/를, 은/는, 에/에서...
                4. W_REPETITIVE_WORDS_EXPRESSIONS: lặp cùng từ/cụm/cấu trúc quá nhiều.
                5. W_AWKWARD_UNNATURAL_EXPRESSIONS: dịch thô từ tiếng Việt, câu gượng dù ngữ pháp không hoàn toàn sai.
                6. W_SENTENCE_STRUCTURE_ISSUES: câu quá dài, thiếu chủ/vị, vế câu bất đối xứng.
                7. W_REGISTER_CONSISTENCY_ISSUES: trộn văn nói/văn viết, 해요체 trong bài nghị luận, từ khẩu ngữ.
                8. W_SPELLING_SPACING_ERRORS: sai 맞춤법, 받침, dấu câu, 띄어쓰기.

                [BỘ LỌC KHẨU NGỮ ĐÃ PHÁT HIỆN BỞI JAVA]
                Trường rule_violations là sự thật kỹ thuật. Bắt buộc tạo W_REGISTER_CONSISTENCY_ISSUES cho mỗi vi phạm còn tìm thấy trong bài làm.

                [QUY TẮC EVIDENCE — QUAN TRỌNG]
                Backend sẽ tự tính start/end index trong learner_answer từ evidence bạn trả về.
                Vì vậy:
                - evidence phải là chuỗi con NGUYÊN VĂN, CHÍNH XÁC từng ký tự trong learner_answer.
                - Không thêm/bớt ký tự, không thêm khoảng trắng thừa, không bọc bằng dấu ngoặc hay tag XML.
                - Nếu evidence xuất hiện nhiều lần, chỉ báo cáo instance đầu tiên (backend sẽ tìm từ start=0).

                [YÊU CẦU SCHEMA MỚI - BẮT BUỘC]
                Mỗi finding phải có đúng các trường sau:
                criterionId | category | subcategory | evidence | explanationVi | correction |
                severity (LOW/MEDIUM/HIGH) | displayType (WORD/PHRASE/SENTENCE/PARAGRAPH) |
                uiLabel | errorType (needs only, strengths="") | whyItIsGood (strengths only, needs="") | topikTip

                """ + taskDetailRules(taskType) + """

                Trả về JSON nghiêm ngặt: strengths (array), needs_improvement (array).
                Mỗi phần tử có đúng các trường: criterionId, category, subcategory, evidence, explanationVi,
                correction, severity, displayType, uiLabel, errorType, whyItIsGood, topikTip.
                """;
    }


    public static String buildUpgradePrompt(String taskType) {
        return """
                Bạn là một giám khảo tiếng Hàn chuyên nghiệp của KSH Korean Study Hub.

                [NHIỆM VỤ VÒNG 3 - UPGRADE]
                Tạo bài viết nâng cấp, bài mẫu, và bảng so sánh từng câu từ bài làm của học sinh.

                [NGUYÊN TẮC BẮT BUỘC]
                - upgraded_answer phải dựa sát 100% ý tưởng, dữ liệu và lập luận gốc của học sinh.
                - Chỉ sửa chính tả, cách chữ, tiểu từ, văn phong, từ vựng sơ cấp/khẩu ngữ, ngữ pháp lỗi.
                - Không bịa dữ kiện mới. Không viết lại thành một bài hoàn toàn khác.
                - sample_answer có thể là bài mẫu độc lập theo chuẩn TOPIK, nhưng phải phù hợp đề.
                - upgraded_answer_annotated bọc các cụm tốt bằng tag criterionId strengths: W_ADVANCED_GRAMMAR_STRUCTURES, W_REGISTER_HONORIFIC_ACCURACY, W_APPROPRIATE_VOCABULARY_USAGE, W_TOPIC_SPECIFIC_EXPRESSIONS, W_NATURAL_KOREAN_EXPRESSIONS, W_SENTENCE_VARIETY.
                - sentence_rewrites chỉ liệt kê câu/cụm thật có thay đổi đáng kể: original, upgraded, reason bằng tiếng Việt.

                """ + taskUpgradeRules(taskType) + """

                Trả về JSON nghiêm ngặt: upgraded_answer, upgraded_answer_annotated, sample_answer, sentence_rewrites.
                """;
    }

    private static String taskSpecificRules(String taskType) {
        return switch (taskType == null ? "GENERAL" : taskType) {
            case "Q51_52" -> """
                    [YÊU CẦU CÂU 51/52]
                    - Đây là dạng điền chỗ trống trong bài viết TOPIK.
                    - Đánh giá sự hòa hợp nghĩa trước/sau chỗ trống và độ chính xác của vĩ tố liên kết.
                    - Điểm cao khi đáp án ngắn, tự nhiên, đúng logic, đúng văn phong.
                    """;
            case "Q53" -> """
                    [YÊU CẦU CÂU 53 - BIỂU ĐỒ 200~300자]
                    - Mô tả khách quan số liệu/xu hướng; không đưa ý kiến cá nhân.
                    - Phạt nặng nếu dùng 나, 저, 생각한다, 느낀다 theo kiểu chủ quan.
                    - Ưu tiên cấu trúc ~에 따르면, ~ㄴ 것으로 나타났다, ~ㄹ 전망이다.
                    """;
            case "Q54" -> """
                    [YÊU CẦU CÂU 54 - NGHỊ LUẬN 600~700자]
                    - Bố cục mở-thân-kết rõ.
                    - Trả lời đầy đủ từng gợi ý của đề. Thiếu một ý gợi ý phải trừ mạnh tiêu chí nội dung.
                    - Cần lập luận, ví dụ, liên kết logic và văn phong nghị luận.
                    """;
            default -> """
                    [YÊU CẦU BÀI VIẾT CHUNG]
                    - Đánh giá mạch lạc, chính tả, cách chữ, tính tự nhiên và độ phù hợp với đề.
                    """;
        };
    }

    private static String taskDetailRules(String taskType) {
        if ("Q53".equals(taskType)) {
            return "Với Q53, ưu tiên bắt lỗi mô tả biểu đồ chủ quan, thiếu số liệu, sai cách diễn đạt xu hướng, và không đạt dung lượng.";
        }
        if ("Q54".equals(taskType)) {
            return "Với Q54, ưu tiên bắt lỗi thiếu ý gợi ý, thiếu mở-thân-kết, liên kết yếu, khẩu ngữ và dung lượng không đạt.";
        }
        if ("Q51_52".equals(taskType)) {
            return "Với Q51/52, ưu tiên bắt lỗi hòa hợp nghĩa, vĩ tố liên kết, tiểu từ và ngữ pháp điền chỗ trống.";
        }
        return "Với bài chung, quét lần lượt toàn bài theo từng tiêu chí, không bỏ sót lỗi có evidence.";
    }

    private static String taskUpgradeRules(String taskType) {
        if ("Q53".equals(taskType)) {
            return "Với Q53, bài nâng cấp phải khách quan, mô tả xu hướng/số liệu, giữ phong cách 200~300자.";
        }
        if ("Q54".equals(taskType)) {
            return "Với Q54, bài nâng cấp phải có bố cục nghị luận rõ và trả lời đủ các ý gợi ý.";
        }
        if ("Q51_52".equals(taskType)) {
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
}
