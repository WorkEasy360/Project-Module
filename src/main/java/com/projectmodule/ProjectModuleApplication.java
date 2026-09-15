package com.projectmodule;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Project Module.
 *
 * <p>This module owns project planning, execution and tracking only. Authentication,
 * user management, organization management and all other company modules are owned
 * elsewhere and are reached through the integration layer. See {@code docs/project/}.
 */
@SpringBootApplication
public class ProjectModuleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProjectModuleApplication.class, args);
    }
}
