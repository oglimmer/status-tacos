<script setup lang="ts">
import { computed, ref } from 'vue'
import '../assets/brand.css'
import SiteFooter from '../components/SiteFooter.vue'
import { useAuthStore } from '../stores/auth'
import { useRoute, useRouter } from 'vue-router'

const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()

// Set by the account deletion: "all" or "data" (the login account at the provider still exists).
const accountDeleted = route.query.accountDeleted as string | undefined

const handleLogin = () => {
  authStore.login()
}

const goToMonitors = () => {
  router.push('/monitors')
}

// The demo strip: 12 minutes of checks, one every 15 seconds, with a one-minute outage.
// The default alert threshold is 30 seconds, so the alert goes out on the third failed check.
const CHECKS = 48
const OUTAGE_START = 30
const OUTAGE_END = 34
const ALERT_AT = OUTAGE_START + 2

const clock = (i: number) => {
  const seconds = 12 * 3600 + i * 15
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = seconds % 60
  return [h, m, s].map((n) => String(n).padStart(2, '0')).join(':')
}

// Response times look plausible but are fixed, so the strip is the same on every load.
const checks = Array.from({ length: CHECKS }, (_, i) => {
  const down = i >= OUTAGE_START && i < OUTAGE_END
  const ms = 38 + ((i * 37) % 23) + (i % 7 === 3 ? 31 : 0)
  return {
    i,
    down,
    time: clock(i),
    result: down ? 'Down, status 503' : `Up, ${ms} ms`,
    height: down ? 100 : Math.round((ms / 92) * 100),
  }
})

// The header reads out one check: the latest, or the one under the pointer or keyboard.
const picked = ref<number | null>(null)
const shown = computed(() => checks[picked.value ?? CHECKS - 1]!)

const pickAt = (event: PointerEvent) => {
  const box = (event.currentTarget as HTMLElement).getBoundingClientRect()
  const i = Math.floor(((event.clientX - box.left) / box.width) * CHECKS)
  picked.value = Math.min(CHECKS - 1, Math.max(0, i))
}

const step = (by: number) => {
  picked.value = Math.min(CHECKS - 1, Math.max(0, (picked.value ?? CHECKS - 1) + by))
}

const pos = (i: number) => `${((i + 0.5) / CHECKS) * 100}%`

const events = [
  { i: OUTAGE_START, time: clock(OUTAGE_START), text: 'Status 503. The check fails.' },
  { i: ALERT_AT, time: clock(ALERT_AT), text: 'Down for 30 seconds. Alert sent.' },
  { i: OUTAGE_END, time: clock(OUTAGE_END), text: 'Answers again. All-clear sent.' },
]
</script>

<template>
  <main class="home tacos-page">
    <section class="hero">
      <header class="bar">
        <span class="tacos-brand">
          <img src="../assets/logo.png" alt="" />
          Status Tacos
        </span>
        <button
          v-if="!authStore.isAuthenticated"
          class="btn btn-quiet"
          :disabled="authStore.isLoading"
          @click="handleLogin"
        >
          {{ authStore.isLoading ? 'Signing in…' : 'Sign in' }}
        </button>
        <button v-else class="btn btn-quiet" @click="goToMonitors">Open my monitors</button>
      </header>

      <div class="hero-text">
        <h1 class="hero-title">Is it up?</h1>
        <p class="lead">
          Status Tacos calls your URLs every 15&nbsp;seconds. When one stops answering, you hear
          about it: by email, webhook, Microsoft Teams or on your iPhone.
        </p>

        <div v-if="!authStore.isAuthenticated" class="hero-action">
          <button class="btn btn-main" :disabled="authStore.isLoading" @click="handleLogin">
            {{ authStore.isLoading ? 'Signing in…' : 'Sign in' }}
          </button>
          <span class="hint">New here? Signing in creates your account.</span>
        </div>
        <div v-else class="hero-action">
          <button class="btn btn-main" @click="goToMonitors">Open my monitors</button>
          <span class="hint">Hi, {{ authStore.user?.profile?.name || 'welcome back' }}.</span>
        </div>

        <p v-if="accountDeleted" class="notice" role="status">
          <template v-if="accountDeleted === 'all'">Your account and all its data are deleted.</template>
          <template v-else>
            Your Status Tacos data is deleted. Your login account at the identity provider still
            exists. Delete it there.
          </template>
        </p>
        <p v-if="authStore.error" class="notice notice-error" role="alert">
          Sign-in failed: {{ authStore.error }}
        </p>
      </div>
    </section>

    <figure class="strip" aria-labelledby="strip-caption">
      <img src="../assets/taco.webp" alt="" class="taco" width="640" height="441" />
      <div class="strip-head">
        <span class="strip-url">example.com/health</span>
        <span class="strip-state" :class="{ 'strip-state-down': shown.down }">
          <time class="strip-time">{{ shown.time }}</time>
          {{ shown.result }}
        </span>
      </div>

      <div
        class="ticks"
        :class="{ 'ticks-picking': picked !== null }"
        role="slider"
        tabindex="0"
        aria-label="Checks of the last 12 minutes"
        aria-valuemin="1"
        :aria-valuemax="CHECKS"
        :aria-valuenow="shown.i + 1"
        :aria-valuetext="`${shown.time}, ${shown.result}`"
        @pointermove="pickAt"
        @pointerdown="pickAt"
        @pointerleave="picked = null"
        @blur="picked = null"
        @keydown.left.prevent="step(-1)"
        @keydown.right.prevent="step(1)"
        @keydown.home.prevent="picked = 0"
        @keydown.end.prevent="picked = CHECKS - 1"
      >
        <span
          v-for="c in checks"
          :key="c.i"
          class="tick"
          :class="{ 'tick-down': c.down, 'tick-picked': c.i === picked }"
          :style="{ '--i': c.i, height: c.height + '%' }"
        ></span>
      </div>

      <ol class="events">
        <li
          v-for="(e, row) in events"
          :key="e.i"
          class="event"
          :style="{ '--row': row, '--i': e.i, right: `calc(100% - ${pos(e.i)})` }"
        >
          <time class="event-time">{{ e.time }}</time> {{ e.text }}
        </li>
      </ol>

      <figcaption id="strip-caption" class="strip-caption">
        Each bar is one check. Its height is the response time. Point at a bar to read it.
      </figcaption>
    </figure>

    <div class="body">
      <section class="part" aria-labelledby="up-title">
        <h2 id="up-title">What counts as up</h2>
        <div class="part-content">
          <p>A check sends a request to your URL and reads the answer. You choose which rule decides.</p>
          <dl class="rules">
            <div class="rule">
              <dt>
                <code class="snippet">HTTP/1.1 <mark>200</mark> OK</code>
                Status code
              </dt>
              <dd>Any 2xx or 3xx answer is up. Need something else? Write your own pattern.</dd>
            </div>
            <div class="rule">
              <dt>
                <code class="snippet">{"db":<mark>"ok"</mark>,"queue":"ok"}</code>
                Body text
              </dt>
              <dd>The answer must contain text you choose. A regular expression works too.</dd>
            </div>
            <div class="rule">
              <dt>
                <code class="snippet">queue_depth <mark>12</mark></code>
                Prometheus metric
              </dt>
              <dd>Point the check at a /metrics page. One value must stay between a minimum and a maximum.</dd>
            </div>
          </dl>
          <p class="aside">Your URL needs a token? Add your own request headers to the check.</p>
        </div>
      </section>

      <section class="part" aria-labelledby="alert-title">
        <h2 id="alert-title">Who hears about it</h2>
        <div class="part-content">
          <p>Pick from the menu. Take as many as you like.</p>
          <ul class="menu">
            <li><span class="menu-item">Email</span><span class="menu-detail">to any address</span></li>
            <li><span class="menu-item">Webhook</span><span class="menu-detail">a request to your URL</span></li>
            <li><span class="menu-item">Microsoft Teams</span><span class="menu-detail">a message in your channel</span></li>
            <li><span class="menu-item">iPhone and iPad</span><span class="menu-detail">a push alert, also in Focus</span></li>
          </ul>
          <p>
            One slow answer does not wake anybody up. You choose how long a URL must fail before
            the alert goes out: 30&nbsp;seconds by default, 15 at the least. When the URL answers
            again, you get the all-clear.
          </p>
          <p>
            Planned maintenance? Make the monitor silent. The checks go on, the alerts stop.
          </p>
        </div>
      </section>

      <section class="part" aria-labelledby="team-title">
        <h2 id="team-title">Watch it together</h2>
        <div class="part-content">
          <p>
            Put monitors into tenants, for example one per customer or per team. Add people by
            their email address. They see the same monitors and the same history.
          </p>
          <p>
            For each URL you see uptime, response times and every outage of the last 24&nbsp;hours,
            7&nbsp;days or 90&nbsp;days. The iPhone app shows the same, and you can silence a
            monitor from it.
          </p>
        </div>
      </section>

      <section class="part part-own" aria-labelledby="own-title">
        <h2 id="own-title">Run your own</h2>
        <div class="part-content">
          <p>
            The code is on GitHub. Run Status Tacos on your server with Docker Compose or the Helm
            chart.
          </p>
          <p class="links">
            <a class="link" href="https://github.com/oglimmer/status-tacos">See the code on GitHub</a>
            <button
              v-if="!authStore.isAuthenticated"
              class="btn btn-main"
              :disabled="authStore.isLoading"
              @click="handleLogin"
            >
              Or sign in and use this one
            </button>
          </p>
        </div>
      </section>
    </div>

    <SiteFooter />
  </main>
</template>

<style scoped>
/* Hero: the teal band of the logo, with the headline painted on it like a shop sign. */
.hero {
  background: var(--teal);
  padding: 1rem var(--gutter) 7.5rem;
}

/* On small screens the taco needs its own room above the strip. */
@media (max-width: 720px) {
  .hero {
    padding-bottom: calc(5rem + 7rem);
  }
}

.bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  max-width: 72rem;
  margin: 0 auto;
}

.hero-text {
  max-width: 72rem;
  margin: 0 auto;
  padding-top: clamp(3rem, 9vw, 6.5rem);
}

.hero-title {
  font-family: var(--sign);
  font-weight: 400;
  font-size: clamp(3.5rem, 13vw, 10rem);
  line-height: 0.9;
  letter-spacing: -0.01em;
  margin: 0 0 2rem;
  /* A painted drop shade, like the lettering on a taqueria window. */
  text-shadow: 0.06em 0.06em 0 var(--masa);
}

.lead {
  max-width: 34rem;
  font-size: clamp(1.2rem, 2.2vw, 1.45rem);
  line-height: 1.45;
  margin: 0 0 2rem;
}

.hero-action {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem 1.25rem;
}

.hint {
  font-size: 1rem;
}

.notice {
  max-width: 34rem;
  margin: 1.5rem 0 0;
  padding: 0.75rem 1rem;
  border: var(--line);
  border-radius: 0.6rem;
  background: var(--paper);
  font-size: 1rem;
}

.notice-error {
  border-color: var(--salsa);
  color: #8f2615;
}

/* Buttons */
.btn {
  font: inherit;
  font-weight: 700;
  font-size: 1rem;
  color: var(--ink);
  border: var(--line);
  border-radius: 999px;
  padding: 0.6rem 1.3rem;
  cursor: pointer;
}

.btn-main {
  background: var(--masa);
  padding: 0.85rem 1.8rem;
  font-size: 1.1rem;
}

.btn-main:hover:not(:disabled) {
  background: #f6c665;
}

.btn-quiet {
  background: transparent;
}

.btn-quiet:hover:not(:disabled) {
  background: rgb(255 255 255 / 0.2);
}

.btn:disabled {
  opacity: 0.6;
  cursor: progress;
}

/* The strip: twelve minutes of checks. It is the one thing on the page that moves. */
/* The taco from the logo stands on top of it, with its HTTP sign. Same teal, so it sits in the band. */
.taco {
  --taco-width: clamp(8rem, 20vw, 17rem);
  position: absolute;
  bottom: calc(100% - 2px);
  right: clamp(1rem, 4vw, 3rem);
  width: var(--taco-width);
  height: auto;
  pointer-events: none;
}

.strip {
  position: relative;
  width: min(72rem, 100% - 2 * var(--gutter));
  margin: -5rem auto 0;
  padding: 1.25rem clamp(1rem, 3vw, 2rem) 1.25rem;
  background: var(--paper);
  border: var(--line);
  border-radius: 1rem;
}

.strip-head {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 0.25rem 1rem;
  margin-bottom: 1rem;
  font-weight: 700;
}

.strip-url {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.strip-state {
  flex: none;
  color: var(--cilantro);
  font-variant-numeric: tabular-nums;
}

.strip-state-down {
  color: var(--salsa);
}

.strip-time {
  color: var(--ink-soft);
  font-weight: 400;
  margin-right: 0.4rem;
}

.strip-state::before {
  content: '';
  display: inline-block;
  width: 0.6rem;
  height: 0.6rem;
  margin-right: 0.45rem;
  border-radius: 50%;
  background: currentColor;
}

.ticks {
  display: flex;
  align-items: flex-end;
  gap: clamp(1px, 0.35vw, 4px);
  height: clamp(4rem, 10vw, 7rem);
  cursor: crosshair;
  touch-action: pan-y;
}

.ticks:focus-visible {
  outline: 3px solid var(--ink);
  outline-offset: 6px;
  border-radius: 2px;
}

.ticks-picking .tick:not(.tick-picked) {
  opacity: 0.45;
}

.tick {
  flex: 1;
  min-height: 30%;
  background: var(--cilantro);
  border-radius: 2px 2px 0 0;
  transform-origin: bottom;
  animation: tick-in 260ms cubic-bezier(0.2, 0.8, 0.3, 1.2) both;
  animation-delay: calc(var(--i) * 32ms);
  transition: opacity 120ms;
}

.tick-down {
  background: var(--salsa);
}

.events {
  --row-height: 1.9rem;
  position: relative;
  height: calc(3 * var(--row-height) + 0.5rem);
  margin: 0;
  padding: 0;
  list-style: none;
  border-top: var(--line);
  font-size: 0.95rem;
}

/* Each note hangs from its check. The right border is the leader line up to the strip. */
.event {
  position: absolute;
  top: 0;
  height: calc((var(--row) + 1) * var(--row-height));
  display: flex;
  align-items: flex-end;
  padding-right: 0.5rem;
  border-right: var(--line);
  white-space: nowrap;
  line-height: 1.2;
  animation: note-in 300ms ease-out both;
  animation-delay: calc(var(--i) * 32ms + 200ms);
}

.event-time {
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  margin-right: 0.4rem;
}

.strip-caption {
  margin-top: 0.75rem;
  font-size: 0.95rem;
  color: var(--ink-soft);
}

@keyframes tick-in {
  from {
    transform: scaleY(0);
  }
}

@keyframes note-in {
  from {
    opacity: 0;
  }
}

/* Body: heading left, text right, one column on small screens. */
.body {
  width: min(72rem, 100% - 2 * var(--gutter));
  margin: 0 auto;
  padding: clamp(3rem, 8vw, 6rem) 0 2rem;
}

.part {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 2fr);
  gap: 1rem 3rem;
  padding-bottom: clamp(3rem, 7vw, 5rem);
}

.part h2 {
  position: sticky;
  top: 2rem;
  align-self: start;
  margin: 0;
  font-size: clamp(1.6rem, 3vw, 2.1rem);
  line-height: 1.15;
  font-weight: 800;
  letter-spacing: -0.01em;
}

.part-content {
  max-width: var(--measure);
}

.part-content > p {
  margin: 0 0 1rem;
}

.aside {
  color: var(--ink-soft);
}

.rules {
  margin: 1.5rem 0;
}

.rule {
  padding: 1rem 0;
  border-top: 1px solid rgb(16 46 42 / 0.25);
}

.rule:last-child {
  border-bottom: 1px solid rgb(16 46 42 / 0.25);
}

.rule dt {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.5rem;
  font-weight: 700;
}

.rule dd {
  margin: 0.25rem 0 0;
}

/* The part of the answer the rule reads. */
.snippet mark {
  background: none;
  color: var(--masa);
  font-weight: 700;
}

.snippet {
  font-family: var(--mono);
  font-size: 0.9rem;
  font-weight: 400;
  padding: 0.2rem 0.55rem;
  background: var(--ink);
  color: var(--agua);
  border-radius: 0.35rem;
}

/* Alert channels as a menu board, with dotted leaders. */
.menu {
  margin: 0 0 1.5rem;
  padding: 0;
  list-style: none;
}

.menu li {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
  padding: 0.3rem 0;
}

.menu li::after {
  content: '';
  order: 1;
  flex: 1;
  min-width: 1.5rem;
  border-bottom: 2px dotted var(--ink-soft);
}

.menu-item {
  font-weight: 700;
}

.menu-detail {
  order: 2;
  text-align: right;
}

.part-own .links {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 1rem 1.5rem;
  margin-top: 1.5rem;
}

.link {
  color: var(--ink);
  font-weight: 700;
  text-decoration-thickness: 2px;
  text-underline-offset: 0.2em;
}

.link:hover {
  text-decoration-color: var(--teal);
}

@media (max-width: 720px) {
  .part {
    grid-template-columns: 1fr;
  }

  .part h2 {
    position: static;
  }

  /* Not enough room for the hanging notes: list them under the strip. */
  .events {
    height: auto;
    padding-top: 0.5rem;
    font-size: 0.9rem;
  }

  .event {
    position: static;
    height: auto;
    padding: 0.15rem 0;
    border-right: 0;
    white-space: normal;
  }

  .menu li {
    flex-direction: column;
    gap: 0;
  }

  .menu li::after {
    display: none;
  }

  .menu-detail {
    text-align: left;
  }
}

@media (prefers-reduced-motion: reduce) {
  .tick,
  .event {
    animation: none;
    transition: none;
  }
}
</style>
