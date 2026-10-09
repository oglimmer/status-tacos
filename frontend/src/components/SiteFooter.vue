<template>
  <footer class="foot">
    <router-link to="/" class="tacos-brand brand-small">
      <img src="../assets/logo.png" alt="" />
      Status Tacos
    </router-link>
    <span class="foot-line">Checks served fresh every 15 seconds.</span>
    <nav class="foot-nav" aria-label="More">
      <a href="https://github.com/oglimmer/status-tacos">GitHub</a>
      <router-link to="/privacy">Privacy</router-link>
      <router-link to="/terms">Terms</router-link>
      <router-link to="/imprint">Imprint</router-link>
    </nav>
  </footer>
</template>

<style scoped>
.foot {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  width: min(72rem, 100% - 2 * var(--gutter));
  margin: 0 auto;
  padding: 1.5rem 0 2.5rem;
  border-top: var(--line);
}

.foot-line {
  flex: 1;
  color: var(--ink-soft);
  font-size: 1rem;
}

.brand-small {
  font-size: 0.9rem;
}

.brand-small img {
  width: 2rem;
  height: 2rem;
}

.foot-nav {
  display: flex;
  gap: 1.5rem;
  font-size: 1rem;
}

.foot-nav a {
  color: var(--ink);
  text-underline-offset: 0.2em;
}

/* The page you are on is not a link you need. */
.foot-nav a.router-link-exact-active {
  font-weight: 700;
  text-decoration: none;
}

@media (max-width: 720px) {
  .foot-line {
    flex-basis: 100%;
    order: 1;
  }

  .foot-nav {
    order: 2;
  }
}
</style>
