package com.wingedsheep.gym.server.lifecycle

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/** Enables the optional snapshot reaper; it remains a no-op while TTL is disabled. */
@Configuration
@EnableScheduling
class SnapshotSchedulingConfig
