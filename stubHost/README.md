# The stub host

A local test host that speaks the three API formats Gloriana's Connections use, so slow, failing and refusing hosts
can be tried on demand. It records every request it gets. It is a test tool only and never part of an app.

## Running it

```sh
./gradlew :stubHost:run
./gradlew :stubHost:run --args="--port 8787 --record /tmp/record.jsonl --delay-ms 90000"
./gradlew :stubHost:run --args="--help"
```

It listens on `127.0.0.1` only and prints its address and the record file's path on start. `--help` lists the
options and their defaults: `--port`, `--record`, `--delay-ms` (the `delay` mode's pause) and `--slow-ms` (the `slow`
mode's pause). A bad option, or a record file that can't be written, stops it with the reason.

From an Android emulator, the host's `127.0.0.1` is `10.0.2.2`; forward a device's port with
`adb reverse tcp:8787 tcp:8787`.

## Base URLs and modes

A Connection's base URL is `http://127.0.0.1:<port>/[<label>/...]<mode>/v1`. The format's own path follows it:

| Format | Path | Key |
| --- | --- | --- |
| Anthropic Messages | `{base}/messages` | `x-api-key` |
| OpenAI Chat Completions | `{base}/chat/completions` | `Authorization: Bearer` |
| OpenAI Responses | `{base}/responses` | `Authorization: Bearer` |

The segment before `v1` picks the mode. Any segments before it are a free label, so two Connections in the same mode
still get their own base URLs and the record tells them apart: `/chat/normal/v1` and `/summary/normal/v1`.

| Mode | Answers |
| --- | --- |
| `normal` | a short reply |
| `reasoning` | a reply with a reasoning block: Anthropic thinking (signed), Chat Completions `reasoning_content`, a Responses reasoning summary |
| `status` | a reply holding `<details><summary>Status</summary>…</details>` |
| `refusal` | the format's own refusal: Anthropic `stop_reason: "refusal"`, a Chat Completions `refusal` message, a Responses `refusal` content part |
| `delay`, `delay-<ms>` | the normal reply after a pause before anything is sent: `--delay-ms`, or the length in the name (`delay-2000`) |
| `slow`, `slow-<ms>` | the normal reply, streamed with a pause between events: `--slow-ms`, or the length in the name |
| `401`, `403`, `429`, `500` | that status with the format's own error body; `429` also sends `Retry-After: 1` |
| `key-echo`, `key-echo-429` | 401 (or 429) with an error body that holds the request's key, or says none was sent |
| `length` | a short reply stopped at the token limit: Anthropic `max_tokens`, Chat Completions `length`, Responses `incomplete` with `max_output_tokens` |
| `cut-off` | streamed: the normal reply's text, then the stream closes without its closing events; whole: the normal reply |
| `stall` | the normal reply, streamed with a `--delay-ms` pause halfway through its events |
| `linger` | the normal reply, then the connection stays open for `--delay-ms` after the last event |
| `bad-type` | the normal reply under a malformed `Content-Type` |
| `redirect` | 307 to the same path in `normal` mode |
| `stream-error` | streamed: the normal reply's text, then the format's error in the stream (Anthropic `error`, a Chat Completions `error` chunk with no `[DONE]`, Responses `response.failed`); whole: `500` |
| `empty-list` | the normal reply; its model list is empty |
| `no-list` | the normal reply; its model list answers 404 in the format's error shape |

A request body needs `model`, which the reply names again. A reply streams when the body has `"stream": true` and
comes whole when it is `false`, `null` or missing; any other value is refused. Both carry usage; a streamed Chat
Completions reply sends its usage chunk only when the body sets `stream_options.include_usage`. A body that isn't such
a request gets 400 in the format's error shape. A format's path with an unknown mode, or a method other than POST, gets
404 in that format's error shape; any other path gets 404 with how to call the host.

## The model list

`GET {base}/models` answers with two models, `stub-model-large` and `stub-model-small`. A request that carries
`anthropic-version` gets an Anthropic model page, any other an OpenAI list:

```json
{"data":[{"type":"model","id":"stub-model-large","display_name":"Stub Model Large","created_at":"2026-01-01T00:00:00Z"},…],"has_more":false,"first_id":"stub-model-large","last_id":"stub-model-small"}
{"object":"list","data":[{"id":"stub-model-large","object":"model","created":1767225600,"owned_by":"stub-host"},…]}
```

`empty-list` lists no models and `no-list` answers 404, as above. A failing mode (`401`, `403`, `429`, `500`,
`stream-error`, `key-echo`, `key-echo-429`) answers with its failure in that shape, `delay` pauses first, and
`redirect` redirects. Every other mode lists the two models: `slow`, `stall`, `linger`, `bad-type` and `cut-off` change
nothing here.

## The record

Every request, to any path but `/record`, is one JSON line appended to the record file:

```json
{"receivedAt":"2026-01-01T12:00:00Z","method":"POST","path":"/summary/normal/v1/messages","query":"","headers":{"x-api-key":["MARKER-KEY-1"]},"body":{"model":"…"}}
```

`query` is the text after `?`, empty when there is none. Header names are lower case, each with its list of values.
`body` is the parsed JSON, the raw text when it isn't JSON, or `null` when empty. `GET /record` serves the same lines
(`application/x-ndjson`), so a marker string can be searched for with
`curl -s http://127.0.0.1:<port>/record | grep MARKER-`.

A record file named with `--record` is appended to, never cleared, so it also holds earlier runs' requests. Use a new
file, or the default temporary one, for a search that must see only this run.

## In a test

`startStubHost(recordFile)` starts the host in the test's own JVM on a free port and returns its `address`,
`baseUrl(mode)` and `recorded()`; `close()` stops it.

## Tests

`./gradlew :stubHost:test` sends every mode through every format, streamed and whole, checks each stream's events in
order against the format's grammar, and checks the record.
