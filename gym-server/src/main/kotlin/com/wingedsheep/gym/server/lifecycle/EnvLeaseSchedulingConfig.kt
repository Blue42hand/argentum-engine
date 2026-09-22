package com.wingedsheep.gym.server.lifecycle

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/** Enables optional Gym lifecycle reapers; each remains a no-op while its TTL is disabled. */
@Configuration
@EnableScheduling
class EnvLeaseSchedulingConfig
