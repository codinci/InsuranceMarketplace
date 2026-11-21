package com.example.insurance.policyservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled} for the unpaid-policy job (M4b). common-messaging enables it too, for
 * the outbox relay; saying it here keeps this service's own job independent of that module.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
