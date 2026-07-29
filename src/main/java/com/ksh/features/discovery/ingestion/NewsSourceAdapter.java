package com.ksh.features.discovery.ingestion;

import com.ksh.features.discovery.entity.NewsSource;
import com.ksh.features.discovery.entity.NewsSourceType;

import java.util.List;

public interface NewsSourceAdapter {
    NewsSourceType supportedType();
    List<NewsCandidate> fetch(NewsSource source);
}
