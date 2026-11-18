# SADEEQI AI Agent Core — Phase 1

This change introduces a small, provider-independent task domain core. It is deliberately not described as a complete autonomous agent.

## Included
- Explicit task lifecycle: queued, running, waiting for approval, paused, completed, failed, cancelled.
- Validated state transitions and bounded task/step metadata.
- Fail-closed action policy: sensitive and unregistered actions require explicit approval; prohibited action classes are denied.
- Explicit approval request and resolution records.
- Unit tests for lifecycle and policy behavior.

## Not included yet
- Durable storage or background execution.
- Model-driven tool selection / tool-call loop.
- Browser research adapter, Android task UI, notifications, or task resume after process restart.
- Phone calls, social messaging, purchases, or trading execution.

Those capabilities must be connected to real providers and tested separately. Unknown tools are not executable by this module. No paid provider is enabled by this change.
