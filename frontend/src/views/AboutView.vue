<script setup lang="ts">
import VersionInfo from '../components/VersionInfo.vue'
</script>

<template>
  <main class="about-container">
    <!-- Navigation Header -->
    <header class="nav-header">
      <div class="nav-brand">
        <img src="../assets/logo.png" alt="Status Tacos" class="nav-logo" />
        <span class="brand-name">Status Tacos</span>
      </div>
      <nav class="nav-links">
        <router-link to="/" class="nav-link">Home</router-link>
        <router-link to="/tos" class="nav-link">Terms</router-link>
        <router-link to="/imprint" class="nav-link">Imprint</router-link>
      </nav>
    </header>

    <!-- Hero Section -->
    <section class="hero">
      <div class="hero-content">
        <div class="hero-badge">
          <span class="badge-icon">🌮</span>
          <span>About Our Mission</span>
        </div>
        <h1 class="hero-title">
          Made with ❤️ for<br>
          <span class="gradient-text">Developers Everywhere</span>
        </h1>
        <p class="hero-description">
          Status Tacos was born from the need for reliable, easy-to-use monitoring that doesn't break the bank.
          We believe every developer deserves peace of mind when it comes to their services.
        </p>
      </div>

      <div class="hero-visual">
        <div class="story-card">
          <div class="story-icon">🚀</div>
          <h3>Our Story</h3>
          <p>Started in 2024 with a simple goal: make monitoring deliciously simple</p>
        </div>
      </div>
    </section>

    <!-- Story Section -->
    <section class="story-section">
      <div class="section-header">
        <h2>The Story Behind Status Tacos</h2>
        <p>How we're revolutionizing endpoint monitoring, one taco at a time</p>
      </div>

      <div class="story-grid">
        <div class="story-item">
          <div class="story-number">01</div>
          <h3>The Problem</h3>
          <p>Existing monitoring tools were either too complex, too expensive, or too limited. Developers needed something better.</p>
        </div>

        <div class="story-item">
          <div class="story-number">02</div>
          <h3>The Vision</h3>
          <p>We envisioned a monitoring platform that's as delightful to use as it is powerful - hence the taco theme that brings joy to monitoring.</p>
        </div>

        <div class="story-item">
          <div class="story-number">03</div>
          <h3>The Solution</h3>
          <p>Status Tacos combines enterprise-grade monitoring with an intuitive interface that developers actually enjoy using.</p>
        </div>
      </div>
    </section>

    <!-- Values Section -->
    <section class="values-section">
      <div class="values-container">
        <div class="values-content">
          <h2>Our Values</h2>
          <div class="values-grid">
            <div class="value-card">
              <div class="value-icon">🎯</div>
              <h3>Reliability First</h3>
              <p>Your monitoring is only as good as its uptime. We maintain 99.99% availability so you can trust us to watch your services.</p>
            </div>

            <div class="value-card">
              <div class="value-icon">💡</div>
              <h3>Developer Experience</h3>
              <p>We're developers too. Every feature is built with the developer experience in mind, from setup to daily use.</p>
            </div>

            <div class="value-card">
              <div class="value-icon">🌍</div>
              <h3>Global Perspective</h3>
              <p>Your users are everywhere. Our global monitoring network ensures you see performance from every corner of the world.</p>
            </div>

            <div class="value-card">
              <div class="value-icon">🔒</div>
              <h3>Security & Privacy</h3>
              <p>Your data is yours. We use industry-standard encryption and never share your monitoring data with third parties.</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Team Section -->
    <section class="team-section">
      <div class="section-header">
        <h2>Built by Developers, for Developers</h2>
        <p>Our team understands the challenges of modern web development</p>
      </div>

      <div class="team-stats">
        <div class="stat-item">
          <div class="stat-number">10+</div>
          <div class="stat-label">Years Combined Experience</div>
        </div>
        <div class="stat-item">
          <div class="stat-number">1M+</div>
          <div class="stat-label">Endpoints Monitored</div>
        </div>
        <div class="stat-item">
          <div class="stat-number">99.99%</div>
          <div class="stat-label">Platform Uptime</div>
        </div>
        <div class="stat-item">
          <div class="stat-number">24/7</div>
          <div class="stat-label">Support Available</div>
        </div>
      </div>
    </section>

    <!-- Technology Section -->
    <section class="tech-section">
      <div class="tech-content">
        <div class="tech-text">
          <h2>Built with Modern Technology</h2>
          <p>Status Tacos is built using cutting-edge technologies to ensure reliability, scalability, and performance.</p>

          <div class="tech-features">
            <div class="tech-feature">
              <span class="tech-icon">⚡</span>
              <div>
                <h4>Lightning Fast</h4>
                <p>Global CDN and optimized infrastructure for sub-second response times</p>
              </div>
            </div>

            <div class="tech-feature">
              <span class="tech-icon">🔄</span>
              <div>
                <h4>Real-time Updates</h4>
                <p>WebSocket connections for instant status updates and notifications</p>
              </div>
            </div>

            <div class="tech-feature">
              <span class="tech-icon">📈</span>
              <div>
                <h4>Scalable Architecture</h4>
                <p>Microservices architecture that scales with your monitoring needs</p>
              </div>
            </div>
          </div>
        </div>

        <div class="version-section">
          <h3>Version Information</h3>
          <div class="version-card">
            <VersionInfo />
          </div>
        </div>
      </div>
    </section>

    <!-- CTA Section -->
    <section class="cta-section">
      <div class="cta-content">
        <h2>Ready to Join the Taco Family?</h2>
        <p>Start monitoring your endpoints with the platform that developers love to use.</p>
        <router-link to="/" class="btn btn-primary btn-large">
          <span class="btn-icon">🌮</span>
          Get Started Today
        </router-link>
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
          <router-link to="/" class="footer-link">Home</router-link>
          <router-link to="/tos" class="footer-link">Terms of Service</router-link>
          <router-link to="/imprint" class="footer-link">Imprint</router-link>
        </nav>
        <div class="footer-tagline">
          Made with ❤️ and a lot of 🌮
        </div>
      </div>
    </footer>
  </main>
</template>

<style scoped>
.about-container {
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

.hero-visual {
  display: flex;
  justify-content: center;
  align-items: center;
}

.story-card {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  padding: 2rem;
  border-radius: 16px;
  text-align: center;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  transform: rotate(-3deg);
  animation: float 6s ease-in-out infinite;
}

.story-icon {
  font-size: 3rem;
  margin-bottom: 1rem;
}

.story-card h3 {
  color: #2c3e50;
  margin-bottom: 1rem;
  font-size: 1.5rem;
}

.story-card p {
  color: #6c757d;
  margin: 0;
}

/* Story Section */
.story-section {
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

.story-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 3rem;
  max-width: 1000px;
  margin: 0 auto;
}

.story-item {
  text-align: center;
  position: relative;
}

.story-number {
  font-size: 4rem;
  font-weight: 800;
  color: #007bff;
  opacity: 0.1;
  position: absolute;
  top: -2rem;
  left: 50%;
  transform: translateX(-50%);
  z-index: 0;
}

.story-item h3 {
  font-size: 1.5rem;
  font-weight: 600;
  color: #2c3e50;
  margin-bottom: 1rem;
  position: relative;
  z-index: 1;
}

.story-item p {
  color: #6c757d;
  line-height: 1.6;
  position: relative;
  z-index: 1;
}

/* Values Section */
.values-section {
  background: linear-gradient(135deg, #2c3e50 0%, #34495e 100%);
  padding: 6rem 2rem;
  color: white;
}

.values-container {
  max-width: 1200px;
  margin: 0 auto;
}

.values-content h2 {
  font-size: 2.5rem;
  font-weight: 700;
  text-align: center;
  margin-bottom: 3rem;
}

.values-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 2rem;
}

.value-card {
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
  padding: 2rem;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  text-align: center;
  transition: transform 0.2s;
}

.value-card:hover {
  transform: translateY(-4px);
}

.value-icon {
  font-size: 3rem;
  margin-bottom: 1rem;
  display: block;
}

.value-card h3 {
  font-size: 1.25rem;
  font-weight: 600;
  margin-bottom: 1rem;
}

.value-card p {
  color: rgba(255, 255, 255, 0.9);
  line-height: 1.6;
  margin: 0;
}

/* Team Section */
.team-section {
  background: white;
  padding: 6rem 2rem;
}

.team-stats {
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
  color: #007bff;
  margin-bottom: 0.5rem;
}

.stat-label {
  font-size: 1.1rem;
  color: #6c757d;
  font-weight: 500;
}

/* Technology Section */
.tech-section {
  background: #f8f9fa;
  padding: 6rem 2rem;
}

.tech-content {
  max-width: 1200px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 4rem;
  align-items: start;
}

.tech-text h2 {
  font-size: 2.5rem;
  font-weight: 700;
  color: #2c3e50;
  margin-bottom: 1rem;
}

.tech-text p {
  font-size: 1.1rem;
  color: #6c757d;
  line-height: 1.6;
  margin-bottom: 2rem;
}

.tech-features {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.tech-feature {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
}

.tech-icon {
  font-size: 2rem;
  flex-shrink: 0;
}

.tech-feature h4 {
  color: #2c3e50;
  margin-bottom: 0.5rem;
  font-size: 1.1rem;
}

.tech-feature p {
  color: #6c757d;
  margin: 0;
  line-height: 1.5;
}

.version-section {
  background: white;
  padding: 2rem;
  border-radius: 12px;
  border: 1px solid #e9ecef;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  position: sticky;
  top: 120px;
}

.version-section h3 {
  color: #2c3e50;
  margin-bottom: 1rem;
  font-size: 1.25rem;
}

.version-card {
  border-radius: 8px;
  overflow: hidden;
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

.btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(0, 123, 255, 0.4);
}

.btn-large {
  padding: 1rem 2rem;
  font-size: 1.1rem;
}

.btn-icon {
  font-size: 1.2rem;
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
  0%, 100% { transform: rotate(-3deg) translateY(0px); }
  50% { transform: rotate(-3deg) translateY(-10px); }
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

  .tech-content {
    grid-template-columns: 1fr;
    gap: 2rem;
  }

  .story-grid {
    grid-template-columns: 1fr;
  }

  .values-grid {
    grid-template-columns: 1fr;
  }

  .team-stats {
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

  .section-header h2 {
    font-size: 2rem;
  }

  .cta-content h2 {
    font-size: 2rem;
  }

  .team-stats {
    grid-template-columns: 1fr;
  }
}
</style>
