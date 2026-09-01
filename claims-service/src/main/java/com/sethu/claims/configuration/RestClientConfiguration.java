package com.sethu.claims.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class RestClientConfiguration {

    @Bean
    RestClient.Builder restClientBuilder() {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofSeconds(2));
        return RestClient.builder().requestFactory(requestFactory);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
