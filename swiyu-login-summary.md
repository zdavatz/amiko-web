# Swiyu OID4VP Login — Implementation Summary for Ruby

## Overview

Swiyu login uses **OID4VP (OpenID for Verifiable Presentations)** to authenticate Swiss healthcare professionals via the swiyu Wallet mobile app. The user scans a QR code with their wallet, which presents a verifiable credential (SD-JWT) to a verifier API. Your backend orchestrates this by creating verification requests, polling for results, and creating a session on success.

## External Verifier API

**Base URL:** `https://swiyu.ywesee.com/verifier-mgmt/api`
**Required Header:** `SWIYU-API-Version: 1`
**Access Control:** The verifier runs behind an Apache reverse proxy with IP whitelist (currently `65.109.136.203`)

There are two API calls your backend makes:

### 1. Create Verification — `POST /verifications`

Request body (JSON):

```json
{
  "accepted_issuer_dids": [
    "did:tdw:QmeA6Hpod7N85daNqWZD5w8jBCU6oaXcxxQFNZ6ox245ci:identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:5b1672d3-2805-4752-b364-2f87013bc5c3"
  ],
  "response_mode": "direct_post",
  "presentation_definition": {
    "id": "<random-uuid>",
    "input_descriptors": [
      {
        "id": "doctor-credential",
        "format": {
          "vc+sd-jwt": {
            "sd-jwt_alg_values": ["ES256"]
          }
        },
        "constraints": {
          "fields": [
            {
              "path": ["$.vct"],
              "filter": {
                "type": "string",
                "const": "doctor-credential-sdjwt"
              }
            },
            {
              "path": ["$.firstName"],
              "optional": true
            },
            {
              "path": ["$.lastName"],
              "optional": true
            },
            {
              "path": ["$.gln"],
              "optional": true
            }
          ]
        }
      }
    ]
  }
}
```

Response (JSON):

```json
{
  "id": "<verification-id>",
  "verification_deeplink": "openid4vp://authorize?...",
  ...
}
```

The `verification_deeplink` is what gets encoded into the QR code.

### 2. Check Verification Status — `GET /verifications/{id}`

Response (JSON):

```json
{
  "state": "PENDING | SUCCESS | FAILED | EXPIRED",
  "wallet_response": {
    "credential_subject_data": [
      {
        "claims": {
          "firstName": "Hans",
          "lastName": "Müller",
          "gln": "7601234567890",
          "vct": "doctor-credential-sdjwt"
        }
      }
    ]
  }
}
```

## Configuration

Only one config value is needed:

```
swiyu.accepted_issuer_did = "did:tdw:QmeA6Hpod7N85daNqWZD5w8jBCU6oaXcxxQFNZ6ox245ci:identifier-reg.trust-infra.swiyu-int.admin.ch:api:v1:did:5b1672d3-2805-4752-b364-2f87013bc5c3"
```

This is the DID of the trusted credential issuer (swiyu integration environment).

## Complete Flow

```
Browser                         Your Ruby App                  swiyu Verifier API
───────                         ────────────                   ──────────────────

1. User visits /swiyu
   ←── render login page

2. JS calls GET /swiyu/login
                                POST /verifications ──────────→
                                ←──────── {id, deeplink}
   ←── {id, deeplink}
   Show QR code from deeplink

3. User scans QR with swiyu Wallet app
   (wallet submits credential directly to verifier via direct_post)

4. JS polls GET /swiyu/status/:id every 2 seconds
                                GET /verifications/:id ───────→
                                ←──────── {state, claims}
   ←── {state, authenticated, claims}

5. On SUCCESS: JS calls POST /swiyu/session with {verification_id}
                                GET /verifications/:id ───────→  (re-validate server-side!)
                                ←──────── {state, claims}
                                Validate claims, create session
   ←── {status: "ok", name, gln}
   Redirect to home

6. Logout: GET /swiyu/logout → clear session → redirect home
```

## Routes You Need in Ruby

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/swiyu` | Render login page with QR widget |
| GET | `/swiyu/login` | Call verifier API, return `{id, deeplink}` as JSON |
| GET | `/swiyu/status/:id` | Proxy poll to verifier, return `{state, authenticated, claims}` |
| POST | `/swiyu/session` | Re-validate server-side, set session cookies |
| GET | `/swiyu/logout` | Clear session, redirect to `/` |

## Validation Rules

- **GLN format:** Must match `/^760\d{10}$/` (Swiss healthcare practitioner number)
- **firstName** and **lastName:** Must be non-empty strings
- **Credential type (vct):** Must be `"doctor-credential-sdjwt"`

## Session Keys

After successful verification, store in session:

| Key | Value |
|-----|-------|
| `swiyu_auth` | `"true"` |
| `swiyu_gln` | The GLN number (e.g., `"7601234567890"`) |
| `swiyu_firstName` | First name from credential |
| `swiyu_lastName` | Last name from credential |

## Frontend Requirements

The frontend needs:
- **QR code library** — the Java version uses [QRCode.js](https://github.com/davidshimjs/qrcodejs) (256x256, dark `#003d73`, light `#ffffff`)
- **Polling** — `setInterval` every 2000ms calling `/swiyu/status/:id`
- **Mobile deeplink fallback** — a direct link to the `verification_deeplink` URL for users on mobile (opens wallet app directly instead of QR scan)
- On `SUCCESS` → POST to `/swiyu/session`, then redirect to home
- On `FAILED` or `EXPIRED` → show error with retry button

## Ruby Implementation Notes

1. **HTTP client:** Use `Net::HTTP`, `Faraday`, or `HTTParty` to call the verifier API at `https://swiyu.ywesee.com/verifier-mgmt/api`. Always send the header `SWIYU-API-Version: 1`.

2. **IP whitelist:** Your Ruby server's outbound IP must be whitelisted on the verifier's Apache proxy. Currently only `65.109.136.203` is allowed — you'll need to add your ch.oddb.org server IP.

3. **Security:** The `POST /swiyu/session` endpoint must **re-fetch** the verification from the verifier API server-side. Never trust the claims sent from the browser — always validate against the verifier.

4. **CSRF:** The `/swiyu/session` POST is called via AJAX (JSON body), so you may need to exempt it from CSRF protection or include a CSRF token in the JS call.

5. **UUID generation:** Use `SecureRandom.uuid` for the `presentation_definition.id`.
