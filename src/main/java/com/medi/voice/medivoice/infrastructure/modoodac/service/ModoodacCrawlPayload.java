package com.medi.voice.medivoice.infrastructure.modoodac.service;

import com.medi.voice.medivoice.domain.review.dto.ExternalReviewDto;

import java.util.List;

public record ModoodacCrawlPayload(
        String targetUrl,
        String rawHtml,
        String snapshotPath,
        List<ExternalReviewDto> reviews,
        List<String> warnings
) {
}
