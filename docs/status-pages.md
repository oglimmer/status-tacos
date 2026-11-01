# Public status pages

Each tenant has a public status page at `/<tenant-code>/status`. Anyone can open it without
sign-in. It shows only the monitors with "Show on the public status page", and for each only:

- the name,
- the current status,
- the start of the current outage,
- the 90-day uptime.

URLs, headers, response times and alert contacts are never on the page.

## Turn it on

1. Edit a monitor (or select many, see [monitors.md](monitors.md)) and tick
   **Show on the public status page**.
2. Open **Tenants → Status Page** to see and brand the page.

The tenant code is the path of the page. You can change it in the tenant form (1 to 50 letters,
numbers, `-` or `_`). The old URL stops working at once, so update your links.

## Branding

The editor (**Tenants → Status Page**) has a live preview with your real data, on desktop and on
mobile.

- **CSS:** set CSS variables for colors, fonts and the logo (`--brand`, `--bg`, `--font`,
  `--logo`, `--logo-width`, …), or write any CSS. Your CSS is in the cascade layer `tenant` and
  always wins over the base styles in the layer `base`.
- **Template:** replace the page body with your own [Mustache](https://mustache.github.io/)
  template. The **Reference** tab of the editor lists all template data and CSS variables.
- **Files:** upload fonts and images and use them as `asset:<name>`, for example
  `url(asset:inter.woff2)` in the CSS or `<img src="asset:logo.svg">` in the template. The server
  serves them from its own host, so the page contacts no third-party CDN and sends no visitor IP
  addresses to other hosts (GDPR). At most 1 MB per file, 20 files and 5 MB per tenant.

Examples: [status-page-themes/](status-page-themes/).

## Safety

Tenant HTML can never run code:

- Mustache has no logic and escapes all data.
- The server removes scripts, event handlers, forms and frames from the template.
- The CSS must not contain `<`. Template and CSS: at most 64 KB each.
- The page is served with a CSP that blocks scripts, in a sandbox with an opaque origin.
- The content type of an uploaded file comes from its file extension, never from the upload. So
  an SVG cannot run scripts.

Any site may embed the page in a frame.

## API

| Request | What it gives |
| --- | --- |
| `GET /api/v1/public/{code}/status` | The data of the page (JSON), no sign-in |
| `GET /api/v1/public/{code}/status/page` | The rendered page (HTML), no sign-in |
| `GET/PUT/DELETE /api/v1/tenants/{id}/status-page` | The template and CSS of the tenant |
| `POST /api/v1/tenants/{id}/status-page/preview` | A preview with unsaved template and CSS |
| `GET /api/v1/tenants/{id}/status-page/assets`, `PUT/DELETE …/assets/{name}` | The uploaded files of the tenant |
| `GET /api/v1/public/{code}/status/assets/{name}` | One uploaded file, no sign-in |

Data and HTML are cached per tenant for 30 seconds. The page reloads itself every 60 seconds.
Times are in UTC.
