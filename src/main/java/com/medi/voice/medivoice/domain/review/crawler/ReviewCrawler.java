package com.medi.voice.medivoice.domain.review.crawler;

import com.medi.voice.medivoice.domain.review.dto.CrawlCommand;
import com.medi.voice.medivoice.domain.review.dto.CrawlResult;
import com.medi.voice.medivoice.domain.review.dto.PlatformTypeEnum;

public interface ReviewCrawler {

    PlatformTypeEnum platformType();

    CrawlResult crawl(CrawlCommand command);
}
