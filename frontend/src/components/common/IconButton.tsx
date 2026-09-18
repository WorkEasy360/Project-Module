import { forwardRef } from 'react'
import { Icon, type IconName } from './Icon'

interface IconButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  icon: IconName
  label: string
  size?: number
  active?: boolean
}

/** Icon-only button: always carries an aria-label and a matching tooltip (title). */
export const IconButton = forwardRef<HTMLButtonElement, IconButtonProps>(
  ({ icon, label, size = 18, active, className, ...rest }, ref) => (
    <button
      ref={ref}
      type="button"
      className={`icon-btn${active ? ' active' : ''}${className ? ` ${className}` : ''}`}
      aria-label={label}
      title={label}
      {...rest}
    >
      <Icon name={icon} size={size} />
    </button>
  ),
)
IconButton.displayName = 'IconButton'
