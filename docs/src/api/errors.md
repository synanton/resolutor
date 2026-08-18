# Errors

All documented error responses use `Content-Type: application/problem+json` (RFC 7807 `ProblemDetail`).

Typical body:

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "detail": "Request payload failed validation",
  "errors": ["topResourceClass: must not be blank"]
}
```

| Status | Title (typical) | Cause |
| --- | --- | --- |
| 400 | Validation failed | Bean Validation (`@NotBlank`, `@PositiveOrZero`, …) |
| 400 | Malformed request body | Unreadable JSON |
| 400 | Malformed path variable | `{id}` is not a UUID |
| 400 | Bad request | Domain `IllegalArgumentException` |
| 404 | Resource not found | Unknown task/plan, or unmatched path |
| 204 | - | No latest plan (`GET /plans/latest`, `GET /plans?at=`) |
| 500 | Internal server error | Unexpected; `detail` is the exception simple name |

Validation includes an `errors` array of `"field: message"` strings.

Success codes: **201** ingest, **200** GET bodies, **204** progress/complete and empty plan GETs.
