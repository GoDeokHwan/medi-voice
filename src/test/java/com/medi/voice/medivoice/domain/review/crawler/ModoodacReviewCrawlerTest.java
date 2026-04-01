package com.medi.voice.medivoice.domain.review.crawler;

import com.medi.voice.medivoice.domain.review.dto.CrawlCommand;
import com.medi.voice.medivoice.domain.review.dto.CrawlResult;
import com.medi.voice.medivoice.domain.review.dto.PlatformTypeEnum;
import com.medi.voice.medivoice.global.binder.ModoodacProperty;
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
class ModoodacReviewCrawlerTest {

    @Autowired
    private ModoodacReviewCrawler crawler;

    @Autowired
    private ModoodacProperty modoodacProperty;

    @Test
    void crawlHitsRealModoodacPage() {
        String targetUrl = modoodacProperty.getCrawl().getTargetUrl();
        Long clinicId = modoodacProperty.getCrawl().getClinicId();

        Map<String, String> options = new LinkedHashMap<>();
        CrawlCommand command = new CrawlCommand(
                clinicId,
                PlatformTypeEnum.MODOODAC,
                targetUrl,
                false,
                Map.copyOf(options)
        );

        CrawlResult result = crawler.crawl(command);

        System.out.println("targetUrl = " + targetUrl);
        System.out.println("snapshotPath = " + result.rawSnapshotPath());
        System.out.println("warnings = " + result.warnings());
        System.out.println("reviewCount = " + result.reviews().size());

        assertEquals(PlatformTypeEnum.MODOODAC, result.platformType());
        assertTrue(result.success());
        assertNotNull(result.rawSnapshotPath());
        assertTrue(result.reviews().size() > 0, "실제 모두닥 리뷰가 1개 이상 파싱되어야 합니다. Bearer 토큰이 필요할 수 있습니다.");
    }

    @Test
    void platformTypeReturnsModoodac() {
        assertEquals(PlatformTypeEnum.MODOODAC, crawler.platformType());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
