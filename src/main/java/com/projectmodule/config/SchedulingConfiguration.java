package com.projectmodule.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on scheduled jobs. Kept apart so controller slice tests never start them. */
@Configuration
@EnableScheduling
public class SchedulingConfiguration {
}
