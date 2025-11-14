<template>
  <main class="tos-container">
    <!-- Navigation Header -->
    <header class="nav-header">
      <div class="nav-brand">
        <img src="../assets/logo.png" alt="Status Tacos" class="nav-logo" />
        <span class="brand-name">Status Tacos</span>
      </div>
      <nav class="nav-links">
        <router-link to="/" class="nav-link">Home</router-link>
        <router-link to="/about" class="nav-link">About</router-link>
        <router-link to="/imprint" class="nav-link">Imprint</router-link>
      </nav>
    </header>

    <!-- Hero Section -->
    <section class="hero">
      <div class="hero-content">
        <div class="hero-badge">
          <span class="badge-icon">📜</span>
          <span>Legal Information</span>
        </div>
        <h1 class="hero-title">
          Terms of <span class="gradient-text">Service</span>
        </h1>
        <p class="hero-description">
          Please read these terms carefully before using Status Tacos.
          By using our service, you agree to these terms and conditions.
        </p>
        <div class="last-updated">
          <span class="update-icon">🕒</span>
          <span>Last updated: July 2025</span>
        </div>
      </div>
    </section>

    <!-- Terms Content -->
    <section class="terms-section">
      <div class="terms-container">
        <!-- Table of Contents -->
        <aside class="toc">
          <h3>Contents</h3>
          <nav class="toc-nav">
            <a href="#acceptable-use" class="toc-link">1. Nature of Service and User Obligations</a>
            <a href="#intellectual-property" class="toc-link">2. Intellectual Property and Data</a>
            <a href="#privacy" class="toc-link">3. Data Protection and Privacy</a>
            <a href="#email-communication" class="toc-link">4. Communications</a>
            <a href="#accounts" class="toc-link">5. User Accounts and Access</a>
            <a href="#termination" class="toc-link">6. Service Availability and Termination</a>
            <a href="#disclaimer" class="toc-link">7. Disclaimer of Warranties</a>
            <a href="#liability" class="toc-link">8. Limitation of Liability</a>
            <a href="#governing-law" class="toc-link">9. Governing Law and Jurisdiction</a>
            <a href="#changes" class="toc-link">10. Modifications to Terms</a>
            <a href="#contact" class="toc-link">11. Contact Information</a>
          </nav>
        </aside>

        <!-- Terms Content -->
        <div class="terms-content">
          <div class="intro-text">
            These General Terms of Use ("Terms") govern the use of the web application "Status Tacos" (hereinafter "Service"), a free HTTP endpoint monitoring service provided by Oliver Zimpasser, operating under oglimmer.com ("Provider", "we", "us", "our"). By accessing or using the Service, you ("User", "you") acknowledge that you have read, understood, and agree to be bound by these Terms. If you do not agree to these Terms, you are not authorized to use the Service.
          </div>

          <section id="acceptable-use" class="term-section">
            <div class="section-number">01</div>
            <h2>Nature of Service and User Obligations</h2>
            <div class="section-content">
              <p>1.1. The Service is provided as a free, experimental software tool for monitoring HTTP endpoints. The Service is offered on a voluntary basis without any legal obligation or guarantee.</p>

              <p>1.2. Users must be at least 16 years of age to use the Service. Users under 18 require parental consent.</p>

              <p>1.3. Users are solely responsible for ensuring they have proper authorization to monitor any endpoints they configure. Users acknowledge that unauthorized monitoring may violate applicable laws and third-party rights.</p>

              <p>1.4. Users agree to use the Service only for lawful purposes and in compliance with all applicable German and international laws. Prohibited uses include, but are not limited to: monitoring without authorization, attempting to compromise system security, distributing malicious software, or any form of illegal activity.</p>
            </div>
          </section>

          <section id="intellectual-property" class="term-section">
            <div class="section-number">02</div>
            <h2>Intellectual Property and Data</h2>
            <div class="section-content">
              <p>2.1. Users retain ownership of their monitoring configurations and data. By using the Service, Users grant the Provider a non-exclusive, royalty-free license to process such data solely for the purpose of providing the monitoring functionality.</p>

              <p>2.2. The Provider retains all rights to the Service software, infrastructure, and any improvements or modifications thereof.</p>

              <p>2.3. Users acknowledge that monitoring data may be stored temporarily and processed automatically. The Provider makes no guarantees regarding data retention, backup, or recovery.</p>
            </div>
          </section>

          <section id="privacy" class="term-section">
            <div class="section-number">03</div>
            <h2>Data Protection and Privacy</h2>
            <div class="section-content">
              <p>3.1. The Provider processes personal data in accordance with applicable German data protection laws (DSGVO/GDPR). A separate Privacy Policy governs data processing practices.</p>

              <p>3.2. Users acknowledge that the Service may collect and process technical data including but not limited to: IP addresses, browser information, usage patterns, and monitoring configurations for the sole purpose of providing the Service.</p>

              <p>3.3. The Provider reserves the right to monitor, review, and analyze User activities to ensure compliance with these Terms and applicable laws. Users consent to such monitoring as a condition of Service use.</p>
            </div>
          </section>

          <section id="email-communication" class="term-section">
            <div class="section-number">04</div>
            <h2>Communications</h2>
            <div class="section-content">
              <p>4.1. By providing an email address, Users consent to receiving operational communications related to the Service, including but not limited to: account notifications, monitoring alerts, and important Service announcements.</p>

              <p>4.2. The Provider may send communications at its discretion. Users acknowledge that the Service does not currently support email opt-out functionality.</p>

              <p>4.3. Account deletion will result in removal of the User's email address from the Provider's systems.</p>
            </div>
          </section>

          <section id="accounts" class="term-section">
            <div class="section-number">05</div>
            <h2>User Accounts and Access</h2>
            <div class="section-content">
              <p>5.1. Users are solely responsible for maintaining the security and confidentiality of their account credentials. All activities under a User account are the User's responsibility.</p>

              <p>5.2. Users must provide accurate registration information and maintain its accuracy. False or misleading information may result in account termination.</p>

              <p>5.3. The Provider reserves the right to suspend, restrict, or terminate User accounts at any time, with or without cause, and without prior notice.</p>
            </div>
          </section>

          <section id="termination" class="term-section">
            <div class="section-number">06</div>
            <h2>Service Availability and Termination</h2>
            <div class="section-content">
              <p>6.1. The Service is provided voluntarily and may be discontinued, modified, or suspended at any time without notice or obligation to Users.</p>

              <p>6.2. Users may discontinue use of the Service at any time by deleting their account or ceasing to access the Service.</p>

              <p>6.3. Upon termination, all User data may be deleted without notice or possibility of recovery. The Provider has no obligation to preserve or return User data.</p>
            </div>
          </section>

          <section id="disclaimer" class="term-section">
            <div class="section-number">07</div>
            <h2>Disclaimer of Warranties</h2>
            <div class="section-content">
              <p>7.1. THE SERVICE IS PROVIDED "AS IS" AND "AS AVAILABLE" WITHOUT ANY WARRANTIES OF ANY KIND, WHETHER EXPRESS OR IMPLIED. THE PROVIDER EXPRESSLY DISCLAIMS ALL WARRANTIES INCLUDING, BUT NOT LIMITED TO, IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NON-INFRINGEMENT.</p>

              <p>7.2. The Provider makes no warranties regarding the accuracy, reliability, availability, or timeliness of the Service or any monitoring results. Users acknowledge that monitoring results may be incomplete, inaccurate, or delayed.</p>

              <p>7.3. The Provider does not warrant that the Service will be uninterrupted, error-free, secure, or free from viruses or other harmful components.</p>
            </div>
          </section>

          <section id="liability" class="term-section">
            <div class="section-number">08</div>
            <h2>Limitation of Liability</h2>
            <div class="section-content">
              <p>8.1. TO THE MAXIMUM EXTENT PERMITTED BY GERMAN LAW, THE PROVIDER SHALL NOT BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, CONSEQUENTIAL, OR PUNITIVE DAMAGES, INCLUDING BUT NOT LIMITED TO: LOSS OF PROFITS, DATA, USE, GOODWILL, BUSINESS INTERRUPTION, OR ANY OTHER COMMERCIAL DAMAGES OR LOSSES.</p>

              <p>8.2. The Provider's total liability for any claims arising from or related to the Service shall not exceed the amount of €0 (zero euros), reflecting the free nature of the Service.</p>

              <p>8.3. Users acknowledge that they use the Service at their own risk and are solely responsible for any consequences arising from such use, including any reliance on monitoring results or Service availability.</p>

              <p>8.4. This limitation of liability applies regardless of the legal theory upon which the claim is based, including contract, tort, negligence, strict liability, or otherwise.</p>
            </div>
          </section>

          <section id="governing-law" class="term-section">
            <div class="section-number">09</div>
            <h2>Governing Law and Jurisdiction</h2>
            <div class="section-content">
              <p>9.1. These Terms shall be governed by and construed in accordance with German law, excluding the UN Convention on Contracts for the International Sale of Goods (CISG).</p>

              <p>9.2. Any disputes arising from or in connection with these Terms or the Service shall be subject to the exclusive jurisdiction of the German courts.</p>

              <p>9.3. If any provision of these Terms is found to be invalid or unenforceable, the remaining provisions shall remain in full force and effect.</p>
            </div>
          </section>

          <section id="changes" class="term-section">
            <div class="section-number">10</div>
            <h2>Modifications to Terms</h2>
            <div class="section-content">
              <p>10.1. The Provider reserves the right to modify these Terms at any time without prior notice. Continued use of the Service after any such changes constitutes acceptance of the modified Terms.</p>

              <p>10.2. Users are responsible for regularly reviewing these Terms. The Provider has no obligation to notify Users of changes.</p>

              <p>10.3. If Users do not agree to modified Terms, their sole remedy is to discontinue use of the Service.</p>
            </div>
          </section>

          <section id="contact" class="term-section">
            <div class="section-number">11</div>
            <h2>Contact Information</h2>
            <div class="section-content">
              <p>11.1. For questions regarding these Terms, Users may contact the Provider at: <a href="mailto:oglimmer@gmail.com">oglimmer@gmail.com</a></p>

              <p>11.2. The Provider is under no obligation to respond to inquiries and may respond at its sole discretion.</p>
            </div>
          </section>
        </div>
      </div>
    </section>

    <!-- CTA Section -->
    <section class="cta-section">
      <div class="cta-content">
        <h2>Ready to Get Started?</h2>
        <p>Now that you've reviewed our terms, join thousands of developers monitoring their services with Status Tacos.</p>
        <router-link to="/" class="btn btn-primary btn-large">
          <span class="btn-icon">🌮</span>
          Start Monitoring Today
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
          <router-link to="/about" class="footer-link">About</router-link>
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
.tos-container {
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
  padding: 6rem 2rem 4rem;
  text-align: center;
  color: white;
}

.hero-content {
  max-width: 800px;
  margin: 0 auto;
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
  margin-bottom: 2rem;
  color: rgba(255, 255, 255, 0.9);
  max-width: 600px;
  margin-left: auto;
  margin-right: auto;
}

.last-updated {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
  padding: 0.75rem 1.5rem;
  border-radius: 25px;
  font-size: 0.9rem;
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.update-icon {
  font-size: 1rem;
}

/* Terms Section */
.terms-section {
  background: white;
  padding: 4rem 2rem;
}

.terms-container {
  max-width: 1200px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 250px 1fr;
  gap: 4rem;
  align-items: start;
}

/* Table of Contents */
.toc {
  position: sticky;
  top: 120px;
  background: #f8f9fa;
  padding: 2rem;
  border-radius: 12px;
  border: 1px solid #e9ecef;
}

.toc h3 {
  color: #2c3e50;
  margin-bottom: 1rem;
  font-size: 1.25rem;
}

.toc-nav {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.toc-link {
  color: #6c757d;
  text-decoration: none;
  padding: 0.5rem 0;
  font-size: 0.9rem;
  transition: all 0.2s;
  border-left: 3px solid transparent;
  padding-left: 0.75rem;
}

.toc-link:hover {
  color: #007bff;
  border-left-color: #007bff;
  background: rgba(0, 123, 255, 0.05);
}

/* Terms Content */
.terms-content {
  line-height: 1.6;
}

.intro-text {
  background: #f8f9fa;
  padding: 2rem;
  border-radius: 12px;
  border: 1px solid #e9ecef;
  margin-bottom: 3rem;
  font-size: 1.1rem;
  line-height: 1.7;
  color: #495057;
}

.term-section {
  margin-bottom: 4rem;
  position: relative;
  padding-top: 2rem;
}

.section-number {
  position: absolute;
  top: 0;
  left: -3rem;
  font-size: 3rem;
  font-weight: 800;
  color: #007bff;
  opacity: 0.1;
}

.term-section h2 {
  font-size: 2rem;
  font-weight: 700;
  color: #2c3e50;
  margin-bottom: 1.5rem;
  position: relative;
}

.section-content {
  position: relative;
}

.section-content p {
  color: #6c757d;
  margin-bottom: 1.5rem;
  font-size: 1rem;
  line-height: 1.7;
}

.section-content ul {
  margin: 1.5rem 0;
  padding-left: 2rem;
}

.section-content li {
  color: #6c757d;
  margin-bottom: 0.75rem;
  line-height: 1.6;
}

/* Feature List */
.feature-list {
  list-style: none;
  padding: 0;
  margin: 1.5rem 0;
}

.feature-list li {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem 0;
  border-bottom: 1px solid #f1f3f4;
}

.feature-list li:last-child {
  border-bottom: none;
}

.feature-icon {
  font-size: 1.25rem;
  flex-shrink: 0;
}

/* Restrictions Grid */
.restrictions-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 1.5rem;
  margin: 2rem 0;
}

.restriction-item {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
  padding: 1.5rem;
  background: #f8f9fa;
  border-radius: 8px;
  border: 1px solid #e9ecef;
}

.restriction-icon {
  font-size: 1.5rem;
  flex-shrink: 0;
}

.restriction-item h4 {
  color: #2c3e50;
  margin-bottom: 0.5rem;
  font-size: 1rem;
  font-weight: 600;
}

.restriction-item p {
  color: #6c757d;
  margin: 0;
  font-size: 0.9rem;
  line-height: 1.5;
}

/* Privacy Highlights */
.privacy-highlights {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 1.5rem;
  margin: 2rem 0;
}

.privacy-item {
  text-align: center;
  padding: 1.5rem;
  background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
  border-radius: 12px;
  border: 1px solid #e9ecef;
}

.privacy-icon {
  font-size: 2rem;
  margin-bottom: 1rem;
  display: block;
}

.privacy-item h4 {
  color: #2c3e50;
  margin-bottom: 0.75rem;
  font-size: 1.1rem;
  font-weight: 600;
}

.privacy-item p {
  color: #6c757d;
  margin: 0;
  font-size: 0.9rem;
  line-height: 1.5;
}

/* Contact Methods */
.contact-methods {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 1.5rem;
  margin: 2rem 0;
}

.contact-item {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1.5rem;
  background: #f8f9fa;
  border-radius: 8px;
  border: 1px solid #e9ecef;
}

.contact-icon {
  font-size: 1.5rem;
  flex-shrink: 0;
}

.contact-item h4 {
  color: #2c3e50;
  margin-bottom: 0.25rem;
  font-size: 1rem;
  font-weight: 600;
}

.contact-item p {
  color: #007bff;
  margin: 0;
  font-size: 0.9rem;
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

/* Responsive Design */
@media (max-width: 768px) {
  .hero-title {
    font-size: 2.5rem;
  }

  .terms-container {
    grid-template-columns: 1fr;
    gap: 2rem;
  }

  .toc {
    position: static;
    order: 2;
  }

  .section-number {
    display: none;
  }

  .restrictions-grid,
  .privacy-highlights,
  .contact-methods {
    grid-template-columns: 1fr;
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

  .cta-content h2 {
    font-size: 2rem;
  }

  .term-section h2 {
    font-size: 1.5rem;
  }
}
</style>
