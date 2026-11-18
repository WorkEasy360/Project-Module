# 36 — WorkEasy360 Operations design

Written for whoever runs or extends the product. It records the November 2026 redesign and the
settings it added.

## What changed

The product is now branded **WorkEasy360 Operations**: the WorkEasy360 logo with "360" in green,
and a green "Operations" badge.

**Sign-in.** The page has two parts:
- a dark green welcome panel, with a drawn laptop showing the home page
- the **Operations Login** card

Existing behaviour is unchanged: registration, invitations, password reset and the hand-over for
customers of the earlier version.

**Sidebar.** It is dark green, with these sections:
- Home
- Projects (Active Projects, Archived Projects)
- Tasks
- Team
- Automation (Automation Rules, Templates)
- Reports (Overview, Timesheet, Calendar)
- Helpdesk
- Settings, below a divider
- the **Current Plan** card at the bottom

**Header.** From left to right:
- the workspace picker
- search
- the **AI Assistant** button, which replaces the floating robot button
- notifications
- help, which opens Helpdesk
- the person's name and role. Workspace admins show as **Owner**.

**Home** has:
- a period selector. The financial year runs April to March.
- six summary tiles
- Project Progress, Tasks by Status, My Tasks, Quick Actions, Recent Activity, Tasks Overview (by month) and Team Workload

Completion dates come from a completed task's last update, which is the closest the data records.

**Projects** has:
- five count tiles, search, and filters for status, member and time
- a table (five per page by default) or a board
- **Archived Projects**, a read-only list

**Helpdesk** has the assistant, a guided tour, support email, and searchable answers.

The theme defaults to **Light**. Dark and System are still available in Settings.

## New data

Migration V19 only adds; it removes nothing:
- `projects.customer_contact` and the `project_tags` table, which are set in the project forms
- `workspace_settings`: the workspace name, shown in the header. Owners rename it in Settings. Until
  then it is "My Workspace".

`GET /api/v1/projects` now also returns:
- each project's description, dates, customer, tags and archived state
- live task counts (`taskCount`, `completedTaskCount`)

`?archived=true` lists archived projects. `GET`/`PATCH /api/v1/workspace/settings` read and rename
the workspace.

## Settings

| Setting | Purpose | Default |
|---|---|---|
| `PROJECTMODULE_PLAN_NAME` | Plan name on the Current Plan card | `Advanced` |
| `PROJECTMODULE_PLAN_VALID_UNTIL` | "Valid till …" line on the card; hidden when empty | empty |
| `PROJECTMODULE_PLAN_MANAGE_URL` | Where **Manage Plan** goes | `https://workeasy360.com` |
| `PROJECTMODULE_SUPPORT_EMAIL` | Contact shown on Helpdesk | `support@workeasy360.com` |
| `PROJECTMODULE_AUTH_WORKEASY360_SIGN_IN_URL` | Where **Continue with WorkEasy360 Account** goes. Until set, the button explains that this sign-in is not set up yet. | empty |

There is no billing system. The plan card shows what these settings say.
