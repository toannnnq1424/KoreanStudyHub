package com.ksh.features.library.service;

import com.ksh.entities.Department;
import com.ksh.entities.Lesson;
import com.ksh.entities.LessonTemplate;
import com.ksh.features.library.dto.LessonTemplateForm;
import com.ksh.features.library.repository.LessonTemplateAttachmentRepository;
import com.ksh.features.library.repository.LessonTemplateRepository;
import com.ksh.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static com.ksh.common.IConstant.VIDEO_PROVIDER_UPLOAD;
import static com.ksh.common.IConstant.VIDEO_PROVIDER_YOUTUBE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Regression coverage for the authoring form's external-video URL boundary. */
@ExtendWith(MockitoExtension.class)
class LessonTemplateServiceFormTest {

    private static final long OWNER_ID = 7L;
    private static final long TEMPLATE_ID = 42L;
    private static final long SUBJECT_ID = 11L;

    @Mock private LessonTemplateRepository templateRepository;
    @Mock private LessonTemplateAttachmentRepository templateAttachmentRepository;
    @Mock private LibrarySubjectResolver subjectResolver;

    private LessonTemplateService service;

    @BeforeEach
    void setUp() {
        service = new LessonTemplateService(
                templateRepository, templateAttachmentRepository, mock(), mock(), mock(), mock(), mock(),
                mock(), mock(), mock(), mock(), mock(), subjectResolver);
        when(subjectResolver.require(OWNER_ID, Role.LECTURER, SUBJECT_ID))
                .thenReturn(mock(Department.class));
        when(templateAttachmentRepository.findByTemplateIdOrderByDisplayOrderAsc(anyLong()))
                .thenReturn(List.of());
    }

    @Test
    void uploaded_video_keeps_its_asset_but_does_not_fill_the_external_url_field() {
        LessonTemplate template = template(VIDEO_PROVIDER_UPLOAD, "library/video/private-key.mp4", 77L);
        when(templateRepository.findByIdAndOwnerId(TEMPLATE_ID, OWNER_ID)).thenReturn(Optional.of(template));

        LessonTemplateForm form = service.loadForm(
                OWNER_ID, Role.LECTURER, TEMPLATE_ID, SUBJECT_ID);

        assertThat(form.getVideoProvider()).isEqualTo(VIDEO_PROVIDER_UPLOAD);
        assertThat(form.getVideoLibraryAssetId()).isEqualTo(77L);
        assertThat(form.getVideoUrl()).isNull();
    }

    @Test
    void external_video_keeps_its_url_for_the_external_url_field() {
        String videoUrl = "https://www.youtube.com/watch?v=video123";
        LessonTemplate template = template(VIDEO_PROVIDER_YOUTUBE, videoUrl, null);
        when(templateRepository.findByIdAndOwnerId(TEMPLATE_ID, OWNER_ID)).thenReturn(Optional.of(template));

        LessonTemplateForm form = service.loadForm(
                OWNER_ID, Role.LECTURER, TEMPLATE_ID, SUBJECT_ID);

        assertThat(form.getVideoUrl()).isEqualTo(videoUrl);
    }

    private static LessonTemplate template(String provider, String videoUrl, Long assetId) {
        LessonTemplate template = new LessonTemplate(
                OWNER_ID, SUBJECT_ID, 1, "Chương 1", 1, "Bài video", Lesson.CONTENT_TYPE_VIDEO);
        template.setVideoProvider(provider);
        template.setVideoUrl(videoUrl);
        template.setVideoLibraryAssetId(assetId);
        return template;
    }
}
