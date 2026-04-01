package com.medi.voice.medivoice.domain.review.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.web.bind.annotation.GetMapping;

@Getter
@AllArgsConstructor
public enum PlatformTypeEnum {
    MODOODAC("모두닥")

    ;

    private String description;
}
