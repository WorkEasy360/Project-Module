# Project Integration Guide

## Project Module Owns

All project-specific business logic and data.

## External References

The Project Module may reference:

- User
- Team
- Organization
- Document
- Calendar
- Notification
- Chat
- Authentication

These are owned by other modules.

## Rule

Do not duplicate another module's database or business logic.

Use:

- API
- Service interface
- Event
- Adapter

for integration.