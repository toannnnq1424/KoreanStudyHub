package com.ksh.features.practice.governance;

public enum PracticeAction {
    CREATE("practice.create"),
    READ("practice.read"),
    EDIT("practice.edit"),
    PUBLISH("practice.publish"),
    ARCHIVE("practice.archive"),
    LOCK("practice.lock"),
    RESTORE("practice.restore"),
    MATERIAL_MANAGE("practice.material.manage"),
    MEDIA_REVIEW("practice.media.review"),
    GOVERNANCE_MANAGE("practice.governance.manage"),
    EMERGENCY_OVERRIDE("practice.override");

    private final String permissionKey;

    PracticeAction(String permissionKey) {
        this.permissionKey = permissionKey;
    }

    public String permissionKey() {
        return permissionKey;
    }
}
