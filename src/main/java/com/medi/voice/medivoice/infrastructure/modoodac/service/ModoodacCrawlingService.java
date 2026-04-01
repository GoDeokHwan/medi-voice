package com.medi.voice.medivoice.infrastructure.modoodac.service;

import java.util.Map;

public interface ModoodacCrawlingService {

    default ModoodacCrawlPayload crawl(String targetUrl, Long clinicId) {
        return crawl(targetUrl, clinicId, Map.of());
    }

    ModoodacCrawlPayload crawl(String targetUrl, Long clinicId, Map<String, String> options);
}
