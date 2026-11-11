# 34 — Daily-work features

Written for whoever operates or extends this product. It records what was added to bring the module
up to what teams use every day, how each part behaves, and what it needs to work in production. All
of it adds to the existing module: existing screens, tables and APIs keep working.

## What was added

| Area | What people can do | API |
|---|---|---|
| Team | Admins invite by link; everyone picks people by name. Members can work on tasks. | `/workspace/people`, `/workspace/invitations`, `/auth/invitation/*` |
| Tasks | In Progress status, moving between any statuses, priority, start date, estimate, tags, repeating tasks | `/tasks/{id}` |
| Comments | Comment on a task; type `@` to mention someone on the project | `/tasks/{id}/comments` |
| Followers | Assignees and commenters follow automatically; anyone can follow or stop | `/tasks/{id}/followers` |
| Notifications | The bell in the header; due-tomorrow reminders each morning | `/notifications` |
| Activity | Who did what on a project | `/projects/{id}/activity` |
| Time | Log time, a timer that survives closing the browser, a weekly timesheet, CSV export | `/tasks/{id}/time-entries`, `/timer`, `/time-entries/mine` |
| Files | Attach up to 25 MB per file to a task | `/tasks/{id}/attachments`, `/attachments/{id}` |
| Account | Edit name, change password, forgot password, Google sign-in | `/auth/*` |
| Search | Projects and tasks across everything a person belongs to | `/search` |
| Export | Tasks and timesheets as CSV, which opens in Excel. Reports through the browser's print-to-PDF | In the browser |

## Rules worth knowing

- **Roles.** A workspace has admins and members. Whoever registers is the admin of a new workspace;
  anyone joining by invitation is a member. Only admins invite and create password reset links.
- **Project roles.** `MEMBER` now holds `EDIT_TASKS`: tasks, subtasks, checklists, time and files. Only
  `MANAGER` and `OWNER` hold `EDIT_PROJECT`: phases, milestones, risks, dependencies and so on.
- **Project lists** show only projects a person belongs to. The dashboard's counts still cover the
  whole workspace, so its "Total Projects" can be higher than the list for someone on few projects.
- **Repeating tasks.** Completing one creates the next, with its dates moved forward and the same
  assignee, priority and tags. It needs a due date. The completed task stops repeating, so reopening
  and completing it again does not create a second one.
- **Notifications** are never sent for a person's own action. Someone mentioned gets the mention rather
  than also getting the ordinary comment notice. Only people on the project can be mentioned.
- **Uploaded files** are always downloaded, never displayed, so an uploaded web page cannot run on
  this site.
- **Reminders** run at 03:00 UTC (08:30 in India). Change with `PROJECTMODULE_REMINDERS_CRON`.

## Settings for production

| Setting | Needed for | Without it |
|---|---|---|
| `PROJECTMODULE_APP_BASE_URL` | Links in emails. Defaults to `https://ops.workeasy360.com` | Set it if the site moves |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, plus `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH` and `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE` set to `true` for most providers | Emailing invitations, notifications, reminders and password resets | Everything works in the app. Admins copy invitation and reset links by hand, and "Forgot password" is hidden |
| `PROJECTMODULE_MAIL_FROM` | The sender address | `noreply@workeasy360.com` |
| `PROJECTMODULE_GOOGLE_CLIENT_ID` | "Sign in with Google" | The button does not appear |

## Before deploying

1. **Upload size.** The deployment bundle raises Elastic Beanstalk's nginx limit to 26 MB
   (`deploy/.platform/nginx/conf.d/workeasy.conf`). Cloudflare's own limit is 100 MB on its free plan.
2. **Files live in the database.** That is simple and backed up with everything else, but a busy
   workspace will grow the database quickly. Moving to S3 later is a change inside the `attachment`
   package only.
3. **Existing people must be linked to their old data** before they lose access. See
   `33-AUTHENTICATION.md`.
4. **Google sign-in has not been tried against Google**, because no client id exists yet. What is done
   with Google's answer is tested; the exchange itself should be checked once one is configured.
5. **Accounts do not verify their email address.** Someone could register with another person's
   address before that person joins. Google sign-in would then open that same account. Verifying the
   address at registration, once email is configured, closes this.
