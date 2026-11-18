# SADEEQI AI Web Test Lab

A static, isolated browser-based test harness for early SADEEQI AI development.

## Current scope
- Local intent classification demo (heuristic simulation, not an LLM).
- Simulated task lifecycle and event log.
- Safety checks demonstrating approval gates for sending, deletion, and purchases.
- No backend connection, API credentials, phone access, external side effects, or persistent storage.

## Run locally
Open `index.html` in a modern browser, or serve this directory with any static web server.

## Deployment
Deploy this directory as the Vercel project root (static site). Keep the deployment on Preview until reviewed. Do not add production credentials or connect real actions to this demo.

## Next integration phases
1. Connect authenticated read-only task status from the SADEEQI backend.
2. Add isolated test accounts and test database records.
3. Add controlled tool adapters with dry-run mode and explicit approval.
4. Add automated browser tests and backend contract tests.
5. Only after verification, port stable behavior into Android.

A green UI status is not proof of real device capability. Each feature needs automated tests and physical Android verification.
