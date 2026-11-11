# Customization Rules for PG CRM Workspace

## File Placement & Sandbox Integrity
- **Always write project documentation, design plans, SOPs, and checklists directly to the project locations** (e.g., project root `/`, `/docs`, or relevant subdirectories within the active project workspace) instead of creating them exclusively in hidden system configuration directories or temporary directories (`C:\Users\Admin\.gemini\antigravity\brain\...`).
- Avoid code/file generation outside the workspace boundary. Maintain all architectural resources and operational artifacts inside the project structure to ensure Git tracking and team visibility.

## Full Agent Documentation
- See [AGENTS.md](file:///e:/PG-Project/AGENTS.md) in the project root for the **complete and authoritative** AI agent operational manual.
- The root `AGENTS.md` contains: architecture overview, all mandatory rules, RBAC security matrix, API endpoint reference, entity reference, service layer reference, Flyway migration history, coding conventions, cron job schedule, and anti-pattern checklist.

## Quick Rule Summary (Full details in AGENTS.md)
- NEVER modify existing Flyway SQL migrations — create a new `V{N+1}__description.sql`
- ALWAYS set `SPRING_PROFILES_ACTIVE=dev` for local backend runs
- NEVER apply pro-rated rent — flat-rate model only
- NEVER hard-delete Guest or Invoice records — use soft delete
- NEVER add `permitAll()` wildcard in SecurityConfig
- NEVER write documentation to `.gemini` hidden directories
