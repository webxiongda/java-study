# Default Demo Login Plan

## Goal

Make the local Java Study login screen usable with one click by providing a seeded demo account and prefilled credentials.

## Scope

- Seed a configurable demo account at backend startup.
- Prefill the frontend login form with the same demo credentials.
- Verify with an integration test that the seeded account can log in.

## Tasks

- [x] Add backend integration coverage for demo account login.
- [x] Add startup seeding for the demo account using password hashing and initial content import.
- [x] Add application properties for demo account username and secret.
- [x] Prefill the React login form with the demo account.
- [x] Run focused backend and frontend verification.
- [x] Fix npm backend scripts so local development starts with the expected profile.
- [x] Add Java Learning logo asset and wire it into the app brand areas.
