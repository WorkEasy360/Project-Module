import { useMemo } from 'react'
import { membersApi } from '../../api/members'
import { usePeople } from '../../context/PeopleContext'
import { useAsyncData } from '../../hooks/useAsyncData'
import { SelectField } from './fields'

/** The people on a project, by name, sorted for a picker. */
export function useProjectPeople(projectId: string): { value: string; label: string }[] {
  const { nameOf } = usePeople()
  const { data } = useAsyncData(() => membersApi.list(projectId), [projectId])
  return useMemo(
    () =>
      (data ?? [])
        .map((member) => ({ value: member.userId, label: nameOf(member.userId) }))
        .sort((a, b) => a.label.localeCompare(b.label)),
    [data, nameOf],
  )
}

/** Choose someone on the project. Keeps a current value showing even if they have since left. */
export function ProjectPersonField({
  id,
  label,
  projectId,
  value,
  onChange,
  emptyLabel = 'Unassigned',
}: {
  id: string
  label: string
  projectId: string
  value: string
  onChange: (userId: string) => void
  emptyLabel?: string
}) {
  const { nameOf } = usePeople()
  const people = useProjectPeople(projectId)
  const options =
    value && !people.some((person) => person.value === value)
      ? [{ value, label: nameOf(value) }, ...people]
      : people
  return (
    <SelectField id={id} label={label} value={value} onChange={onChange} allowEmpty emptyLabel={emptyLabel} options={options} />
  )
}
