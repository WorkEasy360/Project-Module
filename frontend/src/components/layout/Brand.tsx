/** The product's name and mark, in one place so they never drift apart. */

/** The name as plain text, for page titles and anywhere colour cannot be used. */
export const BRAND_NAME = 'WorkEasyProject'

/** The name with "Project" in the brand green, as in the official logo. */
export function BrandName() {
  return (
    <span className="brand-name">
      WorkEasy<span className="brand-accent">Project</span>
    </span>
  )
}

/** The official WorkEasy mark. */
export function BrandMark({ size = 30 }: { size?: number }) {
  return <img className="brand-mark" src="/brand/workeasy-mark.png" width={size} height={size} alt="" aria-hidden="true" />
}
