package com.ksh.features.practice.manage.dto;

import java.util.List;

public class AiDocumentImportRequest {

    private RequestMeta requestMeta;
    private DocumentMetadata document;
    private List<PageContext> pageContexts;
    private List<SectionHint> sections;
    private List<GroupHint> groups;
    private List<RegionPayload> regions;
    private ImportConstraints constraints;

    public AiDocumentImportRequest() {
    }

    public RequestMeta getRequestMeta() {
        return requestMeta;
    }

    public void setRequestMeta(RequestMeta requestMeta) {
        this.requestMeta = requestMeta;
    }

    public DocumentMetadata getDocument() {
        return document;
    }

    public void setDocument(DocumentMetadata document) {
        this.document = document;
    }

    public List<PageContext> getPageContexts() {
        return pageContexts;
    }

    public void setPageContexts(List<PageContext> pageContexts) {
        this.pageContexts = pageContexts;
    }

    public List<SectionHint> getSections() {
        return sections;
    }

    public void setSections(List<SectionHint> sections) {
        this.sections = sections;
    }

    public List<GroupHint> getGroups() {
        return groups;
    }

    public void setGroups(List<GroupHint> groups) {
        this.groups = groups;
    }

    public List<RegionPayload> getRegions() {
        return regions;
    }

    public void setRegions(List<RegionPayload> regions) {
        this.regions = regions;
    }

    public ImportConstraints getConstraints() {
        return constraints;
    }

    public void setConstraints(ImportConstraints constraints) {
        this.constraints = constraints;
    }

    public static class DocumentMetadata {
        private Long sessionId;
        private String filename;
        private Integer targetTestNo;
        private String targetSkill;
        private String targetLessonCode;
        private Integer pageFrom;
        private Integer pageTo;
        private String sourceLanguage = "Korean";
        private String explanationLanguage = "Vietnamese";
        private Integer totalExtractedCharacters;

        public DocumentMetadata() {
        }

        public Long getSessionId() {
            return sessionId;
        }

        public void setSessionId(Long sessionId) {
            this.sessionId = sessionId;
        }

        public String getFilename() {
            return filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }

        public Integer getTargetTestNo() {
            return targetTestNo;
        }

        public void setTargetTestNo(Integer targetTestNo) {
            this.targetTestNo = targetTestNo;
        }

        public String getTargetSkill() {
            return targetSkill;
        }

        public void setTargetSkill(String targetSkill) {
            this.targetSkill = targetSkill;
        }

        public String getTargetLessonCode() {
            return targetLessonCode;
        }

        public void setTargetLessonCode(String targetLessonCode) {
            this.targetLessonCode = targetLessonCode;
        }

        public Integer getPageFrom() {
            return pageFrom;
        }

        public void setPageFrom(Integer pageFrom) {
            this.pageFrom = pageFrom;
        }

        public Integer getPageTo() {
            return pageTo;
        }

        public void setPageTo(Integer pageTo) {
            this.pageTo = pageTo;
        }

        public String getSourceLanguage() {
            return sourceLanguage;
        }

        public void setSourceLanguage(String sourceLanguage) {
            this.sourceLanguage = sourceLanguage;
        }

        public String getExplanationLanguage() {
            return explanationLanguage;
        }

        public void setExplanationLanguage(String explanationLanguage) {
            this.explanationLanguage = explanationLanguage;
        }

        public Integer getTotalExtractedCharacters() {
            return totalExtractedCharacters;
        }

        public void setTotalExtractedCharacters(Integer totalExtractedCharacters) {
            this.totalExtractedCharacters = totalExtractedCharacters;
        }
    }

    public static class RequestMeta {
        private String requestId;
        private String promptVersion;
        private String schemaVersion;
        private Integer sessionRevision;
        private Integer regionRevision;
        private String createdAt;

        public RequestMeta() {
        }

        public String getRequestId() {
            return requestId;
        }

        public void setRequestId(String requestId) {
            this.requestId = requestId;
        }

        public String getPromptVersion() {
            return promptVersion;
        }

        public void setPromptVersion(String promptVersion) {
            this.promptVersion = promptVersion;
        }

        public String getSchemaVersion() {
            return schemaVersion;
        }

        public void setSchemaVersion(String schemaVersion) {
            this.schemaVersion = schemaVersion;
        }

        public Integer getSessionRevision() {
            return sessionRevision;
        }

        public void setSessionRevision(Integer sessionRevision) {
            this.sessionRevision = sessionRevision;
        }

        public Integer getRegionRevision() {
            return regionRevision;
        }

        public void setRegionRevision(Integer regionRevision) {
            this.regionRevision = regionRevision;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(String createdAt) {
            this.createdAt = createdAt;
        }
    }

    public static class PageContext {
        private Integer pageNumber;
        private String rawText;
        private Integer rawCharCount;
        private Boolean allowEntityCreation = false;
        private String usageRule = "Context only. Do not create sections, groups, questions, options or answers from this text unless the same content is tied to sourceRegionIds.";

        public PageContext() {
        }

        public Integer getPageNumber() {
            return pageNumber;
        }

        public void setPageNumber(Integer pageNumber) {
            this.pageNumber = pageNumber;
        }

        public String getRawText() {
            return rawText;
        }

        public void setRawText(String rawText) {
            this.rawText = rawText;
        }

        public Integer getRawCharCount() {
            return rawCharCount;
        }

        public void setRawCharCount(Integer rawCharCount) {
            this.rawCharCount = rawCharCount;
        }

        public Boolean getAllowEntityCreation() {
            return allowEntityCreation;
        }

        public void setAllowEntityCreation(Boolean allowEntityCreation) {
            this.allowEntityCreation = allowEntityCreation;
        }

        public String getUsageRule() {
            return usageRule;
        }

        public void setUsageRule(String usageRule) {
            this.usageRule = usageRule;
        }
    }

    public static class SectionHint {
        private String sectionTempId;
        private String label;
        private String skill;
        private Integer testNo;
        private String lessonCode;
        private Integer displayOrder;
        private Integer durationMinutes;
        private List<String> sourceRegionIds;

        public SectionHint() {
        }

        public String getSectionTempId() {
            return sectionTempId;
        }

        public void setSectionTempId(String sectionTempId) {
            this.sectionTempId = sectionTempId;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getSkill() {
            return skill;
        }

        public void setSkill(String skill) {
            this.skill = skill;
        }

        public Integer getTestNo() {
            return testNo;
        }

        public void setTestNo(Integer testNo) {
            this.testNo = testNo;
        }

        public String getLessonCode() {
            return lessonCode;
        }

        public void setLessonCode(String lessonCode) {
            this.lessonCode = lessonCode;
        }

        public Integer getDisplayOrder() {
            return displayOrder;
        }

        public void setDisplayOrder(Integer displayOrder) {
            this.displayOrder = displayOrder;
        }

        public Integer getDurationMinutes() {
            return durationMinutes;
        }

        public void setDurationMinutes(Integer durationMinutes) {
            this.durationMinutes = durationMinutes;
        }

        public List<String> getSourceRegionIds() {
            return sourceRegionIds;
        }

        public void setSourceRegionIds(List<String> sourceRegionIds) {
            this.sourceRegionIds = sourceRegionIds;
        }
    }

    public static class GroupHint {
        private String groupTempId;
        private String sectionTempId;
        private String label;
        private Integer displayOrder;
        private Integer expectedQuestionFrom;
        private Integer expectedQuestionTo;
        private String expectedQuestionType;
        private List<String> sourceRegionIds;

        public GroupHint() {
        }

        public String getGroupTempId() {
            return groupTempId;
        }

        public void setGroupTempId(String groupTempId) {
            this.groupTempId = groupTempId;
        }

        public String getSectionTempId() {
            return sectionTempId;
        }

        public void setSectionTempId(String sectionTempId) {
            this.sectionTempId = sectionTempId;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public Integer getDisplayOrder() {
            return displayOrder;
        }

        public void setDisplayOrder(Integer displayOrder) {
            this.displayOrder = displayOrder;
        }

        public Integer getExpectedQuestionFrom() {
            return expectedQuestionFrom;
        }

        public void setExpectedQuestionFrom(Integer expectedQuestionFrom) {
            this.expectedQuestionFrom = expectedQuestionFrom;
        }

        public Integer getExpectedQuestionTo() {
            return expectedQuestionTo;
        }

        public void setExpectedQuestionTo(Integer expectedQuestionTo) {
            this.expectedQuestionTo = expectedQuestionTo;
        }

        public String getExpectedQuestionType() {
            return expectedQuestionType;
        }

        public void setExpectedQuestionType(String expectedQuestionType) {
            this.expectedQuestionType = expectedQuestionType;
        }

        public List<String> getSourceRegionIds() {
            return sourceRegionIds;
        }

        public void setSourceRegionIds(List<String> sourceRegionIds) {
            this.sourceRegionIds = sourceRegionIds;
        }
    }

    public static class RegionPayload {
        private String regionId;
        private Integer pageNumber;
        private Integer displayOrder;
        private String regionType;
        private String classificationSource;
        private RegionLocks locks;
        private Boolean sendText;
        private Boolean sendImage;
        private Boolean sendImageToAi;
        private Boolean keepCropInSession;
        private Boolean saveToLibrary;
        private Boolean displayInExam;
        private String ocrText;
        private String lecturerNote;
        private String suggestedNote;
        private String sectionTempId;
        private String groupTempId;
        private Integer expectedQuestionFrom;
        private Integer expectedQuestionTo;
        private String expectedQuestionType;
        private String assetRef;
        private String placement;
        private NormalizedBoundingBox bbox;
        private Boolean lockedByLecturer = false;

        public RegionPayload() {
        }

        public String getRegionId() {
            return regionId;
        }

        public void setRegionId(String regionId) {
            this.regionId = regionId;
        }

        public Integer getPageNumber() {
            return pageNumber;
        }

        public void setPageNumber(Integer pageNumber) {
            this.pageNumber = pageNumber;
        }

        public Integer getDisplayOrder() {
            return displayOrder;
        }

        public void setDisplayOrder(Integer displayOrder) {
            this.displayOrder = displayOrder;
        }

        public String getRegionType() {
            return regionType;
        }

        public void setRegionType(String regionType) {
            this.regionType = regionType;
        }

        public String getClassificationSource() {
            return classificationSource;
        }

        public void setClassificationSource(String classificationSource) {
            this.classificationSource = classificationSource;
        }

        public RegionLocks getLocks() {
            return locks;
        }

        public void setLocks(RegionLocks locks) {
            this.locks = locks;
        }

        public Boolean getSendText() {
            return sendText;
        }

        public void setSendText(Boolean sendText) {
            this.sendText = sendText;
        }

        public Boolean getSendImage() {
            return sendImage;
        }

        public void setSendImage(Boolean sendImage) {
            this.sendImage = sendImage;
        }

        public Boolean getSendImageToAi() {
            return sendImageToAi;
        }

        public void setSendImageToAi(Boolean sendImageToAi) {
            this.sendImageToAi = sendImageToAi;
        }

        public Boolean getKeepCropInSession() {
            return keepCropInSession;
        }

        public void setKeepCropInSession(Boolean keepCropInSession) {
            this.keepCropInSession = keepCropInSession;
        }

        public Boolean getSaveToLibrary() {
            return saveToLibrary;
        }

        public void setSaveToLibrary(Boolean saveToLibrary) {
            this.saveToLibrary = saveToLibrary;
        }

        public Boolean getDisplayInExam() {
            return displayInExam;
        }

        public void setDisplayInExam(Boolean displayInExam) {
            this.displayInExam = displayInExam;
        }

        public String getOcrText() {
            return ocrText;
        }

        public void setOcrText(String ocrText) {
            this.ocrText = ocrText;
        }

        public String getLecturerNote() {
            return lecturerNote;
        }

        public void setLecturerNote(String lecturerNote) {
            this.lecturerNote = lecturerNote;
        }

        public String getSuggestedNote() {
            return suggestedNote;
        }

        public void setSuggestedNote(String suggestedNote) {
            this.suggestedNote = suggestedNote;
        }

        public String getSectionTempId() {
            return sectionTempId;
        }

        public void setSectionTempId(String sectionTempId) {
            this.sectionTempId = sectionTempId;
        }

        public String getGroupTempId() {
            return groupTempId;
        }

        public void setGroupTempId(String groupTempId) {
            this.groupTempId = groupTempId;
        }

        public Integer getExpectedQuestionFrom() {
            return expectedQuestionFrom;
        }

        public void setExpectedQuestionFrom(Integer expectedQuestionFrom) {
            this.expectedQuestionFrom = expectedQuestionFrom;
        }

        public Integer getExpectedQuestionTo() {
            return expectedQuestionTo;
        }

        public void setExpectedQuestionTo(Integer expectedQuestionTo) {
            this.expectedQuestionTo = expectedQuestionTo;
        }

        public String getExpectedQuestionType() {
            return expectedQuestionType;
        }

        public void setExpectedQuestionType(String expectedQuestionType) {
            this.expectedQuestionType = expectedQuestionType;
        }

        public String getAssetRef() {
            return assetRef;
        }

        public void setAssetRef(String assetRef) {
            this.assetRef = assetRef;
        }

        public String getPlacement() {
            return placement;
        }

        public void setPlacement(String placement) {
            this.placement = placement;
        }

        public NormalizedBoundingBox getBbox() {
            return bbox;
        }

        public void setBbox(NormalizedBoundingBox bbox) {
            this.bbox = bbox;
        }

        public Boolean getLockedByLecturer() {
            return lockedByLecturer;
        }

        public void setLockedByLecturer(Boolean lockedByLecturer) {
            this.lockedByLecturer = lockedByLecturer;
        }
    }

    public static class RegionLocks {
        private Boolean regionType = false;
        private Boolean section = false;
        private Boolean group = false;
        private Boolean questionRange = false;
        private Boolean questionType = false;
        private Boolean placement = false;

        public RegionLocks() {
        }

        public Boolean getRegionType() {
            return regionType;
        }

        public void setRegionType(Boolean regionType) {
            this.regionType = regionType;
        }

        public Boolean getSection() {
            return section;
        }

        public void setSection(Boolean section) {
            this.section = section;
        }

        public Boolean getGroup() {
            return group;
        }

        public void setGroup(Boolean group) {
            this.group = group;
        }

        public Boolean getQuestionRange() {
            return questionRange;
        }

        public void setQuestionRange(Boolean questionRange) {
            this.questionRange = questionRange;
        }

        public Boolean getQuestionType() {
            return questionType;
        }

        public void setQuestionType(Boolean questionType) {
            this.questionType = questionType;
        }

        public Boolean getPlacement() {
            return placement;
        }

        public void setPlacement(Boolean placement) {
            this.placement = placement;
        }
    }

    public static class NormalizedBoundingBox {
        private Double x;
        private Double y;
        private Double width;
        private Double height;

        public NormalizedBoundingBox() {
        }

        public NormalizedBoundingBox(Double x, Double y, Double width, Double height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public Double getX() {
            return x;
        }

        public void setX(Double x) {
            this.x = x;
        }

        public Double getY() {
            return y;
        }

        public void setY(Double y) {
            this.y = y;
        }

        public Double getWidth() {
            return width;
        }

        public void setWidth(Double width) {
            this.width = width;
        }

        public Double getHeight() {
            return height;
        }

        public void setHeight(Double height) {
            this.height = height;
        }
    }

    public static class ImportConstraints {
        private Boolean preserveLecturerGrouping = true;
        private Boolean preserveQuestionNumbers = true;
        private Boolean allowIncompleteDraft = true;
        private Boolean allowAnswerInference = false;
        private Boolean returnVietnameseWarnings = true;

        public ImportConstraints() {
        }

        public Boolean getPreserveLecturerGrouping() {
            return preserveLecturerGrouping;
        }

        public void setPreserveLecturerGrouping(Boolean preserveLecturerGrouping) {
            this.preserveLecturerGrouping = preserveLecturerGrouping;
        }

        public Boolean getPreserveQuestionNumbers() {
            return preserveQuestionNumbers;
        }

        public void setPreserveQuestionNumbers(Boolean preserveQuestionNumbers) {
            this.preserveQuestionNumbers = preserveQuestionNumbers;
        }

        public Boolean getAllowIncompleteDraft() {
            return allowIncompleteDraft;
        }

        public void setAllowIncompleteDraft(Boolean allowIncompleteDraft) {
            this.allowIncompleteDraft = allowIncompleteDraft;
        }

        public Boolean getAllowAnswerInference() {
            return allowAnswerInference;
        }

        public void setAllowAnswerInference(Boolean allowAnswerInference) {
            this.allowAnswerInference = allowAnswerInference;
        }

        public Boolean getReturnVietnameseWarnings() {
            return returnVietnameseWarnings;
        }

        public void setReturnVietnameseWarnings(Boolean returnVietnameseWarnings) {
            this.returnVietnameseWarnings = returnVietnameseWarnings;
        }
    }
}
