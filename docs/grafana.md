# Grafana

Status Tacos speaks a subset of the Prometheus HTTP API. Grafana can use it as a Prometheus data
source, without a Prometheus server.

## Set up

1. In Status Tacos, open **Account → API tokens** and create a token. Copy it: it is shown once.
2. In Grafana, add a data source of type **Prometheus**.
   - URL: `https://<your-status-tacos>/api/prom`
   - Auth: **Basic auth** with any user name and the token as password, or a custom HTTP header
     `Authorization` with the value `Bearer <token>`.
3. Click **Save & test**.

The token can read the monitors of all tenants of its user. Delete it on the account page to stop
access.

## Metrics

Labels of every series: `monitor` (name), `monitor_id`, `instance` (URL), `tenant` (name),
`job="status-tacos"`.

| Metric                          | Source             | Value                                         |
|---------------------------------|--------------------|-----------------------------------------------|
| `probe_success`                 | each check         | 1 = up, 0 = down                              |
| `probe_duration_seconds`        | each check         | response time                                 |
| `probe_http_status_code`        | each check         | HTTP status code                              |
| `tacos_uptime_ratio_1h`         | hourly roll-up     | uptime of the hour (0 to 1), interval-weighted |
| `tacos_duration_seconds_avg_1h` | hourly roll-up     | average response time of successful checks    |
| `tacos_duration_seconds_max_1h` | hourly roll-up     | maximum response time of successful checks    |

The per-check metrics come from the raw check results, kept for `monitor.cleanup.retention-days`
(default 7). For longer ranges use the `tacos_*_1h` metrics. Their sample is at the end of the hour
and appears after the hour is rolled up.

An instant query uses the newest sample within 5 minutes, or within 2 check intervals for slow
monitors (2 hours for the hourly metrics).

## Supported PromQL

- Selectors: `metric{label="v", label!="v", label=~"re", label!~"re"}`
- Range selectors in `avg_over_time`, `min_over_time`, `max_over_time`, `sum_over_time`,
  `count_over_time`, `last_over_time`
- Functions: `abs`, `ceil`, `floor`, `round`, `clamp_min`, `clamp_max`, `scalar`, `vector`, `time`
- Aggregations: `sum`, `avg`, `min`, `max`, `count`, with `by (...)` or `without (...)`
- Operators: `+ - * / % ^`, `== != > < >= <=` (with `bool`). Vector-to-vector matches on all
  labels but the metric name.

Not supported: `rate` and other counter functions, `offset`, `@`, subqueries, `and`/`or`/`unless`,
`on`/`ignoring`/`group_left`, `topk` and the other aggregations.

A query may load at most 1,000,000 raw check results, and return at most 11,000 points per series.

## Examples

```promql
# up or down now
probe_success

# uptime in percent over the dashboard range (raw data, max 7 days)
avg_over_time(probe_success[$__range]) * 100

# uptime in percent over 30 days
avg_over_time(tacos_uptime_ratio_1h[30d]) * 100

# response time in ms
probe_duration_seconds * 1000

# alert rule: monitor down
probe_success == 0
```
