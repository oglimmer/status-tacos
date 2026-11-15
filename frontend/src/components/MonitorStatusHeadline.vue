<script setup lang="ts">
// The title of the monitors page is the state of the monitors.
// All up: one quiet line. A monitor down: large and red, with the names of the monitors that are down.
import { computed } from 'vue'

const props = defineProps<{
  total: number
  up: number
  paused: number
  // The names of the monitors that are down now
  downNames: string[]
}>()

// More names than this make the line too long; the rest is "and N more".
const MAX_NAMES = 3

const down = computed(() => props.downNames.length)
const mood = computed(() => (down.value > 0 ? 'down' : props.up === props.total ? 'up' : 'mixed'))

const downNamesText = computed(() => {
  const names = props.downNames.slice(0, MAX_NAMES)
  const rest = props.downNames.length - names.length
  return rest > 0 ? `${names.join(', ')} and ${rest} more` : names.join(', ')
})

const plural = (n: number, word: string) => (n === 1 ? word : `${word}s`)
</script>

<template>
  <div class="status-headline" :class="`is-${mood}`">
    <h1 class="status-headline-title">
      <template v-if="mood === 'down'">
        <span class="status-headline-count num">{{ down }}</span>
        {{ plural(down, 'monitor') }} down
      </template>
      <template v-else-if="mood === 'up'">
        All <span class="num">{{ total }}</span> {{ plural(total, 'monitor') }} up
      </template>
      <template v-else>
        <span class="num">{{ up }}</span> of <span class="num">{{ total }}</span> monitors up
      </template>
    </h1>
    <p v-if="mood === 'down'" class="status-headline-detail">
      <span class="status-headline-names">{{ downNamesText }}</span>
      <span class="status-headline-rest"><span class="num">{{ up }}</span> of <span class="num">{{ total }}</span> up</span>
    </p>
    <p v-else-if="paused > 0" class="status-headline-detail">
      <span class="num">{{ paused }}</span> paused
    </p>
  </div>
</template>

<style scoped>
.status-headline {
  min-width: 0;
}

.status-headline-title {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0 0.3em;
  margin: 0;
  font-size: var(--text-xl);
  font-weight: 700;
  line-height: var(--leading-tight);
  color: var(--text);
}

/* All up is good news: a green dot in front, no more. */
.is-up .status-headline-title::before {
  content: '';
  align-self: center;
  width: 0.5em;
  height: 0.5em;
  margin-right: 0.1em;
  border-radius: 50%;
  background: var(--up);
}

/* A monitor down is the most important fact on the page: the largest text, in red. */
.is-down .status-headline-title {
  font-size: clamp(var(--text-2xl), 5vw, 2.5rem);
  color: var(--down);
}

.status-headline-count {
  font-size: 1.15em;
}

.status-headline-detail {
  display: flex;
  flex-wrap: wrap;
  gap: 0 var(--space-3);
  margin: var(--space-1) 0 0;
  font-size: var(--text-md);
  color: var(--text-muted);
}

.status-headline-names {
  min-width: 0;
  font-weight: 700;
  color: var(--danger-soft-text);
  overflow-wrap: anywhere;
}
</style>
