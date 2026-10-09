<script setup lang="ts">
import LegalPage from '../components/LegalPage.vue'
</script>

<template>
  <LegalPage
    badge-icon="🏢"
    badge="Legal Information"
    title="Imprint /"
    title-highlight="Impressum"
    description="Legal information and contact details."
  >
    <p class="intro">For Germany: Angaben gemäß § 5 TMG</p>

    <h2>Diensteanbieter / Website Provider</h2>
    <p>Diese Webseite und die iOS-App werden bereitgestellt von / This website and the iOS app are provided by:</p>
    <div class="info-card">
      <h3>Oliver Zimpasser</h3>
      <p>
        Berliner Str. 39a<br />
        63110 Rodgau<br />
        Germany
      </p>
    </div>

    <h2>Kontakt / Contact</h2>
    <p>
      E‑Mail: <a href="mailto:oglimmer@gmail.com">oglimmer@gmail.com</a><br />
      (Angaben zur schnellen elektronischen Kontaktaufnahme und unmittelbaren Kommunikation gem. § 5 Abs. 1 Nr. 2 TMG)
    </p>

    <h2>Verantwortlich i.S.d. § 18 Abs. 2 MStV</h2>
    <p>
      Verantwortlich für den Inhalt / Responsible for content:<br />
      Oliver Zimpasser, Berliner Str. 39a, 63110 Rodgau, Germany
    </p>

    <h2>EU‑Streitschlichtung / Dispute Resolution</h2>
    <p>
      Die Europäische Kommission stellt eine Plattform zur Online‑Streitbeilegung (OS) bereit:
      <a href="https://ec.europa.eu/consumers/odr" target="_blank" rel="noopener">https://ec.europa.eu/consumers/odr</a>.
      Wir sind nicht verpflichtet und grundsätzlich nicht bereit, an Streitbeilegungsverfahren vor einer
      Verbraucherschlichtungsstelle teilzunehmen.
    </p>

    <h2>Support</h2>
    <p>
      For help with Status Tacos, the web app or the iOS app, send an email to
      <a href="mailto:oglimmer@gmail.com">oglimmer@gmail.com</a>.
    </p>

    <h2>Service Information</h2>
    <p>All services are provided free of charge.</p>
  </LegalPage>
</template>
