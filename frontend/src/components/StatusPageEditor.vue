<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import FormModal from './FormModal.vue'
import { apiService, type StatusPageAsset, type StatusPageConfig, type Tenant } from '../services/api'
import { showErrorToast } from '../services/toasts'

const props = defineProps<{
  tenant: Tenant
}>()

const emit = defineEmits<{
  close: []
}>()

type Tab = 'template' | 'css' | 'files' | 'reference'

const PREVIEW_DELAY_MS = 400

const config = ref<StatusPageConfig | null>(null)
const htmlTemplate = ref('')
const css = ref('')
const saved = ref({ htmlTemplate: '', css: '' })
const tab = ref<Tab>('css')
const device = ref<'desktop' | 'mobile'>('desktop')

const previewHtml = ref('')
const previewError = ref<string | null>(null)
const removed = ref<string[]>([])
const unknownAssets = ref<string[]>([])
const assets = ref<StatusPageAsset[]>([])
const isUploading = ref(false)
const copiedAsset = ref<string | null>(null)
const isSaving = ref(false)
const loadError = ref<string | null>(null)
const copied = ref(false)

const publicPath = computed(() => `/${encodeURIComponent(props.tenant.code)}/status`)
const embedCode = computed(
  () =>
    `<iframe src="${apiService.publicStatusPageUrl(props.tenant.code)}" title="${props.tenant.name} status" ` +
    `style="width:100%;height:600px;border:0" loading="lazy"></iframe>`
)

const isDirty = computed(
  () => htmlTemplate.value !== saved.value.htmlTemplate || css.value !== saved.value.css
)
const isCustom = computed(() => !!(config.value?.htmlTemplate || config.value?.css))

const applyConfig = (value: StatusPageConfig) => {
  config.value = value
  htmlTemplate.value = value.htmlTemplate ?? ''
  css.value = value.css ?? ''
  saved.value = { htmlTemplate: htmlTemplate.value, css: css.value }
}

// The preview renders on the server, with the same rules and the live data of the tenant.
let previewTimer: ReturnType<typeof setTimeout> | undefined
let previewRun = 0
const refreshPreview = async () => {
  const run = ++previewRun
  try {
    const result = await apiService.previewStatusPage(props.tenant.id, {
      htmlTemplate: htmlTemplate.value,
      css: css.value
    })
    if (run !== previewRun) return // An older answer after a newer request
    previewError.value = result.error ?? null
    removed.value = result.removed
    unknownAssets.value = result.unknownAssets ?? []
    if (result.html) previewHtml.value = result.html
  } catch (err) {
    console.error('Status page preview failed:', err)
  }
}

watch([htmlTemplate, css], () => {
  clearTimeout(previewTimer)
  previewTimer = setTimeout(refreshPreview, PREVIEW_DELAY_MS)
})

const save = async () => {
  if (isSaving.value || previewError.value) return
  isSaving.value = true
  try {
    applyConfig(
      await apiService.saveStatusPage(props.tenant.id, {
        htmlTemplate: htmlTemplate.value,
        css: css.value
      })
    )
  } catch (err) {
    console.error('Saving the status page failed:', err)
  } finally {
    isSaving.value = false
  }
}

const resetToDefault = async () => {
  isSaving.value = true
  try {
    await apiService.resetStatusPage(props.tenant.id)
    applyConfig(await apiService.getStatusPage(props.tenant.id))
  } catch (err) {
    console.error('Resetting the status page failed:', err)
  } finally {
    isSaving.value = false
  }
}

// Uploads one file after the other; a failed file shows a toast, the others still upload.
const uploadAssets = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  if (!files.length) return
  isUploading.value = true
  try {
    for (const file of files) {
      try {
        await apiService.uploadStatusPageAsset(props.tenant.id, file)
      } catch (err) {
        console.error(`Uploading ${file.name} failed:`, err)
      }
    }
    assets.value = await apiService.getStatusPageAssets(props.tenant.id)
    await refreshPreview()
  } finally {
    isUploading.value = false
  }
}

const deleteAsset = async (name: string) => {
  try {
    await apiService.deleteStatusPageAsset(props.tenant.id, name)
    assets.value = assets.value.filter(a => a.name !== name)
    await refreshPreview()
  } catch (err) {
    console.error(`Deleting ${name} failed:`, err)
  }
}

const copyReference = async (asset: StatusPageAsset) => {
  try {
    await navigator.clipboard.writeText(asset.reference)
    copiedAsset.value = asset.name
    setTimeout(() => (copiedAsset.value = null), 2000)
  } catch {
    showErrorToast('Could not copy. Select the text and copy it by hand.')
  }
}

const formatSize = (bytes: number) =>
  bytes < 1024 ? `${bytes} B` : `${(bytes / 1024).toFixed(bytes < 10240 ? 1 : 0)} KB`

const startFromDefaultTemplate = () => {
  if (config.value) htmlTemplate.value = config.value.defaultHtmlTemplate
}

const insertBrandVariables = () => {
  if (!config.value) return
  css.value = css.value.trim() ? `${config.value.starterCss}\n${css.value}` : config.value.starterCss
}

const copyEmbedCode = async () => {
  try {
    await navigator.clipboard.writeText(embedCode.value)
    copied.value = true
    setTimeout(() => (copied.value = false), 2000)
  } catch {
    showErrorToast('Could not copy. Select the code and copy it by hand.')
  }
}

// Tab inserts two spaces. Shift+Tab still leaves the editor (keyboard users).
const indent = (event: KeyboardEvent, target: 'template' | 'css') => {
  const el = event.target as HTMLTextAreaElement
  const start = el.selectionStart
  const end = el.selectionEnd
  const value = el.value.slice(0, start) + '  ' + el.value.slice(end)
  if (target === 'template') htmlTemplate.value = value
  else css.value = value
  requestAnimationFrame(() => el.setSelectionRange(start + 2, start + 2))
}

// Ctrl+S / Cmd+S saves
const onKeydown = (event: KeyboardEvent) => {
  if ((event.metaKey || event.ctrlKey) && event.key === 's') {
    event.preventDefault()
    save()
  }
}

onMounted(async () => {
  window.addEventListener('keydown', onKeydown)
  try {
    const [value, files] = await Promise.all([
      apiService.getStatusPage(props.tenant.id),
      apiService.getStatusPageAssets(props.tenant.id)
    ])
    applyConfig(value)
    assets.value = files
    await refreshPreview()
  } catch (err) {
    console.error('Loading the status page failed:', err)
    loadError.value = 'The status page settings could not be loaded.'
  }
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
  clearTimeout(previewTimer)
})

const VARIABLES: { name: string; text: string }[] = [
  { name: '{{tenantName}}', text: 'Name of the tenant' },
  { name: '{{headline}}', text: '"All systems up", "1 system down", …' },
  { name: '{{#anyDown}}…{{/anyDown}}', text: 'Shown when at least one monitor is down' },
  { name: '{{#allUp}}…{{/allUp}}', text: 'Shown when no monitor is down' },
  { name: '{{downCount}} {{monitorCount}}', text: 'Counts' },
  { name: '{{#hasMonitors}}…{{/hasMonitors}}', text: 'Shown when the page has monitors; {{^hasMonitors}} when not' },
  { name: '{{#monitors}}…{{/monitors}}', text: 'Repeats for each monitor, in name order' },
  { name: '  {{name}}', text: 'Monitor name' },
  { name: '  {{status}}', text: 'up, down or unknown: good for CSS classes' },
  { name: '  {{statusLabel}}', text: 'Up, Down or No data' },
  { name: '  {{#isUp}} {{#isDown}} {{#isUnknown}}', text: 'Sections for one status' },
  { name: '  {{downSince}} {{downSinceIso}}', text: 'Start of the outage (UTC), for display and for <time datetime>' },
  { name: '  {{downFor}}', text: 'Length of the outage, for example 2h 5m' },
  { name: '  {{uptime90d}} {{uptimeValue}}', text: '"99.95%" or "N/A", and the bare number' },
  { name: '  {{uptimeLevel}}', text: 'excellent, good, warning, poor or no-data' },
  { name: '{{updatedAt}} {{updatedAtIso}}', text: 'Time of the data (UTC)' }
]

const CSS_VARIABLES = [
  '--bg', '--surface', '--brand', '--brand-text', '--accent', '--text', '--text-muted', '--border',
  '--up', '--down', '--unknown', '--radius', '--width', '--font', '--heading-font', '--mono',
  '--logo', '--logo-width', '--logo-height'
]
</script>

<template>
  <FormModal :title="`Status page: ${tenant.name}`" wide @close="emit('close')">
    <div v-if="loadError" class="error-message">{{ loadError }}</div>

    <div v-else class="editor">
      <div class="links">
        <span>
          Public page:
          <router-link :to="publicPath" target="_blank">{{ publicPath }}</router-link>
        </span>
        <button type="button" class="btn btn-outline btn-sm" @click="copyEmbedCode">
          {{ copied ? 'Copied' : 'Copy embed code' }}
        </button>
        <span v-if="!isCustom" class="badge">Default look</span>
        <span v-else class="badge custom">Custom look</span>
      </div>

      <div class="panes">
        <section class="code-pane" aria-label="Code">
          <div class="tabs" role="tablist">
            <button type="button" role="tab" :aria-selected="tab === 'css'" :class="{ active: tab === 'css' }" @click="tab = 'css'">CSS</button>
            <button type="button" role="tab" :aria-selected="tab === 'template'" :class="{ active: tab === 'template' }" @click="tab = 'template'">HTML template</button>
            <button type="button" role="tab" :aria-selected="tab === 'files'" :class="{ active: tab === 'files' }" @click="tab = 'files'">Files</button>
            <button type="button" role="tab" :aria-selected="tab === 'reference'" :class="{ active: tab === 'reference' }" @click="tab = 'reference'">Reference</button>
          </div>

          <div v-show="tab === 'css'" class="tab-body">
            <p class="hint">
              Your CSS always wins over the base styles (it is in a later
              <a href="https://developer.mozilla.org/docs/Web/CSS/@layer" target="_blank" rel="noopener">cascade layer</a>).
              For most brands, the variables are enough.
              <button type="button" class="link-btn" @click="insertBrandVariables">Insert brand variables</button>
            </p>
            <textarea
              v-model="css"
              class="code"
              spellcheck="false"
              aria-label="CSS"
              placeholder=":root { --brand: #ff6600; }"
              @keydown.tab.exact.prevent="indent($event, 'css')"
            ></textarea>
          </div>

          <div v-show="tab === 'template'" class="tab-body">
            <p class="hint">
              The body of the page, as a <a href="https://mustache.github.io/mustache.5.html" target="_blank" rel="noopener">Mustache</a>
              template. Empty: the default template.
              <button v-if="!htmlTemplate.trim()" type="button" class="link-btn" @click="startFromDefaultTemplate">
                Start from the default template
              </button>
            </p>
            <textarea
              v-model="htmlTemplate"
              class="code"
              spellcheck="false"
              aria-label="HTML template"
              placeholder="Empty: the default template"
              @keydown.tab.exact.prevent="indent($event, 'template')"
            ></textarea>
          </div>

          <div v-show="tab === 'files'" class="tab-body files">
            <p class="hint">
              Fonts and images for your page. This server hosts them, so the browsers of your visitors
              contact no other host. Use a file as <code>asset:</code> and its name:
              <code>url(asset:inter.woff2)</code> in the CSS, <code>&lt;img src="asset:logo.svg"&gt;</code> in the template.
            </p>
            <label class="btn btn-outline btn-sm upload" :class="{ disabled: isUploading }">
              {{ isUploading ? 'Uploading…' : 'Upload files' }}
              <input
                type="file"
                multiple
                accept=".woff2,.woff,.ttf,.otf,.png,.jpg,.jpeg,.gif,.webp,.avif,.svg,.ico"
                :disabled="isUploading"
                @change="uploadAssets"
              />
            </label>
            <p v-if="!assets.length" class="hint">No files yet.</p>
            <table v-else class="asset-list">
              <tbody>
                <tr v-for="asset in assets" :key="asset.name">
                  <td><code>{{ asset.reference }}</code></td>
                  <td class="size">{{ formatSize(asset.size) }}</td>
                  <td class="actions">
                    <button type="button" class="link-btn" @click="copyReference(asset)">
                      {{ copiedAsset === asset.name ? 'Copied' : 'Copy' }}
                    </button>
                    <button type="button" class="link-btn danger" @click="deleteAsset(asset.name)">Delete</button>
                  </td>
                </tr>
              </tbody>
            </table>
            <p class="hint">
              A file with the same name replaces the old one. Fonts: woff2, woff, ttf, otf. Images: png, jpg,
              gif, webp, avif, svg, ico. At most 1 MB per file, 20 files and 5 MB in total.
            </p>
          </div>

          <div v-show="tab === 'reference'" class="tab-body reference">
            <h3>Template data</h3>
            <table>
              <tbody>
                <tr v-for="v in VARIABLES" :key="v.name">
                  <td><code>{{ v.name }}</code></td>
                  <td>{{ v.text }}</td>
                </tr>
              </tbody>
            </table>
            <h3>CSS variables of the base styles</h3>
            <p><code v-for="v in CSS_VARIABLES" :key="v" class="var">{{ v }}</code></p>
            <h3>Rules</h3>
            <ul>
              <li>No scripts. The page runs in a sandbox, and the server removes scripts, event handlers, forms and frames.</li>
              <li>Images and fonts load from your uploaded files (<code>asset:name</code>), <code>https:</code> URLs or <code>data:</code> URLs. A logo: <code>--logo: url(asset:logo.svg); --logo-width: 8rem;</code></li>
              <li>Prefer uploaded files: an <code>https:</code> URL of another host (a font CDN, for example) sends the IP address of each visitor to that host.</li>
              <li>Links can go to <code>https:</code>, <code>http:</code> and <code>mailto:</code> addresses.</li>
              <li>The CSS cannot contain the character <code>&lt;</code>. Template and CSS: at most 64 KB each.</li>
              <li>The page reloads itself every 60 seconds. Times are in UTC.</li>
            </ul>
          </div>
        </section>

        <section class="preview-pane" aria-label="Preview">
          <div class="preview-bar">
            <span>Live preview with your real data</span>
            <div class="device-switch">
              <button type="button" :class="{ active: device === 'desktop' }" @click="device = 'desktop'">Desktop</button>
              <button type="button" :class="{ active: device === 'mobile' }" @click="device = 'mobile'">Mobile</button>
            </div>
          </div>
          <div v-if="previewError" class="error-message">{{ previewError }}</div>
          <div v-if="removed.length" class="warning-message">
            Removed for safety: {{ removed.join(', ') }}
          </div>
          <div v-if="unknownAssets.length" class="warning-message">
            No such file: {{ unknownAssets.map(name => `asset:${name}`).join(', ') }}. Upload it in the Files tab.
          </div>
          <div class="preview-stage" :class="device">
            <!-- No allow-* flags: no scripts, opaque origin -->
            <iframe class="preview" :srcdoc="previewHtml" sandbox="" title="Preview of the status page"></iframe>
          </div>
        </section>
      </div>

      <div class="form-actions">
        <button
          v-if="isCustom"
          type="button"
          class="btn btn-danger-outline"
          :disabled="isSaving"
          @click="resetToDefault"
        >
          Reset to default look
        </button>
        <span class="spacer"></span>
        <span v-if="isDirty" class="unsaved">Unsaved changes</span>
        <button type="button" class="btn btn-secondary" :disabled="isSaving" @click="emit('close')">Close</button>
        <button
          type="button"
          class="btn btn-primary"
          :disabled="isSaving || !isDirty || !!previewError"
          title="Ctrl+S / ⌘S"
          @click="save"
        >
          {{ isSaving ? 'Saving…' : 'Save' }}
        </button>
      </div>
    </div>
  </FormModal>
</template>

<style scoped>
/* Fills the wide modal: the panes take the free height, the links and buttons keep theirs. */
.editor {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.links,
.form-actions {
  flex: none;
}

.links {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem 1rem;
  font-size: 0.9rem;
  color: #495057;
}

.badge {
  margin-left: auto;
  padding: 0.2rem 0.5rem;
  border-radius: 4px;
  font-size: 0.8rem;
  background: #e9ecef;
  color: #495057;
}

.badge.custom {
  background: #d1ecf1;
  color: #0c5460;
}

.panes {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.2fr);
  gap: 1rem;
}

/* Narrow windows: the panes stack and scroll, each with a usable height. */
@media (max-width: 900px) {
  .panes {
    grid-template-columns: 1fr;
    grid-auto-rows: minmax(22rem, auto);
    overflow-y: auto;
  }
}

.code-pane,
.preview-pane {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.tabs {
  display: flex;
  gap: 0.25rem;
  border-bottom: 1px solid #dee2e6;
}

.tabs button {
  padding: 0.5rem 0.9rem;
  border: 1px solid transparent;
  border-bottom: none;
  border-radius: 4px 4px 0 0;
  background: none;
  cursor: pointer;
  color: #495057;
  font-size: 0.9rem;
}

.tabs button.active {
  border-color: #dee2e6;
  background: white;
  margin-bottom: -1px;
  font-weight: 600;
  color: #2c3e50;
}

.tab-body {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  padding-top: 0.75rem;
}

.hint {
  margin: 0 0 0.5rem;
  font-size: 0.85rem;
  color: #6c757d;
}

.link-btn {
  border: none;
  background: none;
  padding: 0;
  color: #007bff;
  cursor: pointer;
  font-size: inherit;
  text-decoration: underline;
}

.code {
  flex: 1;
  min-height: 8rem;
  width: 100%;
  padding: 0.75rem;
  border: 1px solid #dee2e6;
  border-radius: 4px;
  font-family: ui-monospace, 'SF Mono', Menlo, Consolas, monospace;
  font-size: 0.85rem;
  line-height: 1.5;
  tab-size: 2;
  resize: none;
  background: #fbfcfd;
  color: #212529;
}

.code:focus {
  outline: none;
  border-color: #007bff;
  box-shadow: 0 0 0 2px rgba(0, 123, 255, 0.25);
}

.files {
  font-size: 0.85rem;
  overflow-y: auto;
  gap: 0.5rem;
}

.upload {
  position: relative;
  align-self: flex-start;
  overflow: hidden;
}

.upload input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}

.upload.disabled {
  opacity: 0.6;
}

.upload:focus-within {
  box-shadow: 0 0 0 2px rgba(0, 123, 255, 0.25);
}

.asset-list {
  border-collapse: collapse;
  width: 100%;
}

.asset-list td {
  padding: 0.35rem 0.5rem 0.35rem 0;
  border-bottom: 1px solid #f1f3f5;
  word-break: break-all;
}

.asset-list .size {
  color: #6c757d;
  white-space: nowrap;
}

.asset-list .actions {
  white-space: nowrap;
  text-align: right;
}

.asset-list .actions .link-btn + .link-btn {
  margin-left: 0.75rem;
}

.link-btn.danger {
  color: #dc3545;
}

.reference {
  font-size: 0.85rem;
  overflow-y: auto;
}

.reference h3 {
  margin: 0.5rem 0;
  font-size: 0.95rem;
  color: #2c3e50;
}

.reference table {
  border-collapse: collapse;
  width: 100%;
}

.reference td {
  padding: 0.3rem 0.5rem 0.3rem 0;
  vertical-align: top;
  border-bottom: 1px solid #f1f3f5;
}

.reference td:first-child {
  white-space: pre;
}

.reference .var {
  display: inline-block;
  margin: 0 0.4rem 0.4rem 0;
}

.reference ul {
  padding-left: 1.2rem;
  margin: 0;
}

.preview-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  padding: 0.5rem 0;
  font-size: 0.85rem;
  color: #6c757d;
}

.device-switch {
  display: flex;
  border: 1px solid #dee2e6;
  border-radius: 4px;
  overflow: hidden;
}

.device-switch button {
  padding: 0.25rem 0.6rem;
  border: none;
  background: white;
  cursor: pointer;
  font-size: 0.8rem;
  color: #495057;
}

.device-switch button.active {
  background: #007bff;
  color: white;
}

.preview-stage {
  flex: 1;
  display: flex;
  justify-content: center;
  min-height: 8rem;
  padding: 0.75rem;
  background: repeating-conic-gradient(#f1f3f5 0% 25%, #fff 0% 50%) 50% / 16px 16px;
  border: 1px solid #dee2e6;
  border-radius: 4px;
}

.preview {
  width: 100%;
  height: 100%;
  border: 1px solid #ced4da;
  border-radius: 4px;
  background: white;
  transition: width 0.2s;
}

.preview-stage.mobile .preview {
  width: 390px;
  max-width: 100%;
}

.error-message,
.warning-message {
  padding: 0.6rem 0.75rem;
  border-radius: 4px;
  margin-bottom: 0.5rem;
  font-size: 0.85rem;
}

.error-message {
  background: #f8d7da;
  color: #721c24;
  border: 1px solid #f5c6cb;
}

.warning-message {
  background: #fff3cd;
  color: #856404;
  border: 1px solid #ffeeba;
}

.form-actions {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.spacer {
  flex: 1;
}

.unsaved {
  font-size: 0.85rem;
  color: #b45309;
}

.btn {
  padding: 0.6rem 1.2rem;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.95rem;
  font-weight: 500;
}

.btn-sm {
  padding: 0.25rem 0.6rem;
  font-size: 0.8rem;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-primary {
  background: #007bff;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: #0056b3;
}

.btn-secondary {
  background: #6c757d;
  color: white;
}

.btn-outline {
  background: transparent;
  color: #007bff;
  border: 1px solid #007bff;
}

.btn-danger-outline {
  background: transparent;
  color: #dc3545;
  border: 1px solid #dc3545;
}
</style>
