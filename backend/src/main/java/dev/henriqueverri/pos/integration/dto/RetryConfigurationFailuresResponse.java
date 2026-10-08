package dev.henriqueverri.pos.integration.dto;

/** How many {@code FAILED/CONFIGURATION} events went back to {@code PENDING}. */
public record RetryConfigurationFailuresResponse(int retried) {}
