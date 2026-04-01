package com.medi.voice.medivoice.infrastructure.naver.service;

import com.medi.voice.medivoice.domain.review.dto.ExternalReviewDto;

import java.util.List;

public record NaverPlaceCrawlPayload(
        String targetUrl,
        String rawBody,
        String snapshotPath,
        List<ExternalReviewDto> reviews,
        List<String> warnings
) {
}
