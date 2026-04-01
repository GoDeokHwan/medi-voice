package com.medi.voice.medivoice.global.binder;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;


@Getter
@Setter
@ConfigurationProperties(prefix = "modoodac")
public class ModoodacProperty {
    private final Auth auth = new Auth();
    private final Crawl crawl = new Crawl();

    @Getter
    @Setter
    public static class Auth {
        private String email;
        private String password;
    }

    @Getter
    @Setter
    public static class Crawl {
        private Long clinicId;
        private String targetUrl;
    }
}
