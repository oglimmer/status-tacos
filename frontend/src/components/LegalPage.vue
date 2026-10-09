<script setup lang="ts">
defineProps<{
  badgeIcon: string
  badge: string
  title: string
  titleHighlight: string
  description: string
  lastUpdated?: string
}>()

const year = new Date().getFullYear()
</script>

<template>
  <main class="legal-page">
    <!-- Navigation Header -->
    <header class="nav-header">
      <router-link to="/" class="nav-brand">
        <img src="../assets/logo.png" alt="Status Tacos" class="nav-logo" />
        <span class="brand-name">Status Tacos</span>
      </router-link>
      <nav class="nav-links">
        <router-link to="/" class="nav-link">Home</router-link>
        <router-link to="/privacy" class="nav-link">Privacy</router-link>
        <router-link to="/terms" class="nav-link">Terms</router-link>
        <router-link to="/imprint" class="nav-link">Imprint</router-link>
      </nav>
    </header>

    <!-- Hero Section -->
    <section class="hero">
      <div class="hero-badge">
        <span class="badge-icon">{{ badgeIcon }}</span>
        <span>{{ badge }}</span>
      </div>
      <h1 class="hero-title">
        {{ title }} <span class="gradient-text">{{ titleHighlight }}</span>
      </h1>
      <p class="hero-description">{{ description }}</p>
      <div v-if="lastUpdated" class="last-updated">
        <span>🕒</span>
        <span>Last updated: {{ lastUpdated }}</span>
      </div>
    </section>

    <!-- Content -->
    <section class="content-section">
      <article class="legal-content">
        <slot />
      </article>
    </section>

    <!-- Footer -->
    <footer class="footer">
      <div class="footer-content">
        <div class="footer-brand">
          <img src="../assets/logo.png" alt="Status Tacos" class="footer-logo" />
          <span class="brand-name">Status Tacos</span>
        </div>
        <nav class="footer-nav">
          <router-link to="/" class="footer-link">Home</router-link>
          <router-link to="/privacy" class="footer-link">Privacy Policy</router-link>
          <router-link to="/terms" class="footer-link">Terms of Service</router-link>
          <router-link to="/imprint" class="footer-link">Imprint</router-link>
        </nav>
        <div class="footer-tagline">© {{ year }} Status Tacos · Made with ❤️ and a lot of 🌮</div>
      </div>
    </footer>
  </main>
</template>

<!-- Not scoped: the styles must reach the slotted legal text. All rules sit under .legal-page. -->
<style>
.legal-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  background-attachment: fixed;
}

/* Navigation Header */
.legal-page .nav-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 2rem;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.2);
  position: sticky;
  top: 0;
  z-index: 100;
}

.legal-page .nav-brand {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  text-decoration: none;
}

.legal-page .nav-logo {
  height: 40px;
  width: auto;
}

.legal-page .brand-name {
  font-size: 1.5rem;
  font-weight: 700;
  color: #2c3e50;
}

.legal-page .nav-links {
  display: flex;
  align-items: center;
  gap: 1.5rem;
}

.legal-page .nav-link {
  color: #6c757d;
  text-decoration: none;
  font-weight: 500;
  transition: color 0.2s;
}

.legal-page .nav-link:hover,
.legal-page .nav-link.router-link-exact-active {
  color: #007bff;
}

/* Hero Section */
.legal-page .hero {
  max-width: 860px;
  margin: 0 auto;
  padding: 5rem 2rem 4rem;
  color: white;
}

.legal-page .hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(10px);
  padding: 0.5rem 1rem;
  border-radius: 50px;
  font-size: 0.875rem;
  font-weight: 500;
  margin-bottom: 2rem;
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.legal-page .badge-icon {
  font-size: 1.2rem;
}

.legal-page .hero-title {
  font-size: 3rem;
  font-weight: 800;
  line-height: 1.1;
  margin: 0 0 1.5rem;
  color: white;
}

.legal-page .gradient-text {
  background: linear-gradient(45deg, #ff6b6b, #feca57);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.legal-page .hero-description {
  font-size: 1.2rem;
  line-height: 1.6;
  margin: 0 0 1.5rem;
  color: rgba(255, 255, 255, 0.9);
  max-width: 640px;
}

.legal-page .last-updated {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.9rem;
  color: rgba(255, 255, 255, 0.8);
}

/* Content */
.legal-page .content-section {
  background: white;
  padding: 4rem 2rem 5rem;
}

.legal-page .legal-content {
  max-width: 860px;
  margin: 0 auto;
  color: #495057;
  font-size: 1rem;
  line-height: 1.7;
}

.legal-page .legal-content h2 {
  font-size: 1.5rem;
  font-weight: 700;
  color: #2c3e50;
  margin: 3rem 0 1rem;
  padding-bottom: 0.5rem;
  border-bottom: 2px solid #e9ecef;
}

.legal-page .legal-content h2:first-child {
  margin-top: 0;
}

.legal-page .legal-content h3 {
  font-size: 1.15rem;
  font-weight: 600;
  color: #2c3e50;
  margin: 1.5rem 0 0.5rem;
}

.legal-page .legal-content p {
  margin: 0 0 1rem;
}

.legal-page .legal-content ul {
  margin: 0 0 1rem;
  padding-left: 1.5rem;
}

.legal-page .legal-content li {
  margin-bottom: 0.5rem;
}

.legal-page .legal-content a {
  color: #007bff;
  text-decoration: none;
  font-weight: 500;
}

.legal-page .legal-content a:hover {
  text-decoration: underline;
}

.legal-page .legal-content code {
  background: #f8f9fa;
  border: 1px solid #e9ecef;
  border-radius: 4px;
  padding: 0.1rem 0.35rem;
  font-size: 0.9em;
}

.legal-page .legal-content .intro {
  background: #f8f9fa;
  border: 1px solid #e9ecef;
  border-left: 4px solid #667eea;
  border-radius: 12px;
  padding: 1.5rem;
  margin-bottom: 2.5rem;
}

.legal-page .legal-content .info-card {
  background: #f8f9fa;
  border: 1px solid #e9ecef;
  border-radius: 12px;
  padding: 1.5rem;
  margin-bottom: 1rem;
}

.legal-page .legal-content .info-card p:last-child {
  margin-bottom: 0;
}

/* Footer */
.legal-page .footer {
  background: #2c3e50;
  padding: 3rem 2rem 2rem;
  color: white;
}

.legal-page .footer-content {
  max-width: 1200px;
  margin: 0 auto;
  text-align: center;
}

.legal-page .footer-brand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  margin-bottom: 2rem;
}

.legal-page .footer-brand .brand-name {
  color: white;
}

.legal-page .footer-logo {
  height: 40px;
  width: auto;
  border-radius: 8px;
}

.legal-page .footer-nav {
  display: flex;
  justify-content: center;
  gap: 2rem;
  margin-bottom: 2rem;
}

.legal-page .footer-link {
  color: rgba(255, 255, 255, 0.8);
  text-decoration: none;
  font-weight: 500;
  transition: color 0.2s;
}

.legal-page .footer-link:hover {
  color: #17a2b8;
}

.legal-page .footer-tagline {
  color: rgba(255, 255, 255, 0.6);
  font-size: 0.9rem;
}

/* Responsive Design */
@media (max-width: 768px) {
  .legal-page .nav-header {
    padding: 1rem;
  }

  .legal-page .nav-links {
    gap: 1rem;
  }

  .legal-page .hero {
    padding: 3rem 1rem;
  }

  .legal-page .hero-title {
    font-size: 2.25rem;
  }

  .legal-page .content-section {
    padding: 3rem 1rem 4rem;
  }

  .legal-page .footer-nav {
    flex-direction: column;
    gap: 1rem;
  }
}

@media (max-width: 480px) {
  .legal-page .nav-brand .brand-name {
    display: none;
  }

  .legal-page .hero-title {
    font-size: 1.9rem;
  }

  .legal-page .hero-description {
    font-size: 1.05rem;
  }
}
</style>
