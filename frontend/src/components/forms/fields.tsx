import type { ReactNode } from 'react'

export function TextField({
  id,
  label,
  value,
  onChange,
  required,
  type = 'text',
  hint,
  maxLength,
  placeholder,
}: {
  id: string
  label: string
  value: string
  onChange: (value: string) => void
  required?: boolean
  type?: 'text' | 'date' | 'number'
  hint?: string
  maxLength?: number
  placeholder?: string
}) {
  return (
    <div className="form-field">
      <label htmlFor={id}>
        {label}
        {required && ' *'}
      </label>
      <input
        id={id}
        type={type}
        value={value}
        required={required}
        maxLength={maxLength}
        placeholder={placeholder}
        onChange={(e) => onChange(e.target.value)}
      />
      {hint && <span className="hint">{hint}</span>}
    </div>
  )
}

export function TextareaField({
  id,
  label,
  value,
  onChange,
  required,
  hint,
}: {
  id: string
  label: string
  value: string
  onChange: (value: string) => void
  required?: boolean
  hint?: string
}) {
  return (
    <div className="form-field">
      <label htmlFor={id}>
        {label}
        {required && ' *'}
      </label>
      <textarea id={id} value={value} required={required} onChange={(e) => onChange(e.target.value)} />
      {hint && <span className="hint">{hint}</span>}
    </div>
  )
}

export function SelectField<T extends string>({
  id,
  label,
  value,
  onChange,
  options,
  required,
  allowEmpty,
  emptyLabel = 'Select…',
  hint,
  disabled,
}: {
  id: string
  label: string
  value: T | ''
  onChange: (value: T) => void
  options: { value: T; label: string }[]
  required?: boolean
  allowEmpty?: boolean
  emptyLabel?: string
  hint?: string
  disabled?: boolean
}) {
  return (
    <div className="form-field">
      <label htmlFor={id}>
        {label}
        {required && ' *'}
      </label>
      <select
        id={id}
        value={value}
        required={required}
        disabled={disabled}
        onChange={(e) => onChange(e.target.value as T)}
      >
        {allowEmpty && <option value="">{emptyLabel}</option>}
        {options.map((opt) => (
          <option key={opt.value} value={opt.value}>
            {opt.label}
          </option>
        ))}
      </select>
      {hint && <span className="hint">{hint}</span>}
    </div>
  )
}

export function CheckboxField({
  id,
  label,
  checked,
  onChange,
}: {
  id: string
  label: string
  checked: boolean
  onChange: (checked: boolean) => void
}) {
  return (
    <div className="checkbox-row">
      <input id={id} type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} />
      <label htmlFor={id}>{label}</label>
    </div>
  )
}

export function FormActions({ children }: { children: ReactNode }) {
  return <div className="form-actions">{children}</div>
}
