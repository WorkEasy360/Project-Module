/** The product's name and mark, in one place so they never drift apart. */

/** The name as plain text, for page titles and anywhere colour cannot be used. */
export const BRAND_NAME = 'WorkEasy360 Operations'

/** The company name with "360" in the brand green, as in the official logo. */
export function BrandName() {
  return (
    <span className="brand-name">
      WorkEasy<span className="brand-accent">360</span>
    </span>
  )
}

/** The green "Operations" badge that names this product next to the company logo. */
export function OperationsBadge({ size = 'md' }: { size?: 'sm' | 'md' | 'lg' }) {
  return (
    <span className={`ops-badge ops-badge-${size}`}>
      <svg viewBox="0 0 20 20" width="1em" height="1em" fill="none" stroke="currentColor" strokeWidth={1.7} strokeLinejoin="round" aria-hidden="true">
        <path d="M10 2.8l7 3.6-7 3.6-7-3.6z" />
        <path d="M3 10l7 3.6 7-3.6M3 13.6l7 3.6 7-3.6" />
      </svg>
      Operations
    </span>
  )
}

/** The official WorkEasy mark. */
export function BrandMark({ size = 30 }: { size?: number }) {
  return <img className="brand-mark" src="/brand/workeasy-mark.png" width={size} height={size} alt="" aria-hidden="true" />
}

/** Logo, name and badge together, as on the sign-in page and the sidebar. */
export function BrandLockup({ markSize = 34, badge = 'md' }: { markSize?: number; badge?: 'sm' | 'md' | 'lg' }) {
  return (
    <span className="brand-lockup">
      <span className="brand-lockup-row">
        <BrandMark size={markSize} />
        <BrandName />
      </span>
      <OperationsBadge size={badge} />
    </span>
  )
}
