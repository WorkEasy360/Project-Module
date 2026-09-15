# Project API

## Projects

POST   /projects
GET    /projects
GET    /projects/:id
PATCH  /projects/:id
DELETE /projects/:id

## Members

GET    /projects/:id/members
POST   /projects/:id/members
PATCH  /projects/:id/members/:memberId
DELETE /projects/:id/members/:memberId

## Phases

GET    /projects/:id/phases
POST   /projects/:id/phases
PATCH  /phases/:id
DELETE /phases/:id

## Milestones

GET    /projects/:id/milestones
POST   /projects/:id/milestones
PATCH  /milestones/:id
DELETE /milestones/:id

## Tasks

GET    /projects/:id/tasks
POST   /projects/:id/tasks
GET    /tasks/:id
PATCH  /tasks/:id
DELETE /tasks/:id