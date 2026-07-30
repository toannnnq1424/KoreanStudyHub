package com.ksh.features.discovery.entity;

public enum NewsSourceLayout {
    KBS_WORLD("kbs-world"),
    KOREA_NET("korea-net"),
    STUDY_IN_KOREA("study-in-korea");

    private final String cssClass;

    NewsSourceLayout(String cssClass) {
        this.cssClass = cssClass;
    }

    public String getCssClass() {
        return cssClass;
    }
}
