package com.medi.voice.medivoice.domain.review.dto;

import java.util.List;

public record CrawlResult(
        PlatformTypeEnum platformType
        , boolean success
        , List<ExternalReviewDto> reviews
        , List<String> warnings
        , String rawSnapshotPath
) {
}
