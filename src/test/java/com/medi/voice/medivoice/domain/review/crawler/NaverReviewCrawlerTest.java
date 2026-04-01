package com.medi.voice.medivoice.domain.review.crawler;

import com.medi.voice.medivoice.domain.review.dto.CrawlCommand;
import com.medi.voice.medivoice.domain.review.dto.CrawlResult;
import com.medi.voice.medivoice.domain.review.dto.PlatformTypeEnum;
import com.medi.voice.medivoice.global.binder.NaverPlaceProperty;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
})
class NaverReviewCrawlerTest {

    @Autowired
    private NaverReviewCrawler crawler;

    @Autowired
    private NaverPlaceProperty naverPlaceProperty;

    @Test
    void crawlHitsRealNaverPlaceGraphql() {
        String targetUrl = naverPlaceProperty.getCrawl().getTargetUrl();
        Long placeId = naverPlaceProperty.getCrawl().getPlaceId();

        assertTrue(hasText(targetUrl), "naver.place.crawl.target-url 설정이 필요합니다.");
        assertNotNull(placeId, "naver.place.crawl.place-id 설정이 필요합니다.");

        Map<String, String> options = new LinkedHashMap<>();
        putIfHasText(options, "referer", naverPlaceProperty.getCrawl().getReferer());
        putIfPresent(options, "display", naverPlaceProperty.getCrawl().getDisplay());
        putIfPresent(options, "maxPages", naverPlaceProperty.getCrawl().getMaxPages());
        putIfHasText(options, "businessType", naverPlaceProperty.getCrawl().getBusinessType());
        putIfHasText(options, "bookingBusinessId", naverPlaceProperty.getCrawl().getBookingBusinessId());
        putIfHasText(options, "item", naverPlaceProperty.getCrawl().getItem());
        putIfHasText(options, "sort", naverPlaceProperty.getCrawl().getSort());

        if (!naverPlaceProperty.getCrawl().getCidList().isEmpty()) {
            options.put("cidList", String.join(",", naverPlaceProperty.getCrawl().getCidList()));
        }

        CrawlCommand command = new CrawlCommand(
                placeId,
                PlatformTypeEnum.NAVER_PLACE,
                targetUrl,
                false,
                Map.copyOf(options)
        );

        CrawlResult result = crawler.crawl(command);

        System.out.println("targetUrl = " + targetUrl);
        System.out.println("snapshotPath = " + result.rawSnapshotPath());
        System.out.println("warnings = " + result.warnings());
        System.out.println("reviewCount = " + result.reviews().size());

        assertEquals(PlatformTypeEnum.NAVER_PLACE, result.platformType());
        assertTrue(result.success());
        assertNotNull(result.rawSnapshotPath());
        assertTrue(result.reviews().size() > 0, "실제 네이버 플레이스 리뷰가 1개 이상 파싱되어야 합니다.");
    }

    @Test
    void platformTypeReturnsNaverPlace() {
        assertEquals(PlatformTypeEnum.NAVER_PLACE, crawler.platformType());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void putIfHasText(Map<String, String> options, String key, String value) {
        if (hasText(value)) {
            options.put(key, value);
        }
    }

    private void putIfPresent(Map<String, String> options, String key, Object value) {
        if (value != null) {
            options.put(key, String.valueOf(value));
        }
    }
}
