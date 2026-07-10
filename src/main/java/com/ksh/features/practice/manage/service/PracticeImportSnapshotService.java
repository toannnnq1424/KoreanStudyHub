package com.ksh.features.practice.manage.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksh.entities.PracticePdfImportSession;
import com.ksh.entities.PracticePdfRegionAnnotation;
import com.ksh.features.practice.repository.PracticePdfImportSessionRepository;
import com.ksh.features.practice.repository.PracticePdfRegionAnnotationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PracticeImportSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(PracticeImportSnapshotService.class);

    private final PracticePdfImportSessionRepository sessionRepository;
    private final PracticePdfRegionAnnotationRepository annotationRepository;
    private final ObjectMapper objectMapper;

    public PracticeImportSnapshotService(PracticePdfImportSessionRepository sessionRepository,
                                         PracticePdfRegionAnnotationRepository annotationRepository,
                                         ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.annotationRepository = annotationRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void saveSnapshot(Long sessionId, Long userId) {
        PracticePdfImportSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Session không tồn tại."));
        if (!session.getUploaderId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Bạn không có quyền quản lý session này.");
        }

        try {
            List<PracticePdfRegionAnnotation> annos = annotationRepository.findBySessionIdOrderByPageNumberAscDisplayOrderAsc(sessionId);
            
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("selectedStartPage", session.getSelectedStartPage());
            snapshot.put("selectedEndPage", session.getSelectedEndPage());
            snapshot.put("currentPage", session.getCurrentPage());
            snapshot.put("extractionStrategy", session.getExtractionStrategy());
            snapshot.put("annotations", annos);

            String json = objectMapper.writeValueAsString(snapshot);
            session.setSnapshotJson(json);
            session.setLastSavedAt(LocalDateTime.now());
            sessionRepository.save(session);
            log.info("[SnapshotService] Snapshot saved for sessionId={}", sessionId);
        } catch (Exception e) {
            log.error("[SnapshotService] Failed to save snapshot for sessionId={}", sessionId, e);
            throw new RuntimeException("Không thể lưu snapshot phiên import.", e);
        }
    }

    @Transactional
    public void restoreSnapshot(Long sessionId, Long userId) {
        PracticePdfImportSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Session không tồn tại."));
        if (!session.getUploaderId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Bạn không có quyền quản lý session này.");
        }

        String json = session.getSnapshotJson();
        if (json == null || json.isBlank()) {
            throw new IllegalStateException("Không tìm thấy bản lưu gần nhất để khôi phục.");
        }

        try {
            Map<String, Object> snapshot = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            
            // Restore session basic parameters
            session.setSelectedStartPage((Integer) snapshot.get("selectedStartPage"));
            session.setSelectedEndPage((Integer) snapshot.get("selectedEndPage"));
            session.setCurrentPage((Integer) snapshot.get("currentPage"));
            session.setExtractionStrategy((String) snapshot.get("extractionStrategy"));
            sessionRepository.save(session);

            // Delete current annotations
            annotationRepository.deleteBySessionId(sessionId);

            // Restore annotations
            String annosJson = objectMapper.writeValueAsString(snapshot.get("annotations"));
            List<PracticePdfRegionAnnotation> restoredAnnos = objectMapper.readValue(annosJson, new TypeReference<List<PracticePdfRegionAnnotation>>() {});
            
            for (PracticePdfRegionAnnotation ann : restoredAnnos) {
                ann.setId(null); // Force insert new IDs
                ann.setSessionId(sessionId);
                if (ann.getCreatedAt() == null) ann.setCreatedAt(LocalDateTime.now());
                ann.setUpdatedAt(LocalDateTime.now());
                annotationRepository.save(ann);
            }
            log.info("[SnapshotService] Snapshot restored for sessionId={}", sessionId);
        } catch (Exception e) {
            log.error("[SnapshotService] Failed to restore snapshot for sessionId={}", sessionId, e);
            throw new RuntimeException("Không thể khôi phục snapshot phiên import.", e);
        }
    }
}
