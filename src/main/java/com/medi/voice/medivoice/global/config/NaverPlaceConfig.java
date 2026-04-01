package com.medi.voice.medivoice.global.config;

import com.medi.voice.medivoice.global.binder.NaverPlaceProperty;
import com.medi.voice.medivoice.infrastructure.naver.service.NaverPlaceCrawlingService;
import com.medi.voice.medivoice.infrastructure.naver.service.v1.NaverPlaceCrawlingServiceV1;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NaverPlaceProperty.class)
public class NaverPlaceConfig {

    @Bean
    public NaverPlaceCrawlingService naverPlaceCrawlingService(NaverPlaceProperty naverPlaceProperty) {
        return new NaverPlaceCrawlingServiceV1(naverPlaceProperty);
    }
}
