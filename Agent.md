# Agent.md — AI Agent Guidelines & Architecture Guide

Please refer to [AGENTS.md](file:///e:/PG-Project/AGENTS.md) for the complete AI Agent guidelines, workspace setup, system architecture, business rules, and execution commands.

---

## Quick Reference

- **`[PG-CORE]`**: Single-tenant PG Management (`file:///e:/PG-Project/core-pg-crm/`)
- **Main Docs**: [docs/architecture/](file:///e:/PG-Project/docs/architecture/)
- **Rules & Constraints**:
  - Always write docs and artifacts within workspace boundaries (`e:\PG-Project`).
  - Do NOT edit existing Flyway migrations; create new versioned migration scripts.
  - Enforce flat-rate rent calculation logic and PII anonymization policies.
