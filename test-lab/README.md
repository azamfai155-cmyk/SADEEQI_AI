# SADEEQI AI Web Test Lab

An isolated, Arabic RTL browser test harness for early SADEEQI AI development.

## Included now
- Local heuristic intent classification (not an LLM).
- Simulated task lifecycle with an explicit transition table.
- Safety checks for approval-gated actions (messages, deletion, purchases).
- A 12-case automated smoke suite for intent classification, approval gates, and task transitions.
- Exportable JSON test report.
- No API keys, backend connection, phone access, external side effects, or persistent storage.

## Run locally
Open `index.html` in a modern browser, or serve this directory with any static web server.

## Test workflow
1. Open the conversation panel and submit sample commands.
2. Create tasks and run the lifecycle simulation.
3. Run all automated tests and inspect the pass/fail list.
4. Export the JSON report for regression tracking.
5. Exercise each safety check and verify that approval is required.

## Important boundary
This is still a front-end simulation. It does not call an LLM, persist tasks, invoke real tools, or control Android. A passing browser suite verifies only this isolated demo.

## Integration phases
1. Inspect and document the actual backend API contract.
2. Add authenticated, read-only backend connectivity and health diagnostics.
3. Add isolated test fixtures and contract tests.
4. Add allow-listed tool adapters in dry-run mode.
5. Add approval enforcement and auditable execution tests.
6. Verify on a physical Android device before porting stable behavior.

Never put provider secrets or service-role keys in browser code. Keep credentials server-side. Do not enable real-world side effects in the test environment.
