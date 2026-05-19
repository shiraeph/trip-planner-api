package com.travel.travelplanner.config;

import java.util.concurrent.TimeUnit;

import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@Configuration
@EnableMongoAuditing
public class MongoConfig {

    /**
     * Recycle idle pooled connections before typical LB / NAT idle drops (avoids slow first
     * request after idle). minSize keeps one warm connection when traffic is bursty.
     */
    @Bean
    public MongoClientSettingsBuilderCustomizer mongoConnectionPoolCustomizer() {
        return builder -> builder.applyToConnectionPoolSettings(pool -> pool
                .minSize(1)
                .maxConnectionIdleTime(3, TimeUnit.MINUTES));
    }
}
