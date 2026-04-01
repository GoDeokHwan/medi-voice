package com.medi.voice.medivoice.domain.review.crawler;

import com.medi.voice.medivoice.domain.review.dto.CrawlCommand;
import com.medi.voice.medivoice.domain.review.dto.CrawlResult;
import com.medi.voice.medivoice.domain.review.dto.PlatformTypeEnum;
import com.medi.voice.medivoice.infrastructure.naver.service.NaverPlaceCrawlPayload;
import com.medi.voice.medivoice.infrastructure.naver.service.NaverPlaceCrawlingService;
import org.springframework.stereotype.Component;

@Component
public class NaverReviewCrawler implements ReviewCrawler {
    private final NaverPlaceCrawlingService naverPlaceCrawlingService;

    public NaverReviewCrawler(NaverPlaceCrawlingService naverPlaceCrawlingService) {
        this.naverPlaceCrawlingService = naverPlaceCrawlingService;
    }

    @Override
    public PlatformTypeEnum platformType() {
        return PlatformTypeEnum.NAVER_PLACE;
    }

    @Override
    public CrawlResult crawl(CrawlCommand command) {
        NaverPlaceCrawlPayload payload = naverPlaceCrawlingService.crawl(
                command.targetUrl(),
                command.clinicId(),
                command.options()
        );

        return new CrawlResult(
                platformType(),
                true,
                payload.reviews(),
                payload.warnings(),
                payload.snapshotPath()
        );
    }
}
