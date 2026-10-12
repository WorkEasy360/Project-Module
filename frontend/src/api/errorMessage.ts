import { ApiError, NetworkError } from './client'
import { accessProblemMessage } from './sessionMessages'

/**
 * Turns any error thrown by the API client into a safe, user-facing message. Never surfaces a
 * stack trace, SQL text, or class name — the backend's own ProblemDetail.detail is already
 * written for humans (GlobalExceptionHandler), so we prefer it; everything else falls back to a
 * generic message.
 */
export function toUserMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.isUnauthenticated) {
      return 'Your session has ended. Sign in again to continue.'
    }
    if (error.isForbidden) {
      const code = error.problem?.code
      if (code === 'csrf_invalid') {
        return 'Your session security token was out of date. Reload the page and try again.'
      }
      if (typeof code === 'string' && code.startsWith('workeasy360_')) {
        return accessProblemMessage(code, error.problem?.reason ?? null)
      }
      return error.problem?.detail || "You don't have permission to do this."
    }
    if (error.isConflict) {
      return 'This item was changed by someone else. Reload it and try again.'
    }
    if (error.isIntegrationUnavailable) {
      return error.problem?.detail || 'This service is currently unavailable.'
    }
    if (error.problem?.errors?.length) {
      return error.problem.errors.map((e) => `${e.field}: ${e.message}`).join('; ')
    }
    return error.problem?.detail || error.message
  }
  if (error instanceof NetworkError) {
    return error.message
  }
  return 'Something went wrong. Please try again.'
}
