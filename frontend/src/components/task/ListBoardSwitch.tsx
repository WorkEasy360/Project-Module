import { Link } from 'react-router-dom'
import { Icon } from '../common/Icon'

/** V3: a project's Tasks switch between List and Board over the same task records. */
export function ListBoardSwitch({ current }: { current: 'list' | 'board' }) {
  return (
    <div className="list-board-switch" role="group" aria-label="Show tasks as">
      <Link to="../tasks" relative="path" className={current === 'list' ? 'active' : ''} aria-current={current === 'list' ? 'page' : undefined}>
        <Icon name="list" size={16} /> List
      </Link>
      <Link to="../kanban" relative="path" className={current === 'board' ? 'active' : ''} aria-current={current === 'board' ? 'page' : undefined}>
        <Icon name="kanban" size={16} /> Board
      </Link>
    </div>
  )
}
