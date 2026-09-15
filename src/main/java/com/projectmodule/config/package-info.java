/**
 * Spring configuration: profiles, typed configuration properties, filters and OpenAPI setup.
 *
 * <p>This module does not authenticate anyone. Identity arrives already authenticated from
 * the upstream gateway and is exposed as a request-scoped context; authentication itself is
 * owned by another module.
 */
package com.projectmodule.config;
