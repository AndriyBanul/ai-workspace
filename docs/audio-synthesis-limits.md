# Audio synthesis limits

`POST /api/v1/audio/speech` remains synchronous. Speech synthesis rejects
oversized input before contacting Piper and bounds each provider response.

All settings use the `ai-workspace.audio.synthesis` property prefix. Environment
variables and defaults are also listed in `.env.example`.

| Property suffix | Environment variable | Default |
| --- | --- | --- |
| `max-text-characters` | `AUDIO_SYNTHESIS_MAX_TEXT_CHARACTERS` | 3,000 Unicode code points |
| `max-audio-size` | `AUDIO_SYNTHESIS_MAX_AUDIO_SIZE` | 16 MiB of PCM audio |
| `max-audio-duration` | `AUDIO_SYNTHESIS_MAX_AUDIO_DURATION` | 5 minutes |
| `request-timeout` | `AUDIO_SYNTHESIS_REQUEST_TIMEOUT` | 90 seconds |
| `connect-timeout` | `AUDIO_SYNTHESIS_CONNECT_TIMEOUT` | 5 seconds |
| `read-timeout` | `AUDIO_SYNTHESIS_READ_TIMEOUT` | 15 seconds of read inactivity |
| `max-header-size` | `AUDIO_SYNTHESIS_MAX_HEADER_SIZE` | 8 KiB, excluding the newline |
| `max-event-data-size` | `AUDIO_SYNTHESIS_MAX_EVENT_DATA_SIZE` | 64 KiB of separate JSON metadata per event |
| `max-event-payload-size` | `AUDIO_SYNTHESIS_MAX_EVENT_PAYLOAD_SIZE` | 1 MiB per event |
| `max-events` | `AUDIO_SYNTHESIS_MAX_EVENTS` | 10,000, including unknown and stop events |
| `max-concurrent-requests` | `AUDIO_SYNTHESIS_MAX_CONCURRENT_REQUESTS` | 1 per API process |

`KB` and `MB` property values use binary units. Positive limits are required at
startup; buffer sizes and durations must fit the implementation's integer bounds.

Piper accepts inline metadata in a header's `data` object, separate JSON metadata
after the header, or both. Additional metadata replaces matching inline keys,
including explicit `null` values, before the typed audio/error records are
validated. Inline data is bounded by the header limit; the separate block is
bounded by `max-event-data-size`. This follows the
[Wyoming format](https://github.com/OHF-Voice/wyoming/blob/main/README.md#format).

The total deadline starts before connecting and applies to socket reads and
writes. A scheduled task closes the socket when it expires, so continuously
arriving bytes cannot extend the request indefinitely. Deadline tasks are canceled
on completion, and the scheduler is shut down with the Spring context.

The PCM byte and duration limits apply together. Duration is converted to a
maximum number of complete frames using the validated sample rate, sample width,
and channel count. Declared event lengths and the remaining audio budget are
checked before allocating or reading the event body. A second audio-start event
is rejected so the duration budget cannot be reset. WAV headers and optional
padding add at most 45 bytes to the PCM size limit.

Oversized input returns HTTP 400. Exhausted synthesis capacity returns HTTP 503
with code `AUDIO_SYNTHESIS_BUSY`; requests are rejected immediately without a
waiting queue. Provider response violations and timeouts use the existing Piper
HTTP 502 error boundary. No partial or truncated WAV response is returned.

Concurrency permits are released on every completion path, including transport
and parsing failures. This guard covers the singleton Piper client in one API
process. Multiple API replicas sharing a Piper backend need a shared concurrency
limiter or dedicated Piper instances; the local guard does not coordinate replicas.
