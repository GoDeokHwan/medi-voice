package com.medi.voice.medivoice.domain.review.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PlatformTypeEnum {
    MODOODAC("모두닥"),
    NAVER_PLACE("네이버 플레이스")

    ;

    private String description;
}
