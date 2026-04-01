package com.medi.voice.medivoice.global.config;

import com.medi.voice.medivoice.domain.review.crawler.ModoodacReviewCrawler;
import com.medi.voice.medivoice.global.binder.ModoodacProperty;
import com.medi.voice.medivoice.infrastructure.modoodac.service.ModoodacCrawlingService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ModoodacProperty.class)
public class ModoodacConfig {
    private final ModoodacProperty modoodacProperty;

    public ModoodacConfig(ModoodacProperty modoodacProperty) {
        this.modoodacProperty = modoodacProperty;
    }

    @Bean
    public ModoodacCrawlingService modoodacCrawlingService() {
        return new ModoodacCrawlingService(
                modoodacProperty.getAuth().getEmail(),
                modoodacProperty.getAuth().getPassword()
        );
    }

    @Bean
    public ModoodacReviewCrawler modoodacReviewCrawler(ModoodacCrawlingService modoodacCrawlingService) {
        return new ModoodacReviewCrawler(modoodacCrawlingService);
    }
}
