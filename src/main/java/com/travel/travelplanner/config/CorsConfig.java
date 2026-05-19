package com.travel.travelplanner.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

  /**
   * Comma-separated Spring CORS patterns, e.g. {@code https://app.example.com,https://*.vercel.app}
   * <p>Override via env {@code APP_CORS_ALLOWED_ORIGIN_PATTERNS} or property
   * {@code app.cors.allowed-origin-patterns}.
   */
  @Value("${app.cors.allowed-origin-patterns}")
  private String allowedOriginPatternsRaw;

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    String[] patterns = Arrays.stream(allowedOriginPatternsRaw.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toArray(String[]::new);

    CorsRegistration reg = registry.addMapping("/api/**")
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("*");

    if (patterns.length > 0) {
      reg.allowedOriginPatterns(patterns);
    } else {
      reg.allowedOriginPatterns("http://localhost:*");
    }
  }
}
