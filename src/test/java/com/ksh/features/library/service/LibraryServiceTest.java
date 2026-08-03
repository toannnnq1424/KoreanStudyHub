package com.ksh.features.library.service;

import com.ksh.entities.LibraryAsset;
import com.ksh.entities.User;
import com.ksh.features.auth.repository.UserRepository;
import com.ksh.features.library.dto.LibraryDtos.LibraryAssetRow;
import com.ksh.features.library.repository.LibraryAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Integration contracts for materials uploaded from Library lesson authoring. */
@SpringBootTest
@Transactional
class LibraryServiceTest {

    @Autowired private LibraryService libraryService;
    @Autowired private LibraryAssetRepository assetRepository;
    @Autowired private UserRepository userRepository;

    private User lecturer;

    @BeforeEach
    void setUp() {
        lecturer = userRepository.findByEmailIgnoreCase("lecturer@ksh.edu.vn").orElseThrow();
    }

    @Test
    void upload_document_persists_owned_material() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "materialUploads", "slide.pdf", "application/pdf",
                new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x37, 0x0A});

        LibraryAssetRow row = libraryService.upload(lecturer.getId(), file, "DOCUMENT");

        LibraryAsset asset = assetRepository.findByIdAndOwnerId(row.id(), lecturer.getId())
                .orElseThrow();
        assertThat(asset.getKind()).isEqualTo(LibraryAsset.KIND_DOCUMENT);
        assertThat(asset.getStoredPath()).startsWith("library/" + lecturer.getId() + "/");
    }

    @Test
    void upload_rejects_unsupported_material_extension() {
        MockMultipartFile bad = new MockMultipartFile(
                "materialUploads", "note.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> libraryService.upload(lecturer.getId(), bad, "DOCUMENT"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
