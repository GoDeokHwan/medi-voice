package com.medi.voice.medivoice.infrastructure.naver.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.medi.voice.medivoice.domain.review.dto.ExternalReviewDto;
import com.medi.voice.medivoice.global.binder.NaverPlaceProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NaverPlaceCrawlingService {

    private static final String SNAPSHOT_DIRECTORY = "snapshots/naver-place";
    private static final String GRAPHQL_URL = "https://pcmap-api.place.naver.com/graphql";
    private static final Pattern PLACE_ID_PATTERN = Pattern.compile("/place/(\\d+)");
    private static final String PLACE_ID_OPTION = "placeId";
    private static final String DEFAULT_REFERER = "https://map.naver.com/";
    private static final String VISITOR_REVIEWS_QUERY = """
            query getVisitorReviews($input: VisitorReviewsInput) {
              visitorReviews(input: $input) {
                items {
                  id
                  rating
                  author {
                    id
                    nickname
                    imageUrl
                    __typename
                  }
                  body
                  thumbnail
                  media {
                    type
                    thumbnail
                    class
                    __typename
                  }
                  visitCount
                  viewCount
                  visited
                  created
                  reply {
                    body
                    created
                    replyTitle
                    __typename
                  }
                  originType
                  item {
                    name
                    code
                    options
                    __typename
                  }
                  businessName
                  votedKeywords {
                    code
                    displayName
                    __typename
                  }
                  __typename
                }
                total
                __typename
              }
            }
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final NaverPlaceProperty naverPlaceProperty;

    public NaverPlaceCrawlingService(NaverPlaceProperty naverPlaceProperty) {
        this(
                RestClient.builder()
                        .defaultHeader(HttpHeaders.USER_AGENT,
                                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                                        + "(KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36")
                        .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                        .build(),
                new ObjectMapper(),
                naverPlaceProperty
        );
    }

    public NaverPlaceCrawlingService(
            RestClient restClient,
            ObjectMapper objectMapper,
            NaverPlaceProperty naverPlaceProperty
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.naverPlaceProperty = naverPlaceProperty;
    }

    public NaverPlaceCrawlPayload crawl(String targetUrl, Long clinicId) {
        return crawl(targetUrl, clinicId, Map.of());
    }

    public NaverPlaceCrawlPayload crawl(String targetUrl, Long clinicId, Map<String, String> options) {
        String effectiveTargetUrl = firstNonBlank(targetUrl, naverPlaceProperty.getCrawl().getTargetUrl(), DEFAULT_REFERER);
        String placeId = resolvePlaceId(effectiveTargetUrl, clinicId, options);
        int maxPages = readIntOption(options, "maxPages", defaultInt(naverPlaceProperty.getCrawl().getMaxPages(), 1));
        ArrayNode pageSnapshots = objectMapper.createArrayNode();
        List<String> warnings = new ArrayList<>();
        List<ExternalReviewDto> reviews = new ArrayList<>();
        Integer total = null;

        for (int page = 1; page <= maxPages; page++) {
            String responseBody = fetchReviews(effectiveTargetUrl, placeId, page, options);
            pageSnapshots.add(readSnapshotNode(responseBody, warnings));

            ReviewPage reviewPage = extractReviewPage(effectiveTargetUrl, responseBody, warnings);
            if (reviewPage.total() != null) {
                total = reviewPage.total();
            }
            if (reviewPage.reviews().isEmpty()) {
                break;
            }

            reviews.addAll(reviewPage.reviews());
            if (total != null && reviews.size() >= total) {
                break;
            }
        }

        if (reviews.isEmpty()) {
            warnings.add("네이버 플레이스 리뷰 응답에서 리뷰 목록을 찾지 못했습니다.");
        }

        String snapshotPath = saveSnapshot(placeId, pageSnapshots.toPrettyString());
        return new NaverPlaceCrawlPayload(
                effectiveTargetUrl,
                pageSnapshots.toPrettyString(),
                snapshotPath,
                List.copyOf(reviews),
                List.copyOf(warnings)
        );
    }

    public String fetchReviews(String targetUrl, String placeId, int page, Map<String, String> options) {
        List<Map<String, Object>> payload = List.of(buildRequest(targetUrl, placeId, page, options));

        return restClient.post()
                .uri(GRAPHQL_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> applyHeaders(headers, targetUrl, options))
                .body(payload)
                .retrieve()
                .body(String.class);
    }

    private Map<String, Object> buildRequest(String targetUrl, String placeId, int page, Map<String, String> options) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("operationName", "getVisitorReviews");
        request.put("query", VISITOR_REVIEWS_QUERY);
        request.put("variables", buildVariables(targetUrl, placeId, page, options));
        return request;
    }

    private Map<String, Object> buildVariables(String targetUrl, String placeId, int page, Map<String, String> options) {
        Map<String, Object> variables = new LinkedHashMap<>();
        Map<String, Object> input = new LinkedHashMap<>();

        variables.put("id", placeId);
        input.put("bookingBusinessId", readOption(options, "bookingBusinessId",
                firstNonBlank(naverPlaceProperty.getCrawl().getBookingBusinessId(), "null")));
        input.put("businessId", placeId);
        input.put("businessType", readOption(options, "businessType",
                firstNonBlank(naverPlaceProperty.getCrawl().getBusinessType(), "place")));

        List<String> cidList = resolveCidList(options);
        if (!cidList.isEmpty()) {
            input.put("cidList", cidList);
        }

        input.put("display", readIntOption(options, "display", defaultInt(naverPlaceProperty.getCrawl().getDisplay(), 10)));
        input.put("getAuthorInfo", readBooleanOption(options, "getAuthorInfo", true));
        input.put("includeContent", readBooleanOption(options, "includeContent", true));
        input.put("includeReceiptPhotos", readBooleanOption(options, "includeReceiptPhotos", true));
        input.put("isPhotoUsed", readBooleanOption(options, "isPhotoUsed", false));
        input.put("item", readOption(options, "item", firstNonBlank(naverPlaceProperty.getCrawl().getItem(), "0")));
        input.put("page", page);

        String sort = readNonBlankOption(options, "sort");
        if (sort == null) {
            sort = naverPlaceProperty.getCrawl().getSort();
        }
        if (sort != null && !sort.isBlank()) {
            input.put("sort", sort);
        }

        String referer = readNonBlankOption(options, "referer");
        if (referer == null || referer.isBlank()) {
            referer = firstNonBlank(naverPlaceProperty.getCrawl().getReferer(), targetUrl);
        }
        if (referer != null && !referer.isBlank()) {
            variables.put("referer", referer);
        }

        variables.put("input", input);
        return variables;
    }

    private ReviewPage extractReviewPage(String targetUrl, String responseBody, List<String> warnings) {
        try {
            JsonNode responseRoot = objectMapper.readTree(responseBody);
            JsonNode graphqlRoot = unwrapGraphqlRoot(responseRoot);
            JsonNode visitorReviewsNode = graphqlRoot.path("data").path("visitorReviews");

            if (visitorReviewsNode.isMissingNode() || visitorReviewsNode.isNull()) {
                warnings.add("visitorReviews 필드가 비어 있습니다.");
                return new ReviewPage(List.of(), null);
            }

            JsonNode itemsNode = visitorReviewsNode.path("items");
            if (!itemsNode.isArray()) {
                warnings.add("visitorReviews.items 필드가 배열이 아닙니다.");
                return new ReviewPage(List.of(), readInteger(visitorReviewsNode, "total"));
            }

            List<ExternalReviewDto> reviews = new ArrayList<>();
            for (JsonNode itemNode : itemsNode) {
                reviews.add(toDto(itemNode, targetUrl));
            }

            return new ReviewPage(List.copyOf(reviews), readInteger(visitorReviewsNode, "total"));
        } catch (Exception exception) {
            warnings.add("네이버 플레이스 리뷰 응답 파싱에 실패했습니다: " + exception.getMessage());
            return new ReviewPage(List.of(), null);
        }
    }

    private JsonNode unwrapGraphqlRoot(JsonNode responseRoot) {
        if (responseRoot.isArray() && !responseRoot.isEmpty()) {
            return responseRoot.get(0);
        }
        return responseRoot;
    }

    private ExternalReviewDto toDto(JsonNode node, String targetUrl) {
        return new ExternalReviewDto(
                readText(node, "id"),
                readText(node.path("author"), "nickname"),
                readInteger(node, "rating"),
                readText(node, "body"),
                readText(node.path("item"), "name"),
                readLocalDate(node, "visited", "created"),
                LocalDateTime.now(),
                targetUrl,
                buildMetadata(node)
        );
    }

    private Map<String, Object> buildMetadata(JsonNode node) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        putIfPresent(metadata, "source", "naver-place-graphql");
        putIfPresent(metadata, "businessName", readText(node, "businessName"));
        putIfPresent(metadata, "originType", readText(node, "originType"));
        putIfPresent(metadata, "visitCount", readText(node, "visitCount"));
        putIfPresent(metadata, "viewCount", readText(node, "viewCount"));
        putIfPresent(metadata, "replyBody", readText(node.path("reply"), "body"));
        putIfPresent(metadata, "thumbnail", readText(node, "thumbnail"));
        putIfPresent(metadata, "mediaCount", readArraySize(node, "media"));
        putIfPresent(metadata, "keywords", readKeywordDisplayNames(node.path("votedKeywords")));
        return Map.copyOf(metadata);
    }

    private List<String> readKeywordDisplayNames(JsonNode votedKeywords) {
        if (!votedKeywords.isArray()) {
            return List.of();
        }

        List<String> keywords = new ArrayList<>();
        for (JsonNode keywordNode : votedKeywords) {
            String displayName = readText(keywordNode, "displayName");
            if (displayName != null && !displayName.isBlank()) {
                keywords.add(displayName);
            }
        }
        return List.copyOf(keywords);
    }

    private String resolvePlaceId(String targetUrl, Long clinicId, Map<String, String> options) {
        String configuredPlaceId = readNonBlankOption(options, PLACE_ID_OPTION);
        if (configuredPlaceId != null) {
            return configuredPlaceId;
        }

        if (naverPlaceProperty.getCrawl().getPlaceId() != null) {
            return String.valueOf(naverPlaceProperty.getCrawl().getPlaceId());
        }

        if (clinicId != null) {
            return String.valueOf(clinicId);
        }

        Matcher matcher = PLACE_ID_PATTERN.matcher(targetUrl);
        if (matcher.find()) {
            return matcher.group(1);
        }

        throw new IllegalArgumentException("네이버 플레이스 placeId를 targetUrl에서 추출할 수 없습니다: " + targetUrl);
    }

    private void applyHeaders(HttpHeaders headers, String targetUrl, Map<String, String> options) {
        headers.set(HttpHeaders.ORIGIN, "https://map.naver.com");
        headers.set(HttpHeaders.REFERER,
                firstNonBlank(readNonBlankOption(options, "referer"), naverPlaceProperty.getCrawl().getReferer(), targetUrl, DEFAULT_REFERER));

        String userAgent = readNonBlankOption(options, "userAgent");
        if (userAgent != null) {
            headers.set(HttpHeaders.USER_AGENT, userAgent);
        }

        if (options == null || options.isEmpty()) {
            return;
        }

        options.forEach((key, value) -> {
            if (key.startsWith("header.") && value != null && !value.isBlank()) {
                headers.add(key.substring("header.".length()), value);
            }
        });
    }

    private String saveSnapshot(String placeId, String responseBody) {
        try {
            Path snapshotDirectory = Path.of(SNAPSHOT_DIRECTORY);
            Files.createDirectories(snapshotDirectory);

            String fileName = placeId + "-" + System.currentTimeMillis() + ".json";
            Path snapshotFile = snapshotDirectory.resolve(fileName);
            Files.writeString(snapshotFile, responseBody);

            return snapshotFile.toString();
        } catch (IOException exception) {
            throw new IllegalStateException("네이버 플레이스 스냅샷 저장에 실패했습니다.", exception);
        }
    }

    private JsonNode readSnapshotNode(String responseBody, List<String> warnings) {
        try {
            return objectMapper.readTree(responseBody);
        } catch (IOException exception) {
            warnings.add("네이버 플레이스 응답을 스냅샷 JSON으로 파싱하지 못했습니다: " + exception.getMessage());
            return objectMapper.createObjectNode().put("rawBody", responseBody);
        }
    }

    private String readText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value.isMissingNode() || value.isNull()) {
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
            JsonNode value = node.path(fieldName);
            if (value.isMissingNode() || value.isNull()) {
                continue;
            }

            if (value.isNumber()) {
                return value.asInt();
            }

            if (value.isTextual()) {
                try {
                    return Integer.parseInt(value.asText());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private LocalDate readLocalDate(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = readText(node, fieldName);
            if (value == null || value.isBlank()) {
                continue;
            }

            try {
                return LocalDate.parse(value.substring(0, 10));
            } catch (DateTimeParseException | IndexOutOfBoundsException ignored) {
                // Continue below.
            }

            try {
                return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDate();
            } catch (DateTimeParseException ignored) {
                // Continue below.
            }
        }
        return null;
    }

    private Integer readArraySize(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (!value.isArray()) {
            return null;
        }
        return value.size();
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }

        if (value instanceof String stringValue && stringValue.isBlank()) {
            return;
        }

        if (value instanceof List<?> listValue && listValue.isEmpty()) {
            return;
        }

        target.put(key, value);
    }

    private int readIntOption(Map<String, String> options, String key, int defaultValue) {
        String value = readNonBlankOption(options, key);
        if (value == null) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private boolean readBooleanOption(Map<String, String> options, String key, boolean defaultValue) {
        String value = readNonBlankOption(options, key);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    private String readOption(Map<String, String> options, String key, String defaultValue) {
        String value = readNonBlankOption(options, key);
        if (value == null) {
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

    private List<String> resolveCidList(Map<String, String> options) {
        String cidListOption = readNonBlankOption(options, "cidList");
        if (cidListOption != null) {
            String[] tokens = cidListOption.split(",");
            List<String> cidList = new ArrayList<>();
            for (String token : tokens) {
                if (!token.isBlank()) {
                    cidList.add(token.trim());
                }
            }
            return List.copyOf(cidList);
        }

        if (!naverPlaceProperty.getCrawl().getCidList().isEmpty()) {
            return List.copyOf(naverPlaceProperty.getCrawl().getCidList());
        }

        return List.of();
    }

    private int defaultInt(Integer value, int defaultValue) {
        return value != null ? value : defaultValue;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private record ReviewPage(
            List<ExternalReviewDto> reviews,
            Integer total
    ) {
    }
}
