package com.aipromptbridge.core.provider

/** Tunable retry behaviour. Defaults mirror the original Python app. Delays are in milliseconds. */
data class RetryPolicy(
    val maxAttempts: Int = 6,
    val serverErrorDelayMs: Long = 5_000,
    val emptyResponseDelayMs: Long = 2_000,
    val networkErrorDelayMs: Long = 1_000,
    /** Extra delay for HTTP 429 when there is only ONE key (rotating would just hammer the same key). */
    val singleKeyRateLimitDelayMs: Long = 2_000,
)
