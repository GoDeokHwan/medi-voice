package com.medi.voice.medivoice.domain.review.dto;

import java.util.Map;

public record CrawlCommand(
        Long clinicId,
        PlatformTypeEnum platformType,
        String targetUrl,
        boolean forceRefresh,
        Map<String, String> options
) {
}
