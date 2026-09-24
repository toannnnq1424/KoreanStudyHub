package com.ksh.features.storage;

import com.ksh.features.admin.settings.service.SystemSettingsService;
import com.ksh.features.storage.profile.StorageProfileObjectStore;
import com.ksh.features.storage.profile.GeneralUploadsObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ObjectStoragePrimaryWiringTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path root;
    @Test void generic_injection_uses_routing_storage_but_concrete_backends_remain_distinct() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                    "test-storage", java.util.Map.of("app.upload.dir", root.toString())));
            context.registerBean(SystemSettingsService.class, () -> mock(SystemSettingsService.class));
            context.registerBean(StorageProfileObjectStore.class, () -> mock(StorageProfileObjectStore.class));
            context.registerBean(R2ClientHolder.class, () -> mock(R2ClientHolder.class));
            context.register(ObjectStorageConfig.class);
            context.refresh();
            ObjectStorage primary = context.getBean(ObjectStorage.class);
            assertThat(primary).isSameAs(context.getBean("objectStorage"))
                    .isInstanceOf(GeneralUploadsObjectStorage.class);
            assertThat(primary).isNotSameAs(context.getBean(LocalObjectStorage.class))
                    .isNotSameAs(context.getBean(R2ObjectStorage.class));
        }
    }
}
