# Cloudflare Worker test report – 1.2.0 adapter

Local automated tests run with Node.js 22 using the Fetch API Worker entrypoint.

- Health route without secrets: PASS (returns configured=false; no secrets exposed).
- Chat route without bearer token: PASS (401).
- Chat route with invalid input: PASS (400 before any upstream request).
- Live Cloudflare deployment: NOT RUN.
- OpenAI live API request: NOT RUN (requires the owner's server-side API key and deployed Worker).
- Android-to-Worker integration: NOT RUN (requires deployed URL and Android build/device test).

The pre-existing Express backend test was not run because its `express` dependency is not installed in this local environment. The Cloudflare Worker tests do not require Express.
