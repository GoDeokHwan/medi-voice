package com.medi.voice.medivoice.domain.review.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public record ExternalReviewDto(
        String externalReviewId,
        String authorName,
        Integer rating,
        String content,
        String treatmentName,
        LocalDate visitedAt,
        LocalDateTime crawledAt,
        String sourceUrl,
        Map<String, Object> metadata
) {
}
