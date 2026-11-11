# 35 — The in-app assistant

Written for whoever operates or extends the product. It records what the assistant can do, how it
stays inside each person's permissions, and how to turn it on.

## What it does

The WorkEasy Assistant opens from:
- the robot button in the corner of every page
- **Guide me** in the header
- "Ask the assistant" on Home

People can:
- ask how the product works and where things are
- ask about their work ("what's overdue?", "how is Website Redesign doing?")
- ask for routine updates ("move the analytics task to In Progress", "log 2 hours on the blog migration")
- ask it to open pages

It can take on a whole job. "Set up my work: we launch the app on 15 December, Ravi designs, Meera
builds" becomes one proposed plan: the project, its team, phases, milestones, and tasks with
assignees, dates, estimates, subtasks, checklists and dependencies. The person approves it once, and
it is built in order. If the app refuses a step, the assistant reports it and the rest is still built.

**Voice chat.** One click on the microphone starts a voice chat:
- The assistant listens, sends what was said after a short pause, reads the answer aloud, and listens again.
- Saying "approve" or "decline" answers a proposed change.
- Saying "stop" (or "bye", "that's all", "bas") ends the chat, as do the End button and the microphone button.
- The microphone is off while it speaks, so it never hears itself.
- Closing the panel ends the chat, and it pauses itself after two silent minutes.
- Listening works in Chrome and Edge.

It is built on Claude (`claude-opus-5-5` by default) through the official Anthropic Java SDK. Its
instructions are in `src/main/resources/assistant/system-prompt.md`. They include a guide to every
screen, written from the actual UI. Keep that guide in step when screens change.

## How it stays safe

| Rule | How it is enforced |
|---|---|
| It sees only what the person can see | Every tool calls the same application services as the screens, as the signed-in person, so the existing workspace and permission checks apply unchanged |
| Nothing changes without approval | The changing tools (listed below) never run when the model asks. The conversation pauses, and the person sees the change in plain words and approves or declines. Approvals expire after 30 minutes. Sending a new message instead counts as declining |
| A change the person could not make is never offered | Before asking for approval, the change is checked against the person's access, and the model is told why it cannot happen. A whole plan is checked in full first: people exist and are on the project, dependencies point at tasks in the plan, and repeating tasks have due dates |
| It cannot do destructive things | There are no tools for deleting, archiving, changing roles, project settings or automations. Only workspace admins can invite, as on the screens |
| Conversations cannot be forged | The history is stored on the server and is private to one person in one workspace. The browser only sends new messages |
| It cannot run away | At most 14 model calls per reply, the last one without tools. Conversations end at 200 messages. A plan holds at most 60 tasks, 30 of each other kind of item, and 25 subtasks or checklist items per task |
| Text in records is not obeyed | The instructions tell it that task names, comments and so on are information, never instructions |

## Tools

- **Read:**
  - `list_projects`, `get_project`, `project_plan`
  - `list_tasks`, `get_task`, `my_tasks`, `search`
  - `list_people`, `list_project_members`, `project_activity`, `my_timesheet`, `my_notifications`
  - `list_templates`, `navigate`
- **Changes (approval needed):**
  - `set_up_project` builds a whole plan.
  - Projects: `create_project`, `create_project_from_template`, `add_project_member`
  - Tasks: `create_task`, `update_task`, `add_to_task` (subtasks and checklist items), `update_subtask`, `update_checklist_item`, `add_dependency`, `follow_task`, `add_comment`, `log_time`
  - Project records: `update_milestone`, `create_risk`, `update_risk`, `create_issue`, `record_decision`
  - Workspace: `invite_to_workspace`

The tools are defined in `AssistantTools.java` and, for project structure, `ProjectSetupTools.java`.
Adding a tool means adding:
- its definition
- its execution
- for a change, its approval text, and listing it in `WRITE_TOOLS`

## Turning it on

| Setting | Purpose | Default |
|---|---|---|
| `ANTHROPIC_API_KEY` | Turns the assistant on. Keep it secret: set it only in the environment, never in a file in the repository | Unset, so the assistant says it is not set up |
| `PROJECTMODULE_ASSISTANT_MODEL` | Which Claude model to use | `claude-opus-5-5` |
| `PROJECTMODULE_ASSISTANT_EFFORT` | How hard the model thinks: `low`, `medium`, `high` | `medium` |
| `PROJECTMODULE_ASSISTANT_REFUSAL_FALLBACK` | If Claude's safety checks decline a request, retry automatically on a suitable model | `true` |

The server must be able to reach `api.anthropic.com` over HTTPS. On a machine whose network
inspects HTTPS traffic, Java may not trust the certificate ("The assistant could not be reached",
with PKIX in the log). Start Java with `-Djavax.net.ssl.trustStoreType=Windows-ROOT` on Windows,
or add the network's certificate to Java's trust store.

Costs: each reply sends the instructions, the tool list and the conversation so far. The
instructions and tools are marked for prompt caching, so repeated requests are cheaper. Long
conversations cost more; "New chat" starts fresh.

## Verified

Checked by hand against Claude on 10 November 2026:
- It answers product questions, for example how to get alerts for overdue tasks.
- It politely declines off-topic requests.
- From one sentence and one approval, it planned and built a 20-task launch project with 4 phases,
  5 milestones, 40 subtasks, 52 checklist items and 24 dependencies.
- It created an invitation and shared the join link.

A plan of that size takes the model about a minute to prepare.

The refusal fallback sends the beta header `server-side-fallback-2026-07-01` and
`fallbacks: "default"`. If the API ever rejects those for this account, set
`PROJECTMODULE_ASSISTANT_REFUSAL_FALLBACK=false`.
