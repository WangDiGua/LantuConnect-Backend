# Agent Protocol Matrix

This repository treats agent registration and runtime invocation as distinct concerns.

Supported runtime protocols:

- `openai_compatible`
- `bailian_compatible`
- `anthropic_messages`
- `gemini_generatecontent`

Bailian entrypoints:

- `compatible-mode/v1/responses`
- `api/v1/apps/{id}/completion`

Implementation notes:

- Registration stores protocol, upstream endpoint, upstream agent/app id, and exposed model alias.
- Runtime invocation routes by `registrationProtocol` and normalizes upstream request/response bodies per protocol.
- Unsupported protocol features are returned as business errors instead of being silently forwarded.
