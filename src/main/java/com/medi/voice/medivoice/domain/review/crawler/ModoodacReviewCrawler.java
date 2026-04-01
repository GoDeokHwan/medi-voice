package com.medi.voice.medivoice.domain.review.crawler;

import com.medi.voice.medivoice.domain.review.dto.CrawlCommand;
import com.medi.voice.medivoice.domain.review.dto.CrawlResult;
import com.medi.voice.medivoice.domain.review.dto.PlatformTypeEnum;
import com.medi.voice.medivoice.infrastructure.modoodac.service.ModoodacCrawlPayload;
import com.medi.voice.medivoice.infrastructure.modoodac.service.ModoodacCrawlingService;

public class ModoodacReviewCrawler implements ReviewCrawler {
    private final ModoodacCrawlingService modoodacCrawlingService;

    public ModoodacReviewCrawler(ModoodacCrawlingService modoodacCrawlingService) {
        this.modoodacCrawlingService = modoodacCrawlingService;
    }

    @Override
    public PlatformTypeEnum platformType() {
        return PlatformTypeEnum.MODOODAC;
    }

    @Override
    public CrawlResult crawl(CrawlCommand command) {
        ModoodacCrawlPayload payload = modoodacCrawlingService.crawl(
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
