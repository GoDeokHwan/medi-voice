package com.medi.voice.medivoice.infrastructure.modoodac.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medi.voice.medivoice.domain.review.dto.ExternalReviewDto;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModoodacCrawlingService {

    private final String SNAPSHOT_DIRECTORY = "snapshots/modoodac";
    private final Pattern HOSPITAL_ID_PATTERN = Pattern.compile("/hospital/(\\d+)/");
    private final Pattern REVIEW_API_ID_PATTERN = Pattern.compile("/hospitals/(\\d+)/reviews/");
    private final String REVIEW_API_URL_TEMPLATE = "https://www.apis.modoodoc.com/api/mdduser/v1/hospitals/%d/reviews/";
    private final String LOGIN_API_URL = "https://www.apis.modoodoc.com/api/mdduser/v1/auth/login/email/";
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String email;
    private final String password;

    public ModoodacCrawlingService(String email, String password) {
        this(
                RestClient.builder()
                .defaultHeader(HttpHeaders.USER_AGENT,
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36")
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                .build(),
                new ObjectMapper(),
                email,
                password
        );
    }

    public ModoodacCrawlingService(
            RestClient restClient,
            ObjectMapper objectMapper,
            String email,
            String password
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.email = email;
        this.password = password;
    }

    public ModoodacCrawlPayload crawl(String targetUrl, Long clinicId) {
        return crawl(targetUrl, clinicId, Map.of());
    }

    public ModoodacCrawlPayload crawl(String targetUrl, Long clinicId, Map<String, String> options) {
        long hospitalId = resolveHospitalId(targetUrl, clinicId);
        String apiUrl = REVIEW_API_URL_TEMPLATE.formatted(hospitalId);
        Map<String, String> effectiveOptions = prepareOptions(options);
        String responseBody = fetchReviews(apiUrl, effectiveOptions);
        String snapshotPath = saveSnapshot(hospitalId, responseBody);
        List<String> warnings = new ArrayList<>();
        List<ExternalReviewDto> reviews = extractReviews(targetUrl, responseBody, warnings);

        if (reviews.isEmpty()) {
            warnings.add("리뷰 API 응답에 리뷰 목록이 비어 있습니다. Bearer 토큰이 필요할 수 있습니다.");
        }

        return new ModoodacCrawlPayload(
                targetUrl,
                responseBody,
                snapshotPath,
                reviews,
                List.copyOf(warnings)
        );
    }

    public String fetchHtml(String targetUrl) {
        return fetchReviews(targetUrl, Map.of());
    }

    public String fetchHtml(String targetUrl, Map<String, String> options) {
        return fetchReviews(targetUrl, options);
    }

    public String fetchReviews(String apiUrl, Map<String, String> options) {
        Map<String, Object> requestBody = buildReviewRequestBody(options);

        return restClient.post()
                .uri(apiUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> applyHeaders(headers, options))
                .body(requestBody)
                .retrieve()
                .body(String.class);
    }

    public String saveSnapshot(Long clinicId, String responseBody) {
        try {
            Path snapshotDirectory = Path.of(SNAPSHOT_DIRECTORY);
            Files.createDirectories(snapshotDirectory);

            String fileName = (clinicId != null ? clinicId : "unknown") + "-" + System.currentTimeMillis() + ".json";
            Path snapshotFile = snapshotDirectory.resolve(fileName);
            Files.writeString(snapshotFile, responseBody);

            return snapshotFile.toString();
        } catch (IOException exception) {
            throw new IllegalStateException("모두닥 스냅샷 저장에 실패했습니다.", exception);
        }
    }

    public List<ExternalReviewDto> extractReviews(String targetUrl, String responseBody, List<String> warnings) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode reviewsNode = root.path("reviews");

            if (reviewsNode.isMissingNode() || reviewsNode.isNull()) {
                warnings.add("reviews 필드가 비어 있습니다.");
                return List.of();
            }

            if (!reviewsNode.isArray()) {
                warnings.add("reviews 필드가 배열이 아닙니다.");
                return List.of();
            }

            List<ExternalReviewDto> reviews = new ArrayList<>();
            for (JsonNode reviewNode : reviewsNode) {
                reviews.add(toDto(reviewNode, targetUrl));
            }

            return List.copyOf(reviews);
        } catch (Exception exception) {
            warnings.add("리뷰 API 응답 파싱에 실패했습니다: " + exception.getMessage());
            return List.of();
        }
    }

    private ExternalReviewDto toDto(JsonNode node, String targetUrl) {
        return new ExternalReviewDto(
                readText(node, "id", "reviewId", "externalReviewId"),
                readText(node, "writer", "nickname", "name", "author"),
                readInteger(node, "score_total", "rating", "score"),
                readText(node, "review_contents", "content", "review", "comment"),
                readText(node, "treatment_category_name", "treatmentName", "treatment", "procedure"),
                readLocalDate(node, "treatment_at", "visited_at", "visit_date"),
                LocalDateTime.now(),
                targetUrl,
                buildMetadata(node)
        );
    }

    private String readText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.findValue(fieldName);
            if (value == null || value.isNull()) {
                continue;
            }

            if (value.isValueNode()) {
                return value.asText();
            }
        }

        return null;
    }

    private Integer readInteger(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.findValue(fieldName);
            if (value == null || value.isNull()) {
                continue;
            }

            if (value.isInt() || value.isLong() || value.isFloat() || value.isDouble() || value.isNumber()) {
                return value.asInt();
            }

            if (value.isTextual()) {
                try {
                    return Integer.parseInt(value.asText());
                } catch (NumberFormatException ignored) {
                    continue;
                }
            }
        }

        return null;
    }

    private LocalDate readLocalDate(JsonNode node, String... fieldNames) {
        String value = readText(node, fieldNames);
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(value.substring(0, 10));
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Object> buildMetadata(JsonNode node) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        putIfPresent(metadata, "source", "modoodac-review-api");
        putIfPresent(metadata, "suggest", readBoolean(node, "suggest"));
        putIfPresent(metadata, "doctorName", readText(node, "doctor_name"));
        putIfPresent(metadata, "hospitalName", readText(node, "hospital_name"));
        putIfPresent(metadata, "imageCount", readSize(node, "customerextraimageSet"));
        return Map.copyOf(metadata);
    }

    private Boolean readBoolean(JsonNode node, String fieldName) {
        JsonNode value = node.findValue(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asBoolean();
    }

    private Integer readSize(JsonNode node, String fieldName) {
        JsonNode value = node.findValue(fieldName);
        if (value == null || !value.isArray()) {
            return null;
        }
        return value.size();
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private Map<String, Object> buildReviewRequestBody(Map<String, String> options) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        putIfPresent(requestBody, "language", readOption(options, "language", "ko"));
        putIfPresent(requestBody, "doctor_id", readLongOption(options, "doctor_id"));
        putIfPresent(requestBody, "order_review", readNonBlankOption(options, "order_review"));
        putIfPresent(requestBody, "query", readNonBlankOption(options, "query"));
        putIfPresent(requestBody, "simple_or_complex_only", readBooleanOption(options, "simple_or_complex_only"));
        putIfPresent(requestBody, "tc", readNonBlankOption(options, "tc"));
        putIfPresent(requestBody, "treatment_group_s_id", readLongOption(options, "treatment_group_s_id"));
        putIfPresent(requestBody, "exclude_treatment_group_s_id", readLongOption(options, "exclude_treatment_group_s_id"));
        putIfPresent(requestBody, "mall_type", readNonBlankOption(options, "mall_type"));
        putIfPresent(requestBody, "exclude_mall_type", readNonBlankOption(options, "exclude_mall_type"));
        return requestBody;
    }

    private String readOption(Map<String, String> options, String key, String defaultValue) {
        if (options == null) {
            return defaultValue;
        }

        String value = options.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return value;
    }

    private String readNonBlankOption(Map<String, String> options, String key) {
        if (options == null) {
            return null;
        }

        String value = options.get(key);
        if (value == null || value.isBlank()) {
            return null;
        }

        return value;
    }

    private Long readLongOption(Map<String, String> options, String key) {
        if (options == null) {
            return null;
        }

        String value = options.get(key);
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Boolean readBooleanOption(Map<String, String> options, String key) {
        if (options == null) {
            return null;
        }

        String value = options.get(key);
        if (value == null || value.isBlank()) {
            return null;
        }

        return Boolean.parseBoolean(value) ? Boolean.TRUE : null;
    }

    private long resolveHospitalId(String targetUrl, Long clinicId) {
        if (clinicId != null) {
            return clinicId;
        }

        Matcher reviewApiMatcher = REVIEW_API_ID_PATTERN.matcher(targetUrl);
        if (reviewApiMatcher.find()) {
            return Long.parseLong(reviewApiMatcher.group(1));
        }

        Matcher hospitalPageMatcher = HOSPITAL_ID_PATTERN.matcher(targetUrl);
        if (hospitalPageMatcher.find()) {
            return Long.parseLong(hospitalPageMatcher.group(1));
        }

        throw new IllegalArgumentException("모두닥 병원 ID를 targetUrl에서 추출할 수 없습니다: " + targetUrl);
    }

    private Map<String, String> prepareOptions(Map<String, String> options) {
        Map<String, String> effectiveOptions = new LinkedHashMap<>();
        if (options != null && !options.isEmpty()) {
            effectiveOptions.putAll(options);
        }

        String bearerToken = resolveProvidedBearerToken(effectiveOptions);
        if (bearerToken == null) {
            bearerToken = loginWithCredentials(effectiveOptions);
        }

        if (bearerToken != null && !bearerToken.isBlank()) {
            effectiveOptions.put("bearerToken", bearerToken);
        }

        return Map.copyOf(effectiveOptions);
    }

    private String resolveProvidedBearerToken(Map<String, String> options) {
        String bearerToken = options.get("bearerToken");
        if (bearerToken == null || bearerToken.isBlank()) {
            bearerToken = options.get("accessToken");
        }
        if (bearerToken == null || bearerToken.isBlank()) {
            return null;
        }
        return stripBearerPrefix(bearerToken);
    }

    private String loginWithCredentials(Map<String, String> options) {
        Map<String, Object> loginRequestBody = new LinkedHashMap<>();
        loginRequestBody.put("email", email);
        loginRequestBody.put("password", password);
        loginRequestBody.put("is_in_mall_landing", false);

        String responseBody = restClient.post()
                .uri(LOGIN_API_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .body(loginRequestBody)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String accessToken = readText(root, "accessToken", "access_token");
            if (accessToken == null || accessToken.isBlank()) {
                throw new IllegalStateException("모두닥 로그인 응답에 access token이 없습니다.");
            }
            return stripBearerPrefix(accessToken);
        } catch (IOException exception) {
            throw new IllegalStateException("모두닥 로그인 응답 파싱에 실패했습니다.", exception);
        }
    }

    private void applyHeaders(HttpHeaders headers, Map<String, String> options) {
        if (options == null || options.isEmpty()) {
            return;
        }

        String cookie = options.get("cookie");
        if (cookie != null && !cookie.isBlank()) {
            headers.add(HttpHeaders.COOKIE, cookie);
        }

        String bearerToken = options.get("bearerToken");
        if (bearerToken == null || bearerToken.isBlank()) {
            bearerToken = options.get("accessToken");
        }
        if (bearerToken != null && !bearerToken.isBlank()) {
            headers.setBearerAuth(stripBearerPrefix(bearerToken));
        }

        String referer = options.get("referer");
        if (referer != null && !referer.isBlank()) {
            headers.add(HttpHeaders.REFERER, referer);
        }

        String userAgent = options.get("userAgent");
        if (userAgent != null && !userAgent.isBlank()) {
            headers.set(HttpHeaders.USER_AGENT, userAgent);
        }

        options.forEach((key, value) -> {
            if (key.startsWith("header.") && value != null && !value.isBlank()) {
                headers.add(key.substring("header.".length()), value);
            }
        });
    }

    private String stripBearerPrefix(String bearerToken) {
        String trimmed = bearerToken.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return trimmed.substring(7).trim();
        }
        return trimmed;
    }
}
