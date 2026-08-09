package com.ksh.features.lessons.controller.support;

import com.ksh.entities.LessonActivity;
import com.ksh.entities.SectionActivity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityRowMapperTest {

    @Test
    void section_label_maps_every_supported_activity_and_preserves_unknown_value() {
        assertThat(ActivityRowMapper.sectionLabel(SectionActivity.TYPE_CREATED)).isEqualTo("T\u1ea1o m\u1edbi");
        assertThat(ActivityRowMapper.sectionLabel(SectionActivity.TYPE_RENAMED)).isEqualTo("\u0110\u1ed5i t\u00ean");
        assertThat(ActivityRowMapper.sectionLabel(SectionActivity.TYPE_REORDERED)).isEqualTo("S\u1eafp x\u1ebfp l\u1ea1i");
        assertThat(ActivityRowMapper.sectionLabel(SectionActivity.TYPE_DELETED)).isEqualTo("Xo\u00e1");
        assertThat(ActivityRowMapper.sectionLabel("FUTURE_SECTION_ACTIVITY")).isEqualTo("FUTURE_SECTION_ACTIVITY");
    }

    @Test
    void lesson_label_maps_every_supported_activity_and_preserves_unknown_value() {
        assertThat(ActivityRowMapper.lessonLabel(LessonActivity.TYPE_CREATED)).isEqualTo("T\u1ea1o m\u1edbi");
        assertThat(ActivityRowMapper.lessonLabel(LessonActivity.TYPE_UPDATED)).isEqualTo("C\u1eadp nh\u1eadt");
        assertThat(ActivityRowMapper.lessonLabel(LessonActivity.TYPE_PUBLISHED)).isEqualTo("Xu\u1ea5t b\u1ea3n");
        assertThat(ActivityRowMapper.lessonLabel(LessonActivity.TYPE_UNPUBLISHED)).isEqualTo("Chuy\u1ec3n nh\u00e1p");
        assertThat(ActivityRowMapper.lessonLabel(LessonActivity.TYPE_REORDERED)).isEqualTo("S\u1eafp x\u1ebfp l\u1ea1i");
        assertThat(ActivityRowMapper.lessonLabel(LessonActivity.TYPE_DELETED)).isEqualTo("Xo\u00e1");
        assertThat(ActivityRowMapper.lessonLabel(LessonActivity.TYPE_PDF_UPLOADED)).isEqualTo("T\u1ea3i PDF");
        assertThat(ActivityRowMapper.lessonLabel(LessonActivity.TYPE_VIDEO_SET)).isEqualTo("G\u1eafn video");
        assertThat(ActivityRowMapper.lessonLabel(LessonActivity.TYPE_ATTACHMENT_ADDED)).isEqualTo("Th\u00eam t\u1ec7p");
        assertThat(ActivityRowMapper.lessonLabel(LessonActivity.TYPE_ATTACHMENT_REMOVED)).isEqualTo("Xo\u00e1 t\u1ec7p");
        assertThat(ActivityRowMapper.lessonLabel("FUTURE_LESSON_ACTIVITY")).isEqualTo("FUTURE_LESSON_ACTIVITY");
    }

    @Test
    void utility_constructor_is_private_but_instantiable_for_coverage() throws Exception {
        Constructor<ActivityRowMapper> constructor = ActivityRowMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThat(constructor.newInstance()).isNotNull();
    }
}
