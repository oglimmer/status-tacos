/**
 * The endpoint reference of the /developers page. Keep it in step with the controllers in
 * backend/src/main/java/de/oglimmer/status_tacos/controller.
 */

/** none: no sign-in. token: API token (scope GRAFANA). login: OIDC access token of a sign-in. */
export type AuthKind = 'none' | 'token' | 'login'

export interface Param {
  name: string
  in: 'path' | 'query' | 'body' | 'form'
  type: string
  required?: boolean
  description: string
}

export interface Endpoint {
  method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE' | 'GET, POST'
  path: string
  summary: string
  params?: Param[]
  /** An example request body (JSON). */
  body?: string
  /** An example answer (JSON). */
  response?: string
  /** Status codes other than the plain success. */
  errors?: string[]
  notes?: string[]
}

export interface EndpointGroup {
  id: string
  title: string
  auth: AuthKind
  intro: string
  endpoints: Endpoint[]
}

const MONITOR_EXAMPLE = `{
  "id": 42,
  "name": "Shop checkout",
  "url": "https://shop.example.com/health",
  "tenantId": 7,
  "tenant": { "id": 7, "name": "Shop", "code": "shop", "isActive": true, … },
  "state": "ACTIVE",
  "httpHeaders": { "X-Probe": "tacos" },
  "statusCodeRegex": "^2\\\\d\\\\d$",
  "responseBodyRegex": "\\"status\\":\\"ok\\"",
  "prometheusKey": null,
  "prometheusMinValue": null,
  "prometheusMaxValue": null,
  "checkInterval": 60,
  "connectTimeout": 5,
  "useSystemDns": false,
  "alertingThreshold": 120,
  "showOnPublicPage": true,
  "createdAt": "2026-10-01T08:00:00",
  "updatedAt": "2026-10-20T12:30:00"
}`

const MONITOR_BODY = `{
  "name": "Shop checkout",
  "url": "https://shop.example.com/health",
  "tenantId": 7,
  "state": "ACTIVE",
  "checkInterval": 60,
  "alertingThreshold": 120,
  "statusCodeRegex": "^2\\\\d\\\\d$",
  "showOnPublicPage": true
}`

const MONITOR_FIELDS: Param[] = [
  {
    name: 'name',
    in: 'body',
    type: 'string',
    required: true,
    description: 'At most 255 characters.',
  },
  {
    name: 'url',
    in: 'body',
    type: 'string',
    required: true,
    description: 'An http:// or https:// URL, at most 2048 characters.',
  },
  {
    name: 'tenantId',
    in: 'body',
    type: 'integer',
    required: true,
    description: 'A tenant you are a member of.',
  },
  {
    name: 'state',
    in: 'body',
    type: 'ACTIVE | SILENT | INACTIVE',
    description:
      'ACTIVE: checks and alerts. SILENT: checks, no alerts, until silentUntil. INACTIVE: no checks. Default ACTIVE.',
  },
  {
    name: 'silentFor',
    in: 'body',
    type: '1h | 24h | 3d | 7d',
    description:
      'Only for SILENT: how long. Then the monitor is ACTIVE again. Empty: a running silence keeps its end, a new silence gets 24h.',
  },
  {
    name: 'dailyPauseFrom',
    in: 'body',
    type: 'string (HH:mm)',
    description:
      'Start of the daily pause: no checks and no alerts from this time on, each day. Send it with dailyPauseUntil. Both empty: no daily pause (a PUT without them removes it).',
  },
  {
    name: 'dailyPauseUntil',
    in: 'body',
    type: 'string (HH:mm)',
    description:
      'End of the daily pause (not in the pause). Before dailyPauseFrom: the pause goes past midnight. Must not be the same as dailyPauseFrom.',
  },
  {
    name: 'dailyPauseTimezone',
    in: 'body',
    type: 'string',
    description:
      'IANA time zone of the two times, for example Europe/Berlin. The pause follows its summer and winter time. Empty: UTC.',
  },
  {
    name: 'checkInterval',
    in: 'body',
    type: 'integer',
    description: 'Seconds between checks: 15 to 3600, a multiple of 15. Default 15.',
  },
  {
    name: 'connectTimeout',
    in: 'body',
    type: 'integer',
    description: 'Seconds: 1 to 10. Default 5.',
  },
  {
    name: 'alertingThreshold',
    in: 'body',
    type: 'integer',
    description:
      'Seconds a monitor must be down before the alert: at least 15, a multiple of 15. Default 30.',
  },
  {
    name: 'httpHeaders',
    in: 'body',
    type: 'object',
    description: 'Request headers of the check, for example an API key.',
  },
  {
    name: 'statusCodeRegex',
    in: 'body',
    type: 'string',
    description: 'The HTTP status code must match. Empty: 2xx and 3xx are up.',
  },
  {
    name: 'responseBodyRegex',
    in: 'body',
    type: 'string',
    description: 'The response body must match.',
  },
  {
    name: 'prometheusKey',
    in: 'body',
    type: 'string',
    description: 'For a Prometheus metrics URL: the metric to read.',
  },
  {
    name: 'prometheusMinValue / prometheusMaxValue',
    in: 'body',
    type: 'number',
    description: 'The metric must be in this range.',
  },
  {
    name: 'useSystemDns',
    in: 'body',
    type: 'boolean',
    description:
      'true: use the DNS resolver of the server, not the nameservers of the domain. Default false.',
  },
  {
    name: 'showOnPublicPage',
    in: 'body',
    type: 'boolean',
    description: 'Show the monitor on the public status page of its tenant. Default false.',
  },
]

const UPTIME_STATS_EXAMPLE = `{
  "monitorId": 42,
  "monitorName": "Shop checkout",
  "periodType": "SEVEN_DAYS",
  "periodStart": "2026-10-25T09:00:00",
  "periodEnd": "2026-11-01T09:41:12",
  "intervalMinutes": 60,
  "totalChecks": 10080,
  "successfulChecks": 10071,
  "uptimePercentage": 99.91,
  "minResponseTimeMs": 41,
  "maxResponseTimeMs": 1210,
  "avgResponseTimeMs": 88,
  "p99ResponseTimeMs": 402,
  "responseTimeDataPoints": [{ "timestamp": "2026-10-25T09:00:00", "maxResponseTimeMs": 140 }, …],
  "statusDownPeriods": [{ "start": "2026-10-28T03:14:00", "end": "2026-10-28T03:23:00" }],
  "firstCheckAt": "2026-10-25T09:00:00"
}`

const PROM_VECTOR_EXAMPLE = `{
  "status": "success",
  "data": {
    "resultType": "vector",
    "result": [
      {
        "metric": {
          "__name__": "probe_success",
          "monitor": "Shop checkout",
          "monitor_id": "42",
          "instance": "https://shop.example.com/health",
          "tenant": "Shop",
          "job": "status-tacos"
        },
        "value": [1761990000, "1"]
      }
    ]
  }
}`

export const API_GROUPS: EndpointGroup[] = [
  {
    id: 'ref-public',
    title: 'Public status',
    auth: 'none',
    intro:
      'The data of the public status page of a tenant. No sign-in, CORS is open, and answers are cached for 30 seconds. Only monitors with "Show on the public status page" are in it.',
    endpoints: [
      {
        method: 'GET',
        path: '/v1/public/{tenantCode}/status',
        summary: 'The status page data as JSON.',
        params: [
          {
            name: 'tenantCode',
            in: 'path',
            type: 'string',
            required: true,
            description: 'The code of the tenant, as in the page URL /<code>/status.',
          },
        ],
        response: `{
  "tenantName": "Shop",
  "monitors": [
    {
      "name": "Shop checkout",
      "currentStatus": "up",
      "downSince": null,
      "uptimePercentage90d": 99.97
    },
    {
      "name": "Payments",
      "currentStatus": "down",
      "downSince": "2026-11-01T09:12:30",
      "uptimePercentage90d": 99.42
    }
  ]
}`,
        errors: ['404: no tenant with this code, or the tenant is not active.'],
        notes: [
          'currentStatus is null when the monitor has no check yet.',
          'uptimePercentage90d is rounded down. It is null when there are no checks in the 90 days.',
        ],
      },
      {
        method: 'GET',
        path: '/v1/public/{tenantCode}/status/page',
        summary: 'The branded status page as HTML. Any site can embed it in an iframe.',
        notes: [
          'The page runs no scripts: a CSP header blocks them and puts the page in a sandbox.',
        ],
      },
      {
        method: 'GET',
        path: '/v1/public/{tenantCode}/status/assets/{name}',
        summary: 'A font or image that the tenant uploaded for its page.',
      },
    ],
  },
  {
    id: 'ref-prom',
    title: 'Prometheus query API',
    auth: 'token',
    intro:
      'A subset of the Prometheus HTTP API at /prom/api/v1. Grafana can use it as a Prometheus data source, and any Prometheus client can query it. It covers the monitors of all tenants of the token owner. Each endpoint takes GET with query parameters or POST with a form body.',
    endpoints: [
      {
        method: 'GET, POST',
        path: '/prom/api/v1/query',
        summary: 'An instant query: the value of each series at one time.',
        params: [
          {
            name: 'query',
            in: 'query',
            type: 'PromQL',
            required: true,
            description: 'For example probe_success{tenant="Shop"}.',
          },
          {
            name: 'time',
            in: 'query',
            type: 'unix seconds | RFC 3339',
            description: 'Default: now.',
          },
        ],
        response: PROM_VECTOR_EXAMPLE,
        errors: [
          '400 bad_data: the query or a time cannot be parsed.',
          '422 execution: the query is valid but cannot run, for example it loads too much data.',
          '401: no token, a wrong token, or a token with another scope.',
        ],
      },
      {
        method: 'GET, POST',
        path: '/prom/api/v1/query_range',
        summary: 'A range query: one point per step between start and end.',
        params: [
          {
            name: 'query',
            in: 'query',
            type: 'PromQL',
            required: true,
            description: 'For example avg_over_time(probe_success[1h]).',
          },
          {
            name: 'start',
            in: 'query',
            type: 'unix seconds | RFC 3339',
            required: true,
            description: 'The first point.',
          },
          {
            name: 'end',
            in: 'query',
            type: 'unix seconds | RFC 3339',
            required: true,
            description: 'The last point.',
          },
          {
            name: 'step',
            in: 'query',
            type: 'duration',
            required: true,
            description: 'For example 60s, 5m or 1h.',
          },
        ],
        response: `{
  "status": "success",
  "data": {
    "resultType": "matrix",
    "result": [
      {
        "metric": { "monitor": "Shop checkout", "monitor_id": "42", … },
        "values": [[1761986400, "1"], [1761990000, "0.98"]]
      }
    ]
  }
}`,
        notes: ['At most 11,000 points per series and 1,000,000 raw check results per query.'],
      },
      {
        method: 'GET, POST',
        path: '/prom/api/v1/series',
        summary: 'The series that match the selectors.',
        params: [
          {
            name: 'match[]',
            in: 'query',
            type: 'selector',
            description: 'Repeat it for more selectors.',
          },
        ],
      },
      {
        method: 'GET, POST',
        path: '/prom/api/v1/labels',
        summary: 'All label names.',
        params: [
          {
            name: 'match[]',
            in: 'query',
            type: 'selector',
            description: 'Only labels of these series.',
          },
        ],
      },
      {
        method: 'GET, POST',
        path: '/prom/api/v1/label/{name}/values',
        summary: 'All values of one label, for example the monitor names.',
        params: [
          {
            name: 'name',
            in: 'path',
            type: 'string',
            required: true,
            description: 'For example monitor or tenant.',
          },
          {
            name: 'match[]',
            in: 'query',
            type: 'selector',
            description: 'Only values of these series.',
          },
        ],
      },
      {
        method: 'GET, POST',
        path: '/prom/api/v1/metadata',
        summary: 'The type and help text of each metric.',
      },
      {
        method: 'GET, POST',
        path: '/prom/api/v1/status/buildinfo',
        summary: 'The Prometheus version this API behaves like. Grafana reads it.',
        notes: ['/query_exemplars, /rules and /alerts also exist. They always give an empty list.'],
      },
    ],
  },
  {
    id: 'ref-monitors',
    title: 'Monitors',
    auth: 'login',
    intro: 'Create, change and delete monitors. You see the monitors of all your tenants.',
    endpoints: [
      {
        method: 'GET',
        path: '/v1/monitors',
        summary: 'All monitors of your tenants.',
        params: [
          {
            name: 'name',
            in: 'query',
            type: 'string',
            description: 'Only monitors whose name contains this text (not case-sensitive).',
          },
          {
            name: 'activeOnly',
            in: 'query',
            type: 'boolean',
            description: 'Only monitors in the state ACTIVE. Default false.',
          },
        ],
        response: `[ ${MONITOR_EXAMPLE.replace(/\n/g, '\n  ')} ]`,
      },
      {
        method: 'POST',
        path: '/v1/monitors',
        summary: 'Create a monitor. The answer is 201 with the new monitor.',
        params: MONITOR_FIELDS,
        body: MONITOR_BODY,
        response: MONITOR_EXAMPLE,
        errors: [
          '400: a field is not valid. The answer names the field.',
          '403: you are not a member of the tenant.',
          '409 monitor_limit_reached: you have the maximum number of monitors.',
        ],
      },
      {
        method: 'PUT',
        path: '/v1/monitors/{id}',
        summary: 'Replace all fields of a monitor. Send the full monitor, as for POST.',
        body: MONITOR_BODY,
        response: MONITOR_EXAMPLE,
        errors: [
          '403: you are not a member of the tenant in the body.',
          '404: no such monitor in your tenants.',
        ],
      },
      {
        method: 'PATCH',
        path: '/v1/monitors/{id}/state',
        summary: 'Change only the state, for example to pause a monitor during maintenance.',
        params: [
          {
            name: 'state',
            in: 'query',
            type: 'ACTIVE | SILENT | INACTIVE',
            required: true,
            description: 'The new state.',
          },
          {
            name: 'silentFor',
            in: 'query',
            type: '1h | 24h | 3d | 7d',
            description:
              'Only for SILENT: how long. Then the monitor is ACTIVE again. Default 24h. The response has silentUntil.',
          },
        ],
        response: MONITOR_EXAMPLE,
        errors: ['400: silentFor is not 1h, 24h, 3d or 7d.', '404: no such monitor in your tenants.'],
        notes: [
          'PATCH /v1/monitors/{id}/toggle-status is the old form: it switches between ACTIVE and INACTIVE.',
        ],
      },
      {
        method: 'PATCH',
        path: '/v1/monitors/{id}/tenant',
        summary: 'Move a monitor with all its history to another tenant.',
        params: [
          {
            name: 'tenantId',
            in: 'query',
            type: 'integer',
            required: true,
            description: 'The new tenant. You must be a member of both.',
          },
        ],
        response: MONITOR_EXAMPLE,
        errors: [
          '403: you are not a member of the new tenant.',
          '404: no such monitor.',
          '409: the move is not possible.',
        ],
        notes: ['Alert contacts that are limited to selected monitors lose this monitor.'],
      },
      {
        method: 'DELETE',
        path: '/v1/monitors/{id}',
        summary: 'Delete a monitor and its history. The answer is 204.',
        errors: ['404: no such monitor in your tenants.'],
      },
    ],
  },
  {
    id: 'ref-status',
    title: 'Status and uptime',
    auth: 'login',
    intro: 'The current state of each monitor, the last 24 hours and the uptime of 7 or 90 days.',
    endpoints: [
      {
        method: 'GET',
        path: '/v1/monitor-statuses',
        summary: 'The current state of each monitor that is not INACTIVE.',
        response: `[
  {
    "monitorId": 42,
    "monitorName": "Shop checkout",
    "monitorUrl": "https://shop.example.com/health",
    "tenantId": 7,
    "tenant": { … },
    "currentStatus": "down",
    "lastCheckedAt": "2026-11-01T09:41:00",
    "lastUpAt": "2026-11-01T09:12:15",
    "lastDownAt": "2026-11-01T09:41:00",
    "consecutiveFailures": 115,
    "lastResponseTimeMs": 5003,
    "lastStatusCode": 0,
    "lastErrorMessage": "Connect timed out",
    "peerResult": "CONFIRMED",
    "updatedAt": "2026-11-01T09:41:00"
  }
]`,
        notes: [
          'peerResult is what the other Status Tacos server saw before the DOWN alert: CONFIRMED, PEER_UP or NO_ANSWER. It is null when no peer was asked.',
        ],
      },
      {
        method: 'GET',
        path: '/v1/monitor-statuses/{monitorId}/response-time-history-24h',
        summary: 'Response times and outages of the last 24 hours of one monitor.',
        response: `{
  "monitorId": 42,
  "monitorName": "Shop checkout",
  "intervalMinutes": 15,
  "totalDataPoints": 96,
  "uptimePercentage24h": 99.65,
  "totalChecks24h": 1440,
  "successfulChecks24h": 1435,
  "dataPoints": [{ "timestamp": "2026-10-31T09:45:00", "maxResponseTimeMs": 120 }, …],
  "statusDownPeriods": [{ "start": "2026-11-01T03:14:00", "end": "2026-11-01T03:16:00" }],
  "firstCheckAt": "2026-10-31T09:41:15"
}`,
        notes: [
          'GET /v1/monitor-statuses/response-time-history-24h gives the same for all monitors, as a list.',
        ],
      },
      {
        method: 'GET',
        path: '/v1/uptime-stats/{monitorId}/{period}',
        summary: 'Uptime and response time stats of one monitor.',
        params: [
          {
            name: 'period',
            in: 'path',
            type: 'seven_days | ninety_days',
            required: true,
            description: 'The window. 7 days start at a full UTC hour, 90 days at a full UTC day.',
          },
        ],
        response: UPTIME_STATS_EXAMPLE,
        errors: ['400: unknown period.', '404: no such monitor in your tenants.'],
        notes: [
          'uptimePercentage is rounded down. It is null when there are no checks.',
          'Response times count successful checks only.',
          'GET /v1/uptime-stats/{monitorId} gives both periods. GET /v1/uptime-stats?period=seven_days gives one period for all monitors.',
        ],
      },
      {
        method: 'GET',
        path: '/v1/alerts',
        summary: 'A short state list of all monitors: good for a menu bar app or a widget.',
        response: `[
  { "monitorName": "Shop checkout", "tenantName": "Shop", "status": "down", "downtimeStart": "2026-11-01T09:12:30" },
  { "monitorName": "Blog", "tenantName": "Shop", "status": "up", "downtimeStart": null }
]`,
        notes: ['status is up, down, or unknown when there is no check yet.'],
      },
    ],
  },
  {
    id: 'ref-contacts',
    title: 'Alert contacts',
    auth: 'login',
    intro:
      'Where alerts go: email, an HTTP webhook, or Microsoft Teams. Each contact belongs to one tenant.',
    endpoints: [
      {
        method: 'GET',
        path: '/v1/alert-contacts',
        summary: 'All alert contacts of your tenants.',
        params: [
          {
            name: 'activeOnly',
            in: 'query',
            type: 'boolean',
            description: 'Only active contacts. Default false.',
          },
        ],
        notes: [
          'GET /v1/alert-contacts/tenant/{tenantId} gives the contacts of one tenant. GET /v1/alert-contacts/{id} gives one.',
        ],
      },
      {
        method: 'POST',
        path: '/v1/alert-contacts?tenantId={tenantId}',
        summary: 'Create an alert contact in a tenant.',
        params: [
          {
            name: 'tenantId',
            in: 'query',
            type: 'integer',
            required: true,
            description: 'The tenant of the contact.',
          },
          {
            name: 'type',
            in: 'body',
            type: 'EMAIL | HTTP | TEAMS',
            required: true,
            description: 'IOS_PUSH contacts come from the iOS app only.',
          },
          {
            name: 'value',
            in: 'body',
            type: 'string',
            required: true,
            description:
              'The email address, the webhook URL (http or https) or the Teams workflow URL (https).',
          },
          { name: 'name', in: 'body', type: 'string', description: 'At most 100 characters.' },
          { name: 'isActive', in: 'body', type: 'boolean', description: 'Default true.' },
          {
            name: 'httpMethod',
            in: 'body',
            type: 'GET | POST',
            description: 'HTTP only. Default GET.',
          },
          {
            name: 'httpHeaders',
            in: 'body',
            type: 'object',
            description: 'HTTP only. Values can use the placeholders.',
          },
          {
            name: 'httpBody',
            in: 'body',
            type: 'string',
            description: 'HTTP POST only. It can use the placeholders.',
          },
          {
            name: 'httpContentType',
            in: 'body',
            type: 'string',
            description: 'application/json (default) or text/plain.',
          },
          {
            name: 'allMonitors',
            in: 'body',
            type: 'boolean',
            description: 'true: alerts for all monitors of the tenant. Default true.',
          },
          {
            name: 'monitorIds',
            in: 'body',
            type: 'integer[]',
            description: 'With allMonitors false: only these monitors.',
          },
        ],
        body: `{
  "type": "HTTP",
  "name": "Pager webhook",
  "value": "https://hooks.example.com/tacos",
  "httpMethod": "POST",
  "httpContentType": "application/json",
  "httpBody": "{\\"monitor\\":\\"{{MONITOR_NAME}}\\",\\"state\\":\\"{{ALERT_TYPE}}\\",\\"at\\":\\"{{TIMESTAMP}}\\"}",
  "allMonitors": true
}`,
      },
      {
        method: 'PUT',
        path: '/v1/alert-contacts/{id}',
        summary: 'Replace all fields of an alert contact. The body is the same as for POST.',
      },
      {
        method: 'PATCH',
        path: '/v1/alert-contacts/{id}/toggle-status',
        summary: 'Switch a contact between active and not active.',
      },
      {
        method: 'POST',
        path: '/v1/alert-contacts/{id}/test',
        summary: 'Send a test alert to the contact now. ALERT_TYPE is test.',
      },
      {
        method: 'DELETE',
        path: '/v1/alert-contacts/{id}',
        summary: 'Delete an alert contact. The answer is 204.',
      },
    ],
  },
  {
    id: 'ref-tenants',
    title: 'Tenants',
    auth: 'login',
    intro:
      'A tenant is a group of monitors, alert contacts and members, with one public status page.',
    endpoints: [
      {
        method: 'GET',
        path: '/v1/tenants',
        summary: 'Your tenants.',
        params: [
          {
            name: 'activeOnly',
            in: 'query',
            type: 'boolean',
            description: 'Only active tenants. Default false.',
          },
        ],
        response: `[
  {
    "id": 7,
    "name": "Shop",
    "code": "shop",
    "description": "Everything customers touch",
    "isActive": true,
    "createdAt": "2026-09-01T10:00:00",
    "updatedAt": "2026-09-01T10:00:00"
  }
]`,
        notes: ['GET /v1/tenants/{id} and GET /v1/tenants/code/{code} give one tenant.'],
      },
      {
        method: 'POST',
        path: '/v1/tenants',
        summary: 'Create a tenant. You become its first member.',
        params: [
          {
            name: 'name',
            in: 'body',
            type: 'string',
            required: true,
            description: 'At most 100 characters.',
          },
          {
            name: 'code',
            in: 'body',
            type: 'string',
            required: true,
            description: '1 to 50 letters, numbers, - or _. The path of the status page.',
          },
          {
            name: 'description',
            in: 'body',
            type: 'string',
            description: 'At most 500 characters.',
          },
          { name: 'isActive', in: 'body', type: 'boolean', description: 'Default true.' },
        ],
        body: `{ "name": "Shop", "code": "shop", "description": "Everything customers touch" }`,
      },
      {
        method: 'PUT',
        path: '/v1/tenants/{id}',
        summary: 'Change a tenant. A code of null keeps the code.',
        notes: ['A new code moves the public status page to a new URL at once.'],
      },
      {
        method: 'GET',
        path: '/v1/tenants/{id}/users',
        summary: 'The members of a tenant.',
      },
      {
        method: 'POST',
        path: '/v1/tenants/{id}/users?email={email}',
        summary: 'Add a member by email address. DELETE with the same URL removes the member.',
      },
      {
        method: 'GET',
        path: '/v1/tenants/{id}/status-page',
        summary:
          'The template and CSS of the status page. PUT saves them, DELETE resets to the default.',
        notes: [
          'POST /v1/tenants/{id}/status-page/preview renders unsaved template and CSS.',
          'GET …/status-page/assets lists the uploaded files. PUT and DELETE …/assets/{name} change them (raw bytes, at most 1 MB per file).',
        ],
      },
    ],
  },
  {
    id: 'ref-account',
    title: 'Account and API tokens',
    auth: 'login',
    intro: 'Your user record and your API tokens.',
    endpoints: [
      {
        method: 'GET',
        path: '/v1/users/me',
        summary:
          'Your user with its tenants and the monitor limit. The first call creates the user record.',
        response: `{
  "id": 3,
  "email": "you@example.com",
  "firstName": "Ada",
  "lastName": "Lovelace",
  "tenantIds": [7],
  "tenants": [{ "id": 7, "name": "Shop", "code": "shop", … }],
  "isActive": true,
  "maxMonitors": 50,
  "peerTenantId": null,
  "createdAt": "2026-09-01T10:00:00",
  "updatedAt": "2026-09-01T10:00:00"
}`,
        notes: ['maxMonitors is null when there is no limit.'],
      },
      {
        method: 'GET',
        path: '/v1/api-tokens',
        summary: 'Your API tokens, without the token values.',
      },
      {
        method: 'POST',
        path: '/v1/api-tokens',
        summary:
          'Create an API token. Only this answer holds the full token: the server keeps a hash only.',
        params: [
          {
            name: 'name',
            in: 'body',
            type: 'string',
            required: true,
            description: 'For example "Grafana at home". At most 100 characters.',
          },
          {
            name: 'scope',
            in: 'body',
            type: 'GRAFANA | PEER',
            description:
              'GRAFANA (default): the Prometheus query API. PEER: for a second Status Tacos server.',
          },
        ],
        body: `{ "name": "Grafana at home", "scope": "GRAFANA" }`,
        response: `{
  "id": 11,
  "name": "Grafana at home",
  "prefix": "stt_8f3kq2xa",
  "scope": "GRAFANA",
  "createdAt": "2026-11-01T09:00:00",
  "lastUsedAt": null,
  "token": "stt_8f3kq2xa…"
}`,
        errors: [
          '400: a PEER token without membership in the peer tenant.',
          '409: you have 20 tokens, the maximum.',
        ],
      },
      {
        method: 'DELETE',
        path: '/v1/api-tokens/{id}',
        summary: 'Delete an API token. It stops working at once. The answer is 204.',
      },
    ],
  },
]

/** The placeholders of HTTP alert contacts: in the URL, the header values and the body. */
export const ALERT_PLACEHOLDERS: { name: string; description: string }[] = [
  { name: '{{MONITOR_NAME}}', description: 'The name of the monitor.' },
  { name: '{{MONITOR_URL}}', description: 'The URL the monitor checks.' },
  { name: '{{TENANT_NAME}}', description: 'The name of the tenant.' },
  { name: '{{ALERT_TYPE}}', description: 'down, up or test.' },
  { name: '{{STATUS_CODE}}', description: 'The HTTP status code of the check (0: no answer).' },
  { name: '{{RESPONSE_BODY}}', description: 'The response body of the check, if there is one.' },
  { name: '{{TIMESTAMP}}', description: 'The time of the alert, ISO 8601, UTC.' },
]
