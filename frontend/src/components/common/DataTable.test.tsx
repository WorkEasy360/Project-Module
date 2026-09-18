import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { DataTable, type Column } from './DataTable'

interface Row {
  id: string
  name: string
}

const columns: Column<Row>[] = [{ key: 'name', header: 'Name', render: (r) => r.name }]

describe('DataTable', () => {
  it('shows a loading state while loading', () => {
    render(
      <DataTable
        items={null}
        loading
        error={null}
        columns={columns}
        getRowKey={(r) => r.id}
        emptyTitle="No rows"
      />,
    )
    expect(screen.getByRole('status')).toBeInTheDocument()
  })

  it('shows an error state with a retry action when loading fails', () => {
    const onRetry = vi.fn()
    render(
      <DataTable
        items={null}
        loading={false}
        error="Could not reach the server."
        onRetry={onRetry}
        columns={columns}
        getRowKey={(r) => r.id}
        emptyTitle="No rows"
      />,
    )
    expect(screen.getByText('Could not reach the server.')).toBeInTheDocument()
    screen.getByRole('button', { name: /try again/i }).click()
    expect(onRetry).toHaveBeenCalled()
  })

  it('shows an empty state instead of a blank table when there are no items', () => {
    render(
      <DataTable
        items={[]}
        loading={false}
        error={null}
        columns={columns}
        getRowKey={(r) => r.id}
        emptyTitle="No projects found."
      />,
    )
    expect(screen.getByText('No projects found.')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('renders rows through the column render function', () => {
    render(
      <DataTable
        items={[{ id: '1', name: 'Alpha' }]}
        loading={false}
        error={null}
        columns={columns}
        getRowKey={(r) => r.id}
        emptyTitle="No rows"
      />,
    )
    expect(screen.getByText('Alpha')).toBeInTheDocument()
  })
})
