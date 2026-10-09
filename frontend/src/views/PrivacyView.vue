<script setup lang="ts">
import LegalPage from '../components/LegalPage.vue'
</script>

<template>
  <LegalPage
    badge-icon="🔒"
    badge="Legal Information"
    title="Privacy"
    title-highlight="Policy"
    description="How Status Tacos processes personal data when you use the web app or the iOS app."
    last-updated="9 October 2026"
  >
    <p class="intro">
      This Privacy Policy explains how oglimmer.com / Oliver Zimpasser ("we", "our", "us")
      processes personal data when you use Status Tacos, the HTTP endpoint monitoring service at
      <a href="https://tacos.oglimmer.com">tacos.oglimmer.com</a>, and the Status Tacos iOS app
      (together the "Service"). It applies to all visitors and registered users.
    </p>

    <h2>1. Controller</h2>
    <p>
      Controller under Art. 4(7) GDPR:<br />
      Oliver Zimpasser, Berliner Str. 39a, 63110 Rodgau, Germany<br />
      Email: <a href="mailto:oglimmer@gmail.com">oglimmer@gmail.com</a>
    </p>

    <h2>2. Data We Process</h2>
    <p>We process the following categories of data, depending on how you use the Service:</p>
    <ul>
      <li>
        Account data: your name, your email address and the user ID of your login account. We
        receive these from our login service when you sign in.
      </li>
      <li>
        Tenant data: the names of your tenants and which users belong to them. Members of a
        tenant can see the email addresses of the other members.
      </li>
      <li>
        Monitor configuration: names, URLs, request headers and success criteria of the endpoints
        you monitor. Request headers can contain credentials
        (for example an API key). You decide what you enter.
      </li>
      <li>
        Check results: for each check, the time, the result, the HTTP status code, the response
        time and the error message if the check failed. We also store uptime statistics and
        outages derived from them.
      </li>
      <li>
        Alert contacts: email addresses, webhook URLs and Microsoft Teams workflow URLs that you
        enter, and the history of the alerts we sent.
      </li>
      <li>
        iOS app data: if you turn on push alerts, the push token of your device, the push
        environment and the generic device model name (for example "iPhone"), plus your choice
        of monitors to be alerted about.
      </li>
      <li>
        Technical data: IP address, browser or app user-agent and the time of each request, in
        our server logs.
      </li>
    </ul>

    <h2>3. Purposes and Legal Bases</h2>
    <ul>
      <li>
        Service provision and account management (Art. 6(1)(b) GDPR): operating the Service,
        authentication, running checks, showing results and sending the alerts you configure.
      </li>
      <li>
        Security, abuse prevention and troubleshooting (Art. 6(1)(f) GDPR): keeping the Service
        secure, preventing misuse (for example monitoring used to overload third-party systems)
        and fixing errors, using limited technical data such as IP address and user-agent.
      </li>
      <li>Legal obligations (Art. 6(1)(c) GDPR): complying with statutory requirements.</li>
    </ul>

    <h2>4. Sign-In</h2>
    <p>
      You sign in through our own login service at <code>id.oglimmer.de</code> (Keycloak, operated
      by us). You can create a login with email and password, or sign in with Google, Microsoft
      or Apple. If you choose one of them, that provider authenticates you under its own privacy
      policy and sends us your name, your email address and an account ID. We do not receive your
      password there. With Sign in with Apple you can hide your email address; we then receive an
      Apple relay address that forwards our emails to you. Our login service stores the tokens
      that Apple returns, so that we can revoke them when you delete your account.
    </p>

    <h2>5. Cookies and Local Storage</h2>
    <p>
      The Service does not use analytics or advertising cookies, and it does not track you
      across other websites or apps.
    </p>
    <ul>
      <li>
        Login session (essential): the login service at <code>id.oglimmer.de</code> sets session
        cookies so that you stay signed in. They expire when the session ends.
      </li>
      <li>
        Local storage in the web app (essential): your login tokens and your display preferences
        (for example the selected tenant filter) are kept in your browser's local storage. Signing
        out removes the login tokens.
      </li>
      <li>
        iOS app (essential): your login tokens are kept in the iOS Keychain on your device only.
        Your server settings and display preferences are kept in the app's local settings.
      </li>
    </ul>

    <h2>6. Analytics and Server Logs</h2>
    <p>
      We do not use third-party analytics, advertising SDKs or crash-reporting SDKs, in the web
      app or in the iOS app. Our servers log technical data (IP address, user-agent, time and
      requested path) to secure the Service and to find errors. You can object to processing based
      on legitimate interests (see Section 11).
    </p>

    <h2>7. Monitored Endpoints and Alert Destinations You Provide</h2>
    <p>
      The Service sends requests to endpoints you choose and sends alerts to destinations you
      choose. This changes who receives data, so please read this section.
    </p>
    <ul>
      <li>
        <strong>Requests to your endpoints.</strong> Our servers send HTTP requests to the URLs you
        configure, with the headers you configure. The operator of each endpoint
        receives our server's IP address and the content of the request. You must be authorized
        to monitor each endpoint (see our <router-link to="/terms">Terms of Service</router-link>).
      </li>
      <li>
        <strong>Email alerts.</strong> Alert emails go to the addresses you enter. They contain the
        monitor name, the URL, the status and the reason for the alert. If you enter another
        person's email address, you are responsible for being allowed to do so.
      </li>
      <li>
        <strong>Webhooks and Microsoft Teams.</strong> For webhook and Teams contacts we send the
        alert data to the URL you enter. That recipient processes the data under its own terms;
        we have no relationship with it and cannot assess where it stores the data, which may be
        outside the EU/EEA.
      </li>
      <li>
        <strong>iOS push alerts.</strong> Push alerts are delivered by the Apple Push Notification
        service (Apple Inc.). Apple receives the push token of your device and the notification
        text: the monitor name, the status, the reason and the tenant name. Apple may process
        this data in the USA.
      </li>
    </ul>

    <h2>8. Recipients and Transfers</h2>
    <p>
      We do not sell your data. We run the Service, the database, the login service and the
      email server on infrastructure we operate. Where we use service providers, they
      act as processors under Art. 28 GDPR and process data only on our instructions. We do not
      transfer data outside the EU/EEA unless adequate safeguards are in place. Separately from
      this, the recipients you choose in Section 7 (endpoints, webhooks, Microsoft Teams, Apple
      for push alerts, and Google, Microsoft or Apple if you sign in with them) receive data as
      described there.
    </p>

    <h2>9. Retention</h2>
    <ul>
      <li>Account data: retained for the life of your account; deleted upon account deletion.</li>
      <li>
        Monitor configurations, tenants and alert contacts: retained until you delete them or
        delete your account.
      </li>
      <li>
        Check results: single check results are deleted after 7 days. Hourly and daily statistics
        derived from them are deleted after about 92 days.
      </li>
      <li>Alert history: retained for a limited period for troubleshooting.</li>
      <li>
        iOS push token: deleted when you sign out of the iOS app or delete your account. Tokens
        that Apple reports as invalid (for example after you delete the app) are deleted too.
      </li>
      <li>Server logs and security data: retained for a short period unless needed to investigate issues.</li>
    </ul>

    <h2>10. Security</h2>
    <p>
      We implement appropriate technical and organizational measures to protect your data. All
      connections to the Service use HTTPS. The iOS app keeps your login tokens in the iOS
      Keychain. Monitor configurations, including request headers you enter, are stored in our
      database; do not use credentials there that give more access than the check needs. No
      online service can guarantee absolute security.
    </p>

    <h2>11. Your Rights (EU/EEA)</h2>
    <p>
      You have the rights of access, rectification, erasure, restriction, portability, and
      objection (Art. 15–21 GDPR). Where processing is based on consent, you may withdraw consent
      at any time with effect for the future. You also have the right to lodge a complaint with a
      supervisory authority in Germany.
    </p>

    <h2>12. Deleting Your Account</h2>
    <p>
      You can delete your account at any time: in the web app with "Delete Account" at the top of
      the page, or in the iOS app under Settings → Delete Account. We then delete your login
      account, your user data, your push tokens and every tenant that only you belong to, with its
      monitors, check results and alert contacts. If you signed in with Apple, we also revoke your
      Apple sign-in for Status Tacos. Tenants that you share with other users stay with them; we
      only remove you from them. If you cannot sign in any more, send an email to
      <a href="mailto:oglimmer@gmail.com">oglimmer@gmail.com</a> from the email address of your
      account and we delete it for you.
    </p>

    <h2>13. Children</h2>
    <p>
      The Service is not intended for children under 16. Do not create an account if you are
      under 16.
    </p>

    <h2>14. Changes to this Policy</h2>
    <p>
      We may update this Privacy Policy to reflect changes in our practices or legal requirements.
      The "Last updated" date reflects the effective date of the current version.
    </p>

    <h2>15. Contact</h2>
    <p>
      For privacy questions or requests, contact:
      <a href="mailto:oglimmer@gmail.com">oglimmer@gmail.com</a>.
    </p>
  </LegalPage>
</template>
