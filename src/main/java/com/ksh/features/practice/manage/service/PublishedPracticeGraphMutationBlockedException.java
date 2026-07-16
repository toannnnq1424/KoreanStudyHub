package com.ksh.features.practice.manage.service;

/**
 * Bounded conflict raised when a published practice graph can no longer be
 * destructively replaced because learner history already references it.
 */
public class PublishedPracticeGraphMutationBlockedException extends RuntimeException {

    public static final String RESTORE_MESSAGE =
            "Không thể khôi phục phiên bản vì bộ đề đã có lượt làm của người học.";
    public static final String REPUBLISH_MESSAGE =
            "Không thể xuất bản lại vì bộ đề đã có lượt làm của người học.";

    private PublishedPracticeGraphMutationBlockedException(String message) {
        super(message);
    }

    static PublishedPracticeGraphMutationBlockedException forRestore() {
        return new PublishedPracticeGraphMutationBlockedException(RESTORE_MESSAGE);
    }

    static PublishedPracticeGraphMutationBlockedException forRepublish() {
        return new PublishedPracticeGraphMutationBlockedException(REPUBLISH_MESSAGE);
    }
}
