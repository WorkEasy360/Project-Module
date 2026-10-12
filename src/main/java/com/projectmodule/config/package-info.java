/**
 * Spring configuration: profiles, typed configuration properties, filters and OpenAPI setup.
 *
 * <p>Who is calling comes from the server-side session that "Sign in with Workeasy360" creates
 * ({@code com.projectmodule.security}) and is exposed as a request-scoped context; this module
 * stores no passwords and trusts no identity header.
 */
package com.projectmodule.config;
