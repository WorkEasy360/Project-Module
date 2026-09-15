# Project Module Specification

## 1. Purpose

The Project Module is responsible for project planning,
execution, tracking, intelligence, AI assistance and
project-specific automation.

## 2. Core Principle

Manual = Control
AI = Intelligence
Automation = Less repetitive work
Agents = Execution
Human = Final authority

## 3. Project Module Owns

- Project
- ProjectMember
- Phase
- Milestone
- TaskList
- Task
- Subtask
- Checklist
- Dependency
- Risk
- Issue
- Decision
- ProjectComment
- ProjectActivity
- ProjectCustomField
- ProjectTemplate
- ProjectAutomation
- AutomationRun
- AIRecommendation
- AIAction
- AIApproval
- ProjectHealth

## 4. Project Module Does NOT Own

- Authentication
- User Management
- HR
- CRM
- Sales
- Finance
- Billing
- Company-wide Chat
- Company-wide Notifications
- Company Calendar
- Organization Management
- Customer Management
- Support
- Marketing

## 5. Integration Principle

The Project Module must communicate with other modules
through APIs, service interfaces, events or adapters.

The Project Module must not implement functionality owned
by another module.

## 6. Manual + AI

Users must always be able to:

- Create projects manually
- Create tasks manually
- Edit projects manually
- Edit tasks manually
- Manage projects without AI

AI is an enhancement, not a requirement.

AI-generated changes must be reviewable.

Users can:

- Accept
- Edit
- Reject

AI suggestions.

Manual user changes always have final authority.

## 7. Project Hierarchy

Project
    ↓
Phase
    ↓
Milestone / Task List
    ↓
Task
    ↓
Subtask