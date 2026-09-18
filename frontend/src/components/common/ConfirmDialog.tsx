import { useState } from 'react'
import { Modal } from './Modal'
import { toUserMessage } from '../../api/errorMessage'
import { InlineError } from './ErrorState'

export function ConfirmDialog({
  title,
  message,
  confirmLabel = 'Confirm',
  danger = true,
  onConfirm,
  onClose,
}: {
  title: string
  message: string
  confirmLabel?: string
  danger?: boolean
  onConfirm: () => Promise<void>
  onClose: () => void
}) {
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleConfirm() {
    setSubmitting(true)
    setError(null)
    try {
      await onConfirm()
      onClose()
    } catch (err) {
      setError(toUserMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal title={title} onClose={onClose}>
      <p>{message}</p>
      {error && <InlineError message={error} />}
      <div className="form-actions">
        <button type="button" className="btn" onClick={onClose} disabled={submitting}>
          Cancel
        </button>
        <button
          type="button"
          className={danger ? 'btn btn-danger' : 'btn btn-primary'}
          onClick={handleConfirm}
          disabled={submitting}
        >
          {submitting ? 'Working…' : confirmLabel}
        </button>
      </div>
    </Modal>
  )
}
