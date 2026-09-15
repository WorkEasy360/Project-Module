/**
 * AI layer: AIRecommendation, AIAction and AIApproval.
 *
 * <p><strong>Safety boundary.</strong> AI must never reach the database directly. Every AI
 * operation follows:
 * {@code AI -> Tool -> Authorization -> Validation -> Business Logic -> Database -> Audit}.
 *
 * <p>This package therefore contains no repository, no {@code EntityManager} and no entity
 * mapping. Its planned subpackages are {@code tools} (the only entry point AI code has),
 * {@code authorization} and {@code validation}. AI tools build the same commands an HTTP
 * request builds, so AI is authorized and validated on exactly the same path as a human user.
 *
 * <p>AI is an enhancement, never a requirement: all manual functionality must work with AI
 * disabled. See {@code docs/project/07-AI.md}.
 */
package com.projectmodule.ai;
