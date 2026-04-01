package com.medi.voice.medivoice.infrastructure.naver.service;

import java.util.Map;

public interface NaverPlaceCrawlingService {

    default NaverPlaceCrawlPayload crawl(String targetUrl, Long clinicId) {
        return crawl(targetUrl, clinicId, Map.of());
    }

    NaverPlaceCrawlPayload crawl(String targetUrl, Long clinicId, Map<String, String> options);
}
