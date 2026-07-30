package com.ksh.features.discovery.ingestion;

import com.ksh.features.discovery.entity.NewsSourceLayout;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NewsSourceContentCrawlerTest {

    private final NewsContentSanitizer sanitizer = new NewsContentSanitizer();

    @Test
    void extractsFullKbsBodyAndDropsExecutableMarkup() {
        String html = """
                <html><head>
                  <meta property="og:image" content="https://worldimg.kbs.co.kr/hero.jpg">
                </head><body>
                  <div class="body_txt fr-view">
                    <div class="img_box"><img src="https://worldimg.kbs.co.kr/body.jpg"></div>
                    Đoạn một.<br><br>Đoạn hai.
                    <script>alert('xss')</script>
                  </div>
                </body></html>
                """;

        NewsSourceContentCrawler.ParsedSourceContent parsed =
                NewsSourceContentCrawler.parseKbs(
                        html,
                        "https://world.kbs.co.kr/service/news_view.htm?Seq_Code=1",
                        sanitizer
                );

        assertThat(parsed.imageUrl()).isEqualTo("https://worldimg.kbs.co.kr/hero.jpg");
        assertThat(parsed.content().layout()).isEqualTo(NewsSourceLayout.KBS_WORLD);
        assertThat(parsed.content().html()).contains("Đoạn một.", "body.jpg");
        assertThat(parsed.content().html()).doesNotContain("<script", "alert(");
        assertThat(parsed.content().text()).contains("Đoạn một.", "Đoạn hai.");
    }

    @Test
    void extractsKoreaNetBodyAndMakesImagesAbsolute() {
        String html = """
                <html><head>
                  <meta property="og:image" content="https://vietnamese.korea.net/hero.jpg">
                </head><body>
                  <div id="content_text_ALLBOX">
                    <div class="figCenter">
                      <img src="/upload/content/image/story.jpg" alt="Ảnh nguồn">
                      <p class="figcaption">Chú thích ảnh.</p>
                    </div>
                    <p>Toàn bộ ruột bài Korea.net.</p>
                  </div>
                </body></html>
                """;

        NewsSourceContentCrawler.ParsedSourceContent parsed =
                NewsSourceContentCrawler.parseKoreaNet(
                        html,
                        "https://vietnamese.korea.net/NewsFocus/FoodTravel/view?articleId=1",
                        sanitizer
                );

        assertThat(parsed.content().layout()).isEqualTo(NewsSourceLayout.KOREA_NET);
        assertThat(parsed.content().html())
                .contains("https://vietnamese.korea.net/upload/content/image/story.jpg")
                .contains("figcaption")
                .contains("Toàn bộ ruột bài Korea.net.");
    }

    @Test
    void extractsStudyInKoreaAttachmentNamesUrlsAndSizes() {
        String html = """
                <div class="link-wrap">
                  <a href="javascript:fn_egov_downFile('FILE_123','1')">
                    2027 GKS Application Guidelines.pdf [283434 byte]
                  </a>
                  <a href="javascript:fn_egov_downFile('FILE_123','2')">
                    Application Form.docx [1024 byte]
                  </a>
                </div>
                """;

        List<NewsAttachmentCandidate> attachments =
                NewsSourceContentCrawler.parseStudyAttachments(html);

        assertThat(attachments).hasSize(2);
        assertThat(attachments.get(0).displayName())
                .isEqualTo("2027 GKS Application Guidelines.pdf");
        assertThat(attachments.get(0).sourceUrl())
                .contains("atchFileId=FILE_123")
                .contains("fileSn=1");
        assertThat(attachments.get(0).sizeBytes()).isEqualTo(283434);
        assertThat(attachments.get(0).mediaType()).isEqualTo("application/pdf");
    }
}
