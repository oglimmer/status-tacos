<script setup lang="ts">
import { } from 'vue'
import { useAuthStore } from '../stores/auth'
import { useRouter } from 'vue-router'

const authStore = useAuthStore()
const router = useRouter()
const handleLogin = () => {
  authStore.login()
}

const goToDashboard = () => {
  router.push('/monitors')
}
</script>

<template>
  <main class="home-container">
    <!-- Navigation Header -->
    <header class="nav-header">
      <div class="nav-brand">
        <img src="../assets/logo.png" alt="Status Tacos" class="nav-logo" />
        <span class="brand-name">Status Tacos</span>
      </div>
      <nav class="nav-links">
        <router-link to="/about" class="nav-link">About</router-link>
        <div v-if="!authStore.isAuthenticated" class="login-section">
          <button
            @click="handleLogin"
            :disabled="authStore.isLoading"
            class="btn btn-outline"
          >
            {{ authStore.isLoading ? 'Signing in...' : 'Sign In' }}
          </button>
        </div>
        <button
          v-else
          @click="goToDashboard"
          class="btn btn-primary"
        >
          Dashboard
        </button>
      </nav>
    </header>

    <!-- Hero Section -->
    <section class="hero">
      <div class="hero-content">
        <div class="hero-badge">
          <span class="badge-icon">🌮</span>
          <span>Deliciously Reliable Monitoring</span>
        </div>
        <h1 class="hero-title">
          Keep Your APIs Fresh & Your<br>
          <span class="gradient-text">Services Spicy</span>
        </h1>
        <p class="hero-description">
          Status Tacos serves up comprehensive endpoint monitoring with a side of reliability.
          Track uptime, monitor performance, and get notified faster than you can say "¡Olé!"
        </p>

        <div class="hero-actions">
          <div v-if="!authStore.isAuthenticated" class="hero-login-section">
            <button
              @click="handleLogin"
              :disabled="authStore.isLoading"
              class="btn btn-primary btn-large"
            >
              <span class="btn-icon">🚀</span>
              {{ authStore.isLoading ? 'Getting Started...' : 'Start Monitoring Free' }}
            </button>
          </div>
          <div v-else class="authenticated-hero">
            <p class="welcome-text">¡Hola {{ authStore.user?.profile?.name || 'Amigo' }}!</p>
            <button @click="goToDashboard" class="btn btn-primary btn-large">
              <span class="btn-icon">📊</span>
              View Your Monitors
            </button>
          </div>
        </div>

        <div v-if="authStore.error" class="error-alert">
          <span class="error-icon">⚠️</span>
          {{ authStore.error }}
        </div>
      </div>

      <div class="hero-visual">
        <div class="status-demo">
          <div class="demo-card">
            <div class="demo-header">
              <span class="status-dot status-up"></span>
              <span class="demo-url">api.example.com</span>
              <span class="demo-time">99.9% uptime</span>
            </div>
            <div class="demo-chart">
              <div class="chart-bar" style="height: 60%"></div>
              <div class="chart-bar" style="height: 80%"></div>
              <div class="chart-bar" style="height: 100%"></div>
              <div class="chart-bar" style="height: 40%"></div>
              <div class="chart-bar" style="height: 90%"></div>
              <div class="chart-bar" style="height: 70%"></div>
            </div>
          </div>
          <div class="floating-alerts">
            <div class="alert-bubble">
              <span class="alert-icon">✅</span>
              <span>API is healthy</span>
            </div>
            <div class="alert-bubble delay-1">
              <span class="alert-icon">📈</span>
              <span>Response time: 45ms</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Features Section -->
    <section class="features-section">
      <div class="section-header">
        <h2>Why Choose Status Tacos?</h2>
        <p>Everything you need to keep your services running smoothly</p>
      </div>

      <div class="features-grid">
        <div class="feature-card">
          <div class="feature-icon">⚡</div>
          <h3>Lightning Fast Checks</h3>
          <p>Monitor your endpoints every minute with sub-second response times. No more waiting around for status updates.</p>
        </div>

        <div class="feature-card">
          <div class="feature-icon">🎯</div>
          <h3>Smart Alerting</h3>
          <p>Configurable thresholds and multi-channel notifications. Get alerts via email, Slack, or webhook when it matters.</p>
        </div>

        <div class="feature-card">
          <div class="feature-icon">📊</div>
          <h3>Beautiful Analytics</h3>
          <p>Visualize your uptime trends, response times, and historical data with stunning charts and insights.</p>
        </div>

        <div class="feature-card">
          <div class="feature-icon">🔒</div>
          <h3>Enterprise Security</h3>
          <p>Multi-tenant architecture with role-based access control. Your data stays secure and organized.</p>
        </div>

        <div class="feature-card">
          <div class="feature-icon">🌍</div>
          <h3>Global Monitoring</h3>
          <p>Check your services from multiple locations worldwide. Ensure consistent performance for all users.</p>
        </div>

        <div class="feature-card">
          <div class="feature-icon">🔧</div>
          <h3>Easy Integration</h3>
          <p>RESTful API, webhooks, and integrations with your favorite tools. Set up monitoring in minutes, not hours.</p>
        </div>
      </div>
    </section>

    <!-- Stats Section -->
    <section class="stats-section">
      <div class="stats-container">
        <div class="stat-item">
          <div class="stat-number">99.99%</div>
          <div class="stat-label">Platform Uptime</div>
        </div>
        <div class="stat-item">
          <div class="stat-number" style="text-wrap: nowrap">< 100ms</div>
          <div class="stat-label">Avg Response Time</div>
        </div>
        <div class="stat-item">
          <div class="stat-number">24/7</div>
          <div class="stat-label">Monitoring Coverage</div>
        </div>
        <div class="stat-item">
          <div class="stat-number">∞</div>
          <div class="stat-label">Endpoints Supported</div>
        </div>
      </div>
    </section>

    <!-- CTA Section -->
    <section class="cta-section">
      <div class="cta-content">
        <h2>Ready to Spice Up Your Monitoring?</h2>
        <p>Join thousands of developers who trust Status Tacos to keep their services running smoothly.</p>
        <div v-if="!authStore.isAuthenticated" class="cta-login-section">
          <button
            @click="handleLogin"
            :disabled="authStore.isLoading"
            class="btn btn-primary btn-large"
          >
            <span class="btn-icon">🌮</span>
            {{ authStore.isLoading ? 'Getting Started...' : 'Start Your Free Monitoring' }}
          </button>
        </div>
        <button
          v-else
          @click="goToDashboard"
          class="btn btn-primary btn-large"
        >
          <span class="btn-icon">📊</span>
          Go to Your Dashboard
        </button>
      </div>
    </section>

    <!-- Footer -->
    <footer class="footer">
      <div class="footer-content">
        <div class="footer-brand">
          <img src="../assets/logo.png" alt="Status Tacos" class="footer-logo" />
          <span class="brand-name">Status Tacos</span>
        </div>
        <nav class="footer-nav">
          <router-link to="/about" class="footer-link">About</router-link>
          <router-link to="/imprint" class="footer-link">Imprint</router-link>
          <router-link to="/tos" class="footer-link">Terms of Service</router-link>
        </nav>
        <div class="footer-tagline">
          Made with ❤️ and a lot of 🌮
        </div>
      </div>
    </footer>
  </main>
</template>

<style scoped>
.home-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  background-attachment: fixed;
}

/* Navigation Header */
.nav-header {
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

.nav-brand {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.nav-logo {
  height: 40px;
  width: auto;
}

.brand-name {
  font-size: 1.5rem;
  font-weight: 700;
  color: #2c3e50;
}

.nav-links {
  display: flex;
  align-items: center;
  gap: 1.5rem;
}

.login-section {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.hero-login-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
}

.cta-login-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
}

.nav-link {
  color: #6c757d;
  text-decoration: none;
  font-weight: 500;
  transition: color 0.2s;
}

.nav-link:hover {
  color: #007bff;
}

/* Hero Section */
.hero {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4rem;
  align-items: center;
  padding: 6rem 2rem;
  max-width: 1200px;
  margin: 0 auto;
}

.hero-content {
  color: white;
}

.hero-badge {
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

.badge-icon {
  font-size: 1.2rem;
}

.hero-title {
  font-size: 3.5rem;
  font-weight: 800;
  line-height: 1.1;
  margin-bottom: 1.5rem;
  color: white;
}

.gradient-text {
  background: linear-gradient(45deg, #ff6b6b, #feca57);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.hero-description {
  font-size: 1.25rem;
  line-height: 1.6;
  margin-bottom: 2.5rem;
  color: rgba(255, 255, 255, 0.9);
  max-width: 500px;
}

.hero-actions {
  margin-bottom: 2rem;
}

.authenticated-hero .welcome-text {
  font-size: 1.1rem;
  margin-bottom: 1rem;
  color: rgba(255, 255, 255, 0.9);
}

.error-alert {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  background: rgba(220, 53, 69, 0.1);
  backdrop-filter: blur(10px);
  color: #ff6b6b;
  padding: 1rem;
  border-radius: 8px;
  border: 1px solid rgba(220, 53, 69, 0.2);
}

/* Hero Visual */
.hero-visual {
  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
}

.status-demo {
  position: relative;
  transform: rotate(-5deg);
  animation: float 6s ease-in-out infinite;
}

.demo-card {
  background: white;
  border-radius: 12px;
  padding: 1.5rem;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.1);
  min-width: 280px;
}

.demo-header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.status-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #28a745;
  box-shadow: 0 0 0 3px rgba(40, 167, 69, 0.2);
  animation: pulse 2s infinite;
}

.demo-url {
  font-weight: 600;
  color: #2c3e50;
  flex: 1;
}

.demo-time {
  font-size: 0.875rem;
  color: #28a745;
  font-weight: 500;
}

.demo-chart {
  display: flex;
  align-items: end;
  gap: 4px;
  height: 60px;
}

.chart-bar {
  background: linear-gradient(to top, #007bff, #17a2b8);
  width: 8px;
  border-radius: 4px;
  transition: height 0.3s ease;
  animation: chart-animate 2s ease-in-out infinite;
}

.chart-bar:nth-child(even) {
  animation-delay: 0.2s;
}

.chart-bar:nth-child(3n) {
  animation-delay: 0.4s;
}

.floating-alerts {
  position: absolute;
  top: -20px;
  right: -40px;
}

.alert-bubble {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  padding: 0.5rem 1rem;
  border-radius: 20px;
  font-size: 0.875rem;
  font-weight: 500;
  color: #2c3e50;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  margin-bottom: 0.5rem;
  animation: slide-in 0.5s ease-out;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.alert-bubble.delay-1 {
  animation-delay: 1s;
  animation-fill-mode: both;
}

/* Buttons */
.btn {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1.5rem;
  border: none;
  border-radius: 8px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  text-decoration: none;
}

.btn-primary {
  background: linear-gradient(45deg, #007bff, #0056b3);
  color: white;
  box-shadow: 0 4px 12px rgba(0, 123, 255, 0.3);
}

.btn-primary:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(0, 123, 255, 0.4);
}

.btn-outline {
  background: transparent;
  color: #007bff;
  border: 2px solid #007bff;
}

.btn-outline:hover:not(:disabled) {
  background: #007bff;
  color: white;
}

.btn-large {
  padding: 1rem 2rem;
  font-size: 1.1rem;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none !important;
}

.btn-icon {
  font-size: 1.2rem;
}

/* Features Section */
.features-section {
  background: white;
  padding: 6rem 2rem;
}

.section-header {
  text-align: center;
  max-width: 600px;
  margin: 0 auto 4rem;
}

.section-header h2 {
  font-size: 2.5rem;
  font-weight: 700;
  color: #2c3e50;
  margin-bottom: 1rem;
}

.section-header p {
  font-size: 1.1rem;
  color: #6c757d;
  line-height: 1.6;
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 2rem;
  max-width: 1200px;
  margin: 0 auto;
}

.feature-card {
  background: #f8f9fa;
  padding: 2rem;
  border-radius: 12px;
  border: 1px solid #e9ecef;
  transition: transform 0.2s, box-shadow 0.2s;
  text-align: center;
}

.feature-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.1);
}

.feature-icon {
  font-size: 3rem;
  margin-bottom: 1rem;
  display: block;
}

.feature-card h3 {
  font-size: 1.25rem;
  font-weight: 600;
  color: #2c3e50;
  margin-bottom: 1rem;
}

.feature-card p {
  color: #6c757d;
  line-height: 1.6;
  margin: 0;
}

/* Stats Section */
.stats-section {
  background: linear-gradient(135deg, #2c3e50 0%, #34495e 100%);
  padding: 4rem 2rem;
  color: white;
}

.stats-container {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 2rem;
  max-width: 1000px;
  margin: 0 auto;
  text-align: center;
}

.stat-item {
  padding: 1rem;
}

.stat-number {
  font-size: 3rem;
  font-weight: 800;
  color: #17a2b8;
  margin-bottom: 0.5rem;
}

.stat-label {
  font-size: 1.1rem;
  color: rgba(255, 255, 255, 0.8);
  font-weight: 500;
}

/* CTA Section */
.cta-section {
  background: linear-gradient(45deg, #ff6b6b, #feca57);
  padding: 6rem 2rem;
  text-align: center;
}

.cta-content {
  max-width: 600px;
  margin: 0 auto;
}

.cta-content h2 {
  font-size: 2.5rem;
  font-weight: 700;
  color: white;
  margin-bottom: 1rem;
}

.cta-content p {
  font-size: 1.1rem;
  color: rgba(255, 255, 255, 0.9);
  margin-bottom: 2rem;
  line-height: 1.6;
}

/* Footer */
.footer {
  background: #2c3e50;
  padding: 3rem 2rem 2rem;
  color: white;
}

.footer-content {
  max-width: 1200px;
  margin: 0 auto;
  text-align: center;
}

.footer-brand {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  margin-bottom: 2rem;
}

.footer-logo {
  height: 40px;
  width: auto;
  filter: brightness(0) invert(1);
}

.footer-nav {
  display: flex;
  justify-content: center;
  gap: 2rem;
  margin-bottom: 2rem;
}

.footer-link {
  color: rgba(255, 255, 255, 0.8);
  text-decoration: none;
  font-weight: 500;
  transition: color 0.2s;
}

.footer-link:hover {
  color: #17a2b8;
}

.footer-tagline {
  color: rgba(255, 255, 255, 0.6);
  font-size: 0.9rem;
}

/* Animations */
@keyframes float {
  0%, 100% { transform: rotate(-5deg) translateY(0px); }
  50% { transform: rotate(-5deg) translateY(-10px); }
}

@keyframes pulse {
  0%, 100% { box-shadow: 0 0 0 3px rgba(40, 167, 69, 0.2); }
  50% { box-shadow: 0 0 0 6px rgba(40, 167, 69, 0.4); }
}

@keyframes chart-animate {
  0%, 100% { transform: scaleY(1); }
  50% { transform: scaleY(1.2); }
}

@keyframes slide-in {
  from {
    opacity: 0;
    transform: translateX(20px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

/* Responsive Design */
@media (max-width: 768px) {
  .hero {
    grid-template-columns: 1fr;
    gap: 2rem;
    padding: 4rem 1rem;
  }

  .hero-title {
    font-size: 2.5rem;
  }

  .nav-header {
    padding: 1rem;
  }

  .nav-links {
    gap: 1rem;
  }

  .features-grid {
    grid-template-columns: 1fr;
  }

  .stats-container {
    grid-template-columns: repeat(2, 1fr);
  }

  .footer-nav {
    flex-direction: column;
    gap: 1rem;
  }
}

@media (max-width: 480px) {
  .hero-title {
    font-size: 2rem;
  }

  .hero-description {
    font-size: 1.1rem;
  }

  .section-header h2 {
    font-size: 2rem;
  }

  .cta-content h2 {
    font-size: 2rem;
  }

  .stats-container {
    grid-template-columns: 1fr;
  }

  .stat-number {
    font-size: 2.5rem;
  }
}
</style>
