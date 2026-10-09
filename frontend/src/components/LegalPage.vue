<script setup lang="ts">
import { onMounted, ref } from 'vue'
import '../assets/brand.css'
import SiteFooter from './SiteFooter.vue'

defineProps<{
  title: string
  description: string
  lastUpdated?: string
}>()

// The contents list is built from the h2 headings of the slotted legal text.
const article = ref<HTMLElement | null>(null)
const sections = ref<{ id: string; text: string }[]>([])

const slug = (text: string) =>
  text
    .toLowerCase()
    .replace(/^\d+\.\s*/, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '')

onMounted(() => {
  const headings = article.value?.querySelectorAll('h2') ?? []
  sections.value = Array.from(headings, (h) => {
    h.id ||= slug(h.textContent ?? '')
    return { id: h.id, text: h.textContent ?? '' }
  })
})
</script>

<template>
  <main class="legal-page tacos-page">
    <section class="legal-band">
      <header class="legal-bar">
        <router-link to="/" class="tacos-brand">
          <img src="../assets/logo.png" alt="" />
          Status Tacos
        </router-link>
        <nav class="legal-nav" aria-label="Legal">
          <router-link to="/privacy">Privacy</router-link>
          <router-link to="/terms">Terms</router-link>
          <router-link to="/imprint">Imprint</router-link>
        </nav>
      </header>

      <div class="legal-head">
        <h1 class="legal-title">{{ title }}</h1>
        <p class="legal-description">{{ description }}</p>
        <p v-if="lastUpdated" class="legal-updated">Last updated {{ lastUpdated }}</p>
      </div>
    </section>

    <div class="legal-sheet">
      <img src="../assets/taco.webp" alt="" class="legal-taco" width="640" height="441" />
      <article ref="article" class="legal-content">
        <slot />
      </article>
      <nav v-if="sections.length > 1" class="legal-toc" aria-labelledby="legal-toc-title">
        <h2 id="legal-toc-title">Contents</h2>
        <ol>
          <li v-for="s in sections" :key="s.id">
            <a :href="`#${s.id}`">{{ s.text }}</a>
          </li>
        </ol>
      </nav>
    </div>

    <SiteFooter />
  </main>
</template>

<!-- Not scoped: the styles must reach the slotted legal text. All rules sit under .legal-page. -->
<style>
.legal-page {
  --legal-width: min(72rem, 100% - 2 * var(--gutter));
}

/* The teal band of the landing page, quieter: no strip, a smaller sign. */
.legal-page .legal-band {
  background: var(--teal);
  padding: 1rem 0 6rem;
}

.legal-page .legal-bar,
.legal-page .legal-head {
  width: var(--legal-width);
  margin: 0 auto;
}

.legal-page .legal-bar {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
}

.legal-page .legal-nav {
  display: flex;
  gap: 1.25rem;
  font-size: 1rem;
}

.legal-page .legal-nav a {
  color: var(--ink);
  text-underline-offset: 0.2em;
}

.legal-page .legal-nav a.router-link-exact-active {
  font-weight: 700;
  text-decoration: none;
}

.legal-page .legal-head {
  padding-top: clamp(2.5rem, 7vw, 4.5rem);
}

.legal-page .legal-title {
  font-family: var(--sign);
  font-weight: 400;
  font-size: clamp(2.4rem, 7vw, 5rem);
  line-height: 0.95;
  margin: 0 0 1.25rem;
  text-shadow: 0.06em 0.06em 0 var(--masa);
  overflow-wrap: anywhere;
  /* Keep clear of the taco in the right corner. */
  max-width: calc(100% - clamp(7rem, 14vw, 12rem) - 2rem);
}

.legal-page .legal-description {
  max-width: 34rem;
  font-size: clamp(1.1rem, 2vw, 1.3rem);
  line-height: 1.45;
  margin: 0 0 0.75rem;
}

.legal-page .legal-updated {
  margin: 0;
  font-size: 1rem;
}

/* The text sits on a sheet that overlaps the band. The taco stands on top of it. */
.legal-page .legal-sheet {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 42rem) minmax(12rem, 16rem);
  justify-content: space-between;
  gap: 3rem;
  width: var(--legal-width);
  margin: -3.5rem auto clamp(3rem, 7vw, 5rem);
  padding: clamp(1.5rem, 4vw, 3rem);
  background: var(--paper);
  border: var(--line);
  border-radius: 1rem;
}

.legal-page .legal-taco {
  position: absolute;
  bottom: calc(100% - 2px);
  right: clamp(1rem, 4vw, 3rem);
  width: clamp(7rem, 14vw, 12rem);
  height: auto;
  pointer-events: none;
}

.legal-page .legal-toc {
  position: sticky;
  top: 2rem;
  align-self: start;
  font-size: 0.95rem;
}

.legal-page .legal-toc h2 {
  margin: 0 0 0.75rem;
  font-size: 1rem;
  font-weight: 700;
}

.legal-page .legal-toc ol {
  margin: 0;
  padding: 0;
  list-style: none;
  border-left: var(--line);
}

.legal-page .legal-toc li {
  padding: 0.25rem 0 0.25rem 0.9rem;
}

.legal-page .legal-toc a {
  color: var(--ink-soft);
  text-decoration: none;
}

.legal-page .legal-toc a:hover {
  color: var(--ink);
  text-decoration: underline;
}

/* The legal text */
.legal-page .legal-content {
  font-size: 1.05rem;
  line-height: 1.65;
}

.legal-page .legal-content h2 {
  font-size: 1.35rem;
  font-weight: 800;
  line-height: 1.25;
  margin: 2.5rem 0 0.75rem;
  padding-top: 1.5rem;
  border-top: 1px solid rgb(16 46 42 / 0.25);
  scroll-margin-top: 1.5rem;
}

.legal-page .legal-content h3 {
  font-size: 1.1rem;
  font-weight: 700;
  margin: 1.5rem 0 0.5rem;
}

.legal-page .legal-content p {
  margin: 0 0 1rem;
}

.legal-page .legal-content ul {
  margin: 0 0 1rem;
  padding-left: 1.25rem;
}

.legal-page .legal-content li {
  margin-bottom: 0.4rem;
}

.legal-page .legal-content a {
  color: var(--ink);
  font-weight: 700;
  text-decoration-thickness: 2px;
  text-underline-offset: 0.2em;
  overflow-wrap: anywhere;
}

.legal-page .legal-content a:hover {
  text-decoration-color: var(--teal);
}

.legal-page .legal-content code {
  font-family: var(--mono);
  font-size: 0.9em;
  padding: 0.1rem 0.35rem;
  background: var(--agua);
  border-radius: 0.3rem;
}

.legal-page .legal-content .intro {
  font-size: 1.2rem;
  line-height: 1.5;
  margin-bottom: 0;
}

.legal-page .legal-content .info-card {
  margin: 0 0 1rem;
  padding: 1rem 1.25rem;
  background: var(--agua);
  border: var(--line);
  border-radius: 0.6rem;
}

.legal-page .legal-content .info-card h3 {
  margin-top: 0;
}

.legal-page .legal-content .info-card p:last-child {
  margin-bottom: 0;
}

@media (max-width: 860px) {
  .legal-page .legal-sheet {
    grid-template-columns: minmax(0, 1fr);
  }

  /* The headings are one scroll away on a phone; the list would only push the text down. */
  .legal-page .legal-toc {
    display: none;
  }
}

@media (max-width: 720px) {
  .legal-page .legal-band {
    padding-bottom: calc(3.5rem + 6.5rem);
  }

  /* The taco has its own room below the title here. */
  .legal-page .legal-title {
    max-width: none;
  }
}
</style>
