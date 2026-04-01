package com.medi.voice.medivoice.global.binder;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "naver.place")
public class NaverPlaceProperty {
    private final Crawl crawl = new Crawl();

    @Getter
    @Setter
    public static class Crawl {
        private Long placeId;
        private String targetUrl;
        private String referer;
        private Integer display = 10;
        private Integer maxPages = 1;
        private String businessType = "place";
        private String bookingBusinessId = "null";
        private String item = "0";
        private String sort = "recent";
        private final List<String> cidList = new ArrayList<>();
    }
}
