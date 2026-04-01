package com.medi.voice.medivoice.global.config;

import com.medi.voice.medivoice.domain.review.crawler.NaverReviewCrawler;
import com.medi.voice.medivoice.global.binder.NaverPlaceProperty;
import com.medi.voice.medivoice.infrastructure.naver.service.NaverPlaceCrawlingService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NaverPlaceProperty.class)
public class NaverPlaceConfig {

    @Bean
    public NaverPlaceCrawlingService naverPlaceCrawlingService(NaverPlaceProperty naverPlaceProperty) {
        return new NaverPlaceCrawlingService(naverPlaceProperty);
    }

    @Bean
    public NaverReviewCrawler naverReviewCrawler(NaverPlaceCrawlingService naverPlaceCrawlingService) {
        return new NaverReviewCrawler(naverPlaceCrawlingService);
    }
}
