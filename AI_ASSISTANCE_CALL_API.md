# OmniDimension AI assistance call API

The Android app sends one authenticated request after a customer has explicitly opted in and accumulated five minutes of foreground use:

`POST /api/users/engagement/assistance-call`

Example body:

```json
{
  "event": "five_minute_service_exploration",
  "platform": "android",
  "foregroundEngagementMs": 300000,
  "uniqueServiceCount": 3,
  "serviceIds": ["ac", "plumbing", "cleaning"],
  "consent": true
}
```

The server must authenticate the Firebase bearer token and obtain the destination number from the verified user profile. Do not trust a phone number supplied by the client.

Before invoking the AI/telephony provider, the server must:

- require `consent: true` and a verified phone number;
- enforce one request per user in seven days (idempotency key: authenticated user + engagement window);
- observe the user's timezone, configured quiet hours, and local calling/marketing-consent rules;
- log the consent timestamp, provider request ID, outcome, and opt-out status; and
- return `202 Accepted` for a queued call, including a request ID. Return `200` for an idempotent duplicate.

## OmniDimension dispatch

Store these only in the backend environment:

```text
OMNIDIM_API_KEY=...
OMNIDIM_AGENT_ID=...
OMNIDIM_FROM_NUMBER_ID=... # optional; uses the workspace default when omitted
```

Once the backend has accepted the authenticated app request and passed its consent, cooldown,
and quiet-hours checks, dispatch it server-to-server:

```http
POST https://omnidim.io/api/v1/calls/dispatch
Authorization: Bearer $OMNIDIM_API_KEY
Content-Type: application/json
Idempotency-Key: assistance:{userId}:{engagementWindowId}

{
  "agent_id": 123456,
  "to_number": "+919876543210",
  "from_number_id": 123,
  "call_context": {
    "user_name": "Customer name",
    "reason": "The customer opted in for help after exploring services.",
    "services_explored": "ac, plumbing"
  },
  "metadata": {
    "user_id": "authenticated-user-id",
    "source": "apnaservo_android_engagement"
  },
  "__languages": ["Hindi", "English"]
}
```

`to_number` must be the verified stored number formatted as E.164 (`+91` plus the 10-digit
Indian mobile number). Save OmniDimension's returned `requestId` with the local call-request
record, and configure a post-call webhook on the agent to record final outcomes.

Provider credentials, call scripts, and outbound-call scheduling belong exclusively on the backend. The app does not make phone calls and never contains provider secrets.
