<script setup lang="ts">
import LegalPage from '../components/LegalPage.vue'
import { ALERT_PLACEHOLDERS, API_GROUPS, type AuthKind } from './developers/apiReference'

// The examples use the host of this page, so they also fit a self-hosted server.
const apiBase =
  window.location.hostname === 'localhost'
    ? 'http://localhost:8080/api'
    : `https://${window.location.hostname}/api`
const appBase = window.location.origin

const AUTH_LABEL: Record<AuthKind, string> = {
  none: 'No sign-in',
  token: 'API token',
  login: 'Sign-in token',
}

const exportLine = `export TACOS=${apiBase}`

const starterPublic = `${exportLine}

curl "$TACOS/v1/public/shop/status"`

const starterProm = `${exportLine}
export TACOS_TOKEN=stt_…   # Account → API tokens

curl -H "Authorization: Bearer $TACOS_TOKEN" \\
  --data-urlencode 'query=probe_success' \\
  "$TACOS/prom/api/v1/query"`

const starterRest = `${exportLine}
export TACOS_JWT=eyJ…      # the access token of your sign-in

curl -H "Authorization: Bearer $TACOS_JWT" "$TACOS/v1/monitors"`

const useCaseBadge = `<span id="shop-status">…</span>
<script>
  fetch('${apiBase}/v1/public/shop/status')
    .then((r) => r.json())
    .then((page) => {
      const down = page.monitors.filter((m) => m.currentStatus === 'down')
      document.getElementById('shop-status').textContent =
        down.length === 0 ? 'All systems up' : down.length + ' system(s) down'
    })
<\/script>`

const useCaseIframe = `<iframe
  src="${apiBase}/v1/public/shop/status/page"
  title="Shop status"
  style="width: 100%; height: 480px; border: 0"
></iframe>`

const useCaseReport = `#!/bin/sh
# Uptime of the last 30 days per monitor, in percent.
curl -s -H "Authorization: Bearer $TACOS_TOKEN" \\
  --data-urlencode 'query=avg_over_time(tacos_uptime_ratio_1h[30d]) * 100' \\
  "$TACOS/prom/api/v1/query" |
  jq -r '.data.result[] | "\\(.metric.monitor): \\(.value[1] | tonumber | . * 100 | floor / 100)%"'`

const useCaseCi = `# After a deploy: add a monitor for the new service.
curl -X POST -H "Authorization: Bearer $TACOS_JWT" \\
  -H 'Content-Type: application/json' \\
  -d '{"name":"Search API","url":"https://search.example.com/health",
       "tenantId":7,"checkInterval":60,"alertingThreshold":120}' \\
  "$TACOS/v1/monitors"

# During maintenance: no alerts for 1 hour, but keep the checks.
curl -X PATCH -H "Authorization: Bearer $TACOS_JWT" \\
  "$TACOS/v1/monitors/42/state?state=SILENT&silentFor=1h"`

const useCaseWebhook = `{
  "type": "HTTP",
  "name": "Ops chat",
  "value": "https://chat.example.com/hooks/abc123",
  "httpMethod": "POST",
  "httpContentType": "application/json",
  "httpBody": "{\\"text\\":\\"{{MONITOR_NAME}} is {{ALERT_TYPE}} ({{STATUS_CODE}})\\"}",
  "allMonitors": true
}`

const errorExample = `{ "error": "monitor_limit_reached", "message": "You have 50 monitors, the maximum." }`
</script>

<template>
  <LegalPage
    title="Developers"
    description="Read your monitors, uptime and outages from your own code, scripts and dashboards."
  >
    <p class="intro">
      Status Tacos has three HTTP APIs. All answers are JSON. Pick the API by what you want to
      build.
    </p>

    <div class="dev-apis">
      <a href="#ref-public" class="info-card dev-api">
        <span class="dev-auth dev-auth-none">No sign-in</span>
        <h3>Public status</h3>
        <p>The data of a public status page. For a badge or a widget on your website.</p>
      </a>
      <a href="#ref-prom" class="info-card dev-api">
        <span class="dev-auth dev-auth-token">API token</span>
        <h3>Prometheus queries</h3>
        <p>Check results and uptime as metrics. For Grafana, reports and scripts.</p>
      </a>
      <a href="#ref-monitors" class="info-card dev-api">
        <span class="dev-auth dev-auth-login">Sign-in token</span>
        <h3>REST API</h3>
        <p>Everything the web app does: monitors, alert contacts, tenants, stats.</p>
      </a>
    </div>

    <h2>Quick start</h2>
    <p>
      The base URL of this server is <code>{{ apiBase }}</code
      >. The examples keep it in the shell variable <code>TACOS</code>.
    </p>

    <h3>1. Read a public status page</h3>
    <p>
      Use the code of a tenant (the <code>shop</code> in <code>{{ appBase }}/shop/status</code>).
      Only monitors with "Show on the public status page" are in the answer.
    </p>
    <pre class="dev-code"><code>{{ starterPublic }}</code></pre>

    <h3>2. Query metrics with an API token</h3>
    <p>
      Open <router-link to="/account">Account → API tokens</router-link> and create a token. Copy
      it: it is shown once. Then ask for the current state of all your monitors:
    </p>
    <pre class="dev-code"><code>{{ starterProm }}</code></pre>

    <h3>3. Call the REST API</h3>
    <p>
      The REST API under <code>/v1</code> uses the access token of a sign-in, as the web app does.
      See <a href="#authentication">Authentication</a>.
    </p>
    <pre class="dev-code"><code>{{ starterRest }}</code></pre>

    <h2>Authentication</h2>
    <div class="info-card">
      <h3><span class="dev-auth dev-auth-none">No sign-in</span> Public status</h3>
      <p>
        <code>/v1/public/…</code> needs no header. Any website can call it (CORS is open). Answers
        are cached for 30 seconds, so call it at most every 30 seconds.
      </p>
    </div>
    <div class="info-card">
      <h3><span class="dev-auth dev-auth-token">API token</span> Prometheus queries</h3>
      <p>
        <code>/prom/…</code> takes an API token of the scope <code>GRAFANA</code>. Send it as
        <code>Authorization: Bearer stt_…</code>, or as the password of HTTP basic auth with any
        user name. The token can read the monitors of all tenants of its owner. It can change
        nothing.
      </p>
      <p>
        A user can have 20 tokens. The server keeps only a hash of each token. Delete a token on the
        account page and it stops working at once.
      </p>
    </div>
    <div class="info-card">
      <h3><span class="dev-auth dev-auth-login">Sign-in token</span> REST API</h3>
      <p>
        <code>/v1/…</code> takes an OpenID Connect access token (a JWT) of your login, as
        <code>Authorization: Bearer eyJ…</code>. The login server is
        <code>https://id.oglimmer.de/realms/status-tacos</code>, the client ID is
        <code>status-tacos-frontend</code> (authorization code flow with PKCE). The token expires
        soon; get a new one with the refresh token.
      </p>
      <p>
        API tokens do not work for <code>/v1</code>. A <code>401</code> means the token is missing
        or expired.
      </p>
    </div>

    <h2>Use cases</h2>

    <h3>A status badge on your website</h3>
    <p>
      Show "All systems up" in your footer. The public API needs no token, so the code can be in the
      browser.
    </p>
    <pre class="dev-code"><code>{{ useCaseBadge }}</code></pre>

    <h3>Embed the status page</h3>
    <p>
      The branded status page of a tenant works in an iframe on any site. It runs no scripts. Brand
      it in <router-link to="/tenants">Tenants → Status Page</router-link>.
    </p>
    <pre class="dev-code"><code>{{ useCaseIframe }}</code></pre>

    <h3>A Grafana dashboard</h3>
    <p>
      In Grafana, add a data source of the type <strong>Prometheus</strong> with the URL
      <code>{{ apiBase }}/prom</code>. Set <strong>Basic auth</strong> with any user name and your
      API token as the password. Then click <strong>Save &amp; test</strong>.
    </p>
    <table class="dev-table">
      <thead>
        <tr>
          <th>Metric</th>
          <th>Value</th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <td><code>probe_success</code></td>
          <td>Each check: 1 = up, 0 = down. Raw data, kept for 7 days.</td>
        </tr>
        <tr>
          <td><code>probe_duration_seconds</code></td>
          <td>Each check: the response time.</td>
        </tr>
        <tr>
          <td><code>probe_http_status_code</code></td>
          <td>Each check: the HTTP status code.</td>
        </tr>
        <tr>
          <td><code>tacos_uptime_ratio_1h</code></td>
          <td>Each hour: the uptime from 0 to 1. Use it for ranges longer than 7 days.</td>
        </tr>
        <tr>
          <td><code>tacos_duration_seconds_avg_1h</code></td>
          <td>Each hour: the average response time of successful checks.</td>
        </tr>
        <tr>
          <td><code>tacos_duration_seconds_max_1h</code></td>
          <td>Each hour: the maximum response time of successful checks.</td>
        </tr>
      </tbody>
    </table>
    <p>
      Each series has the labels <code>monitor</code>, <code>monitor_id</code>,
      <code>instance</code> (the URL), <code>tenant</code> and <code>job="status-tacos"</code>. Some
      queries:
    </p>
    <pre class="dev-code"><code># uptime in percent over the dashboard range (max 7 days)
avg_over_time(probe_success[$__range]) * 100

# uptime in percent over 30 days
avg_over_time(tacos_uptime_ratio_1h[30d]) * 100

# response time in ms
probe_duration_seconds * 1000

# alert rule: a monitor is down
probe_success == 0</code></pre>
    <p>
      Supported PromQL: selectors with <code>= != =~ !~</code>; <code>avg_over_time</code>,
      <code>min_over_time</code>, <code>max_over_time</code>, <code>sum_over_time</code>,
      <code>count_over_time</code>, <code>last_over_time</code>; <code>abs</code>,
      <code>ceil</code>, <code>floor</code>, <code>round</code>, <code>clamp_min</code>,
      <code>clamp_max</code>, <code>scalar</code>, <code>vector</code>, <code>time</code>;
      <code>sum</code>, <code>avg</code>, <code>min</code>, <code>max</code>,
      <code>count</code> with <code>by</code> or <code>without</code>; arithmetic and comparison
      operators. Not supported: <code>rate</code> and other counter functions, <code>offset</code>,
      <code>@</code>, subqueries, set operators,
      <code>on</code>/<code>ignoring</code>/<code>group_left</code>.
    </p>

    <h3>A monthly uptime report</h3>
    <p>A shell script with the same API token. It needs <code>curl</code> and <code>jq</code>.</p>
    <pre class="dev-code"><code>{{ useCaseReport }}</code></pre>

    <h3>Monitors from your deploy pipeline</h3>
    <p>
      Add a monitor when you ship a new service. Set it to <code>SILENT</code> during maintenance:
      it keeps the checks but sends no alerts. After 1h, 24h, 3d or 7d the alerts start again.
    </p>
    <pre class="dev-code"><code>{{ useCaseCi }}</code></pre>

    <h3>Alerts into your own system</h3>
    <p>
      An alert contact of the type <code>HTTP</code> calls your URL when a monitor goes down and
      when it is up again. Create it in
      <router-link to="/alert-contacts">Alert Contacts</router-link> or with
      <code>POST /v1/alert-contacts?tenantId=7</code>:
    </p>
    <pre class="dev-code"><code>{{ useCaseWebhook }}</code></pre>
    <p>The URL, the header values and the body can use these placeholders:</p>
    <table class="dev-table">
      <tbody>
        <tr v-for="p in ALERT_PLACEHOLDERS" :key="p.name">
          <td>
            <code>{{ p.name }}</code>
          </td>
          <td>{{ p.description }}</td>
        </tr>
      </tbody>
    </table>

    <h2>Conventions</h2>
    <ul>
      <li>
        Times are ISO 8601 in UTC, without a zone: <code>2026-11-01T09:41:00</code>. The Prometheus
        API uses unix seconds.
      </li>
      <li>Requests with a body send <code>Content-Type: application/json</code>.</li>
      <li>
        A REST call sees only the data of your tenants. For data of another tenant the answer is
        <code>403</code> or <code>404</code>.
      </li>
      <li>
        <code>PUT</code> replaces all fields. Send the full object, not only the changed fields.
      </li>
      <li>Some errors have a JSON body with a <code>message</code> for people:</li>
    </ul>
    <pre class="dev-code"><code>{{ errorExample }}</code></pre>
    <table class="dev-table">
      <tbody>
        <tr>
          <td><code>400</code></td>
          <td>A parameter or field is not valid.</td>
        </tr>
        <tr>
          <td><code>401</code></td>
          <td>No token, a wrong token, or an expired token.</td>
        </tr>
        <tr>
          <td><code>403</code></td>
          <td>You are not a member of the tenant.</td>
        </tr>
        <tr>
          <td><code>404</code></td>
          <td>The object does not exist in your tenants.</td>
        </tr>
        <tr>
          <td><code>409</code></td>
          <td>A limit is reached, or the change is not possible.</td>
        </tr>
      </tbody>
    </table>

    <h2>API reference</h2>
    <p>
      All paths are relative to <code>{{ apiBase }}</code
      >.
    </p>

    <section v-for="group in API_GROUPS" :id="group.id" :key="group.id" class="dev-group">
      <h3 class="dev-group-title">
        {{ group.title }}
        <span class="dev-auth" :class="`dev-auth-${group.auth}`">{{ AUTH_LABEL[group.auth] }}</span>
      </h3>
      <p>{{ group.intro }}</p>

      <details v-for="ep in group.endpoints" :key="ep.method + ep.path" class="dev-endpoint">
        <summary>
          <span
            class="dev-method"
            :class="`dev-method-${ep.method.split(',')[0]?.toLowerCase()}`"
            >{{ ep.method }}</span
          >
          <code class="dev-path">{{ ep.path }}</code>
          <span class="dev-summary">{{ ep.summary }}</span>
        </summary>

        <div class="dev-endpoint-body">
          <table v-if="ep.params?.length" class="dev-table">
            <thead>
              <tr>
                <th>Parameter</th>
                <th>In</th>
                <th>Type</th>
                <th>Description</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="p in ep.params" :key="p.name">
                <td>
                  <code>{{ p.name }}</code
                  ><span v-if="p.required" class="dev-required" title="required">*</span>
                </td>
                <td>{{ p.in }}</td>
                <td>
                  <code>{{ p.type }}</code>
                </td>
                <td>{{ p.description }}</td>
              </tr>
            </tbody>
          </table>

          <template v-if="ep.body">
            <h4>Request body</h4>
            <pre class="dev-code"><code>{{ ep.body }}</code></pre>
          </template>

          <template v-if="ep.response">
            <h4>Answer</h4>
            <pre class="dev-code"><code>{{ ep.response }}</code></pre>
          </template>

          <template v-if="ep.errors?.length">
            <h4>Errors</h4>
            <ul>
              <li v-for="e in ep.errors" :key="e">{{ e }}</li>
            </ul>
          </template>

          <ul v-if="ep.notes?.length" class="dev-notes">
            <li v-for="n in ep.notes" :key="n">{{ n }}</li>
          </ul>
        </div>
      </details>
    </section>
  </LegalPage>
</template>

<style scoped>
.dev-apis {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(12rem, 1fr));
  gap: 1rem;
  margin: 1.5rem 0 0;
}

.legal-page .legal-content a.dev-api {
  display: block;
  margin: 0;
  color: var(--ink);
  font-weight: 400;
  text-decoration: none;
}

.legal-page .legal-content a.dev-api:hover {
  background: var(--paper);
}

.dev-api h3 {
  margin: 0.6rem 0 0.4rem;
}

.dev-api p {
  font-size: 0.95rem;
  line-height: 1.45;
}

.dev-auth {
  display: inline-block;
  padding: 0.05rem 0.5rem;
  border: 1.5px solid var(--ink-on-light);
  border-radius: 999px;
  color: var(--ink-on-light);
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.02em;
  vertical-align: middle;
  white-space: nowrap;
}

.dev-auth-none {
  background: #c9ecd9;
}

.dev-auth-token {
  background: var(--masa);
}

.dev-auth-login {
  background: #f6c6bd;
}

.info-card h3 .dev-auth {
  margin-right: 0.4rem;
}

.dev-code {
  margin: 0 0 1.25rem;
  padding: 1rem 1.1rem;
  overflow-x: auto;
  background: var(--code-bg);
  color: var(--code-fg);
  border-radius: 0.6rem;
  font-family: var(--mono);
  font-size: 0.85rem;
  line-height: 1.5;
  tab-size: 2;
}

/* Undo the inline code style of the legal text inside a block. */
.legal-page .legal-content .dev-code code {
  padding: 0;
  background: none;
  font-size: inherit;
  color: inherit;
  white-space: pre;
}

.dev-table {
  width: 100%;
  margin: 0 0 1.25rem;
  border-collapse: collapse;
  font-size: 0.92rem;
  line-height: 1.45;
}

.dev-table th,
.dev-table td {
  padding: 0.45rem 0.6rem;
  border-bottom: 1px solid var(--hairline);
  text-align: left;
  vertical-align: top;
}

.dev-table th {
  font-weight: 700;
  border-bottom: var(--line);
}

.dev-table td:first-child {
  white-space: nowrap;
}

.dev-required {
  color: var(--salsa);
  font-weight: 700;
  margin-left: 0.15rem;
}

.dev-group {
  margin-top: 2rem;
  scroll-margin-top: 1.5rem;
}

.legal-page .legal-content .dev-group-title {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  font-size: 1.2rem;
  margin-bottom: 0.4rem;
}

.dev-endpoint {
  margin: 0 0 0.6rem;
  border: var(--line);
  border-radius: 0.6rem;
  background: var(--paper);
}

.dev-endpoint[open] {
  background: var(--paper-bright);
}

.dev-endpoint summary {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0.3rem 0.7rem;
  padding: 0.6rem 0.9rem;
  cursor: pointer;
  list-style: none;
}

.dev-endpoint summary::-webkit-details-marker {
  display: none;
}

.dev-endpoint summary::before {
  content: '▸';
  transition: transform 0.15s;
}

.dev-endpoint[open] summary::before {
  transform: rotate(90deg);
}

.dev-method {
  min-width: 3.6rem;
  padding: 0.05rem 0.4rem;
  border-radius: 0.3rem;
  font-family: var(--mono);
  font-size: 0.75rem;
  font-weight: 700;
  text-align: center;
  color: var(--ink-on-light);
  background: #e9f2ee;
}

.dev-method-get {
  background: #c9ecd9;
}

.dev-method-post {
  background: #cfe3f7;
}

.dev-method-put,
.dev-method-patch {
  background: #fbe3ae;
}

.dev-method-delete {
  background: #f6c6bd;
}

.legal-page .legal-content .dev-path {
  padding: 0;
  background: none;
  font-weight: 700;
  overflow-wrap: anywhere;
}

.dev-summary {
  flex-basis: 100%;
  padding-left: 1.3rem;
  color: var(--ink-soft);
  font-size: 0.95rem;
}

.dev-endpoint-body {
  padding: 0.25rem 0.9rem 0.5rem;
  border-top: 1px solid var(--hairline);
}

.dev-endpoint-body h4 {
  margin: 1rem 0 0.4rem;
  font-size: 0.95rem;
}

.dev-notes {
  color: var(--ink-soft);
  font-size: 0.95rem;
}

/* Long URLs and metric names break on a narrow screen. */
.legal-page .legal-content code {
  overflow-wrap: anywhere;
}

@media (max-width: 720px) {
  .dev-table td:first-child {
    white-space: normal;
  }

  /* A wide table scrolls by itself, not the page. */
  .dev-table {
    display: block;
    overflow-x: auto;
  }
}
</style>
