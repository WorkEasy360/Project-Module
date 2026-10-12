import type { AccessProblem } from '../context/sessionStore'

/** Reasons the backend gives for `workeasy360_access_ended`, mapped to plain-language text. */
const ENDED_MESSAGES: Record<string, string> = {
  expired: "Your organization's Easy Project subscription has ended. An owner can renew it in Workeasy360.",
  not_entitled: "Your organization's Workeasy360 plan does not include Easy Project.",
  suspended: "Your organization's Easy Project access is suspended. Contact your administrator.",
  membership_inactive: 'Your account or membership in this organization is no longer active. Contact your administrator.',
  no_seat: 'You no longer have an Easy Project seat. Ask an administrator to assign one.',
  read_only: "Your organization's Easy Project subscription is read-only: you can view but not change.",
}

export const ACCESS_ENDED_GENERIC = 'Your access to Easy Project has ended. Contact your administrator or check Workeasy360.'
export const ACCESS_UNVERIFIED_MESSAGE =
  "Workeasy360 can't confirm your organization's access to Easy Project right now. This is usually temporary. Try again in a moment."
export const READ_ONLY_MESSAGE =
  "Your organization's Easy Project subscription is read-only: you can view but not change."

/**
 * Own-property lookup only. The keys come from a URL or a server response, so `__proto__`,
 * `constructor` and friends must not resolve to anything on `Object.prototype`.
 */
function lookup(table: Record<string, string>, key: string): string | undefined {
  return Object.hasOwn(table, key) ? table[key] : undefined
}

export function accessProblemMessage(code: string, reason: string | null): string {
  if (code === 'workeasy360_access_unverified') return ACCESS_UNVERIFIED_MESSAGE
  if (code === 'workeasy360_read_only') return READ_ONLY_MESSAGE
  return (reason && lookup(ENDED_MESSAGES, reason)) || ACCESS_ENDED_GENERIC
}

export function buildAccessProblem(code: string, reason: unknown): AccessProblem {
  const r = typeof reason === 'string' ? reason : null
  return { code, reason: r, message: accessProblemMessage(code, r) }
}

/** Sign-in failure codes the backend sends back as `#/sign-in?sso_error=<code>`. */
export const SSO_ERROR_MESSAGES: Record<string, string> = {
  cancelled: 'Sign-in was cancelled. You can start again whenever you are ready.',
  expired: 'That sign-in took too long or was already used. Start again.',
  unavailable: 'Workeasy360 sign-in is not available right now. Try again shortly.',
  refused: 'Workeasy360 did not accept that sign-in. Start again, and contact your administrator if it keeps happening.',
  rate_limited: 'Too many sign-in attempts. Wait a few minutes, then try again.',
  not_entitled: 'Your organization does not have an Easy Project subscription. Ask an owner to add Easy Project in Workeasy360.',
  no_seat: 'You do not have an Easy Project seat. Ask an administrator to assign one in Workeasy360.',
  provider_membership_inactive: 'Your membership in this organization is not active. Contact your administrator.',
  organization_inactive: "Your organization's Workeasy360 account is not active. Contact your administrator.",
  access_policy: "Your organization's sign-in policy does not allow this sign-in. Contact your administrator.",
  email_missing: 'Workeasy360 did not share an email address for your account, so we cannot sign you in. Contact your administrator.',
  email_unverified: 'Your email address is not verified in Workeasy360. Verify it there, then try again.',
  account_conflict: 'This sign-in conflicts with an existing Easy Project account. Contact your administrator to resolve it.',
  account_disabled: 'Your Easy Project account is disabled. Contact your administrator.',
  membership_inactive: 'Your membership in this organization is no longer active. Contact your administrator.',
  wrong_product: 'That sign-in was for a different product. Start again from the Workeasy360 launcher.',
  wrong_organization:
    'You signed in to a different organization than the one you chose. Start again from the Workeasy360 launcher.',
}

export function ssoErrorMessage(code: string): string {
  return lookup(SSO_ERROR_MESSAGES, code) ?? SSO_ERROR_MESSAGES.refused
}
