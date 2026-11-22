package com.example.insurance.claimservice.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers the review thresholds so they can be injected (PLAN 4, 9.1). */
@Configuration
@EnableConfigurationProperties(ReviewProperties.class)
public class ReviewConfig {}
