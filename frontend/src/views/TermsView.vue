<script setup lang="ts">
import LegalPage from '../components/LegalPage.vue'
</script>

<template>
  <LegalPage
    badge-icon="📜"
    badge="Legal Information"
    title="Terms of"
    title-highlight="Service"
    description="Please read these terms carefully before you use Status Tacos. By using the service, you agree to them."
    last-updated="9 October 2026"
  >
    <p class="intro">
      These Terms of Service ("Terms") govern your use of Status Tacos, the HTTP endpoint
      monitoring service at <a href="https://tacos.oglimmer.com">tacos.oglimmer.com</a>, and the
      Status Tacos iOS app (together the "Service"), provided by oglimmer.com / Oliver Zimpasser
      ("we," "our," or "us"). By accessing or using the Service, you agree to abide by these
      Terms. If you do not agree with any part of these Terms, you should not use the Service.
    </p>

    <h2>1. Acceptable Use</h2>

    <p>1.1. You must be at least 16 years old to use the Service.</p>

    <p>
      1.2. You may only monitor endpoints that you own or that you are authorized to monitor. You
      are solely responsible for the monitors you configure, including their URLs, request headers
      and check intervals.
    </p>

    <p>
      1.3. You may not use the Service for any unauthorized or illegal purposes, including but not
      limited to overloading or attacking third-party systems, probing systems for
      vulnerabilities, distributing malware, spamming, or engaging in any form of hacking. You may
      not send alerts to people who have not agreed to receive them.
    </p>

    <h2>2. Intellectual Property</h2>

    <p>
      2.1. You retain all rights in your monitor configurations and in the data the Service
      collects for you ("User Data"). You grant us a limited, non-exclusive, worldwide,
      royalty-free license to store and process your User Data solely to provide the Service to
      you and to the members of your tenants.
    </p>

    <p>
      2.2. We retain all rights in the Service software, infrastructure and any improvements or
      modifications of them.
    </p>

    <p>
      2.3. We do not claim any ownership of your User Data and will never sell, license, or use it
      for any purpose other than providing the Service as described above.
    </p>

    <h2>3. Privacy</h2>

    <p>
      3.1. Our <router-link to="/privacy">Privacy Policy</router-link> explains how we collect and
      process personal data, the purposes and legal bases, retention periods, and your rights. By
      using the Service, you acknowledge that you have read it.
    </p>

    <h2>4. User Accounts and Tenants</h2>

    <p>
      4.1. You are responsible for maintaining the confidentiality of your account credentials and
      for all activities that occur under your account.
    </p>

    <p>
      4.2. You agree to provide accurate, current, and complete information when you sign up and
      to keep it accurate, current, and complete.
    </p>

    <p>
      4.3. Monitors and alert contacts belong to a tenant. Every member of a tenant can see and
      change its monitors and alert contacts and can add or remove other members. Only add people
      you trust to a tenant.
    </p>

    <p>
      4.4. We reserve the right to suspend or terminate your account at our discretion if we
      believe you have violated these Terms or engaged in any fraudulent or abusive behavior.
    </p>

    <h2>5. Monitoring and Alerts</h2>

    <p>
      5.1. <strong>No guarantee of detection.</strong> The Service checks your endpoints at
      regular intervals from our servers. A check can fail or be delayed because of our servers,
      the network or the endpoint itself. We do not guarantee that every outage is detected, that
      it is detected within a certain time, or that a reported outage is real.
    </p>

    <p>
      5.2. <strong>No guarantee of delivery.</strong> Alerts by email, webhook, Microsoft Teams and
      iOS push notification depend on systems that we do not control, such as email providers,
      the recipient of a webhook and the Apple Push Notification service. We do not guarantee that
      an alert is delivered, or delivered in time. Do not rely on the Service as your only means
      of detecting outages of critical systems.
    </p>

    <p>
      5.3. <strong>Requests sent on your instruction.</strong> By creating a monitor you instruct
      us to send HTTP requests to its URL, with the headers you configure, at the interval you
      choose. You alone are responsible for the load and any costs these requests cause on the
      endpoint, and for any consequences under your agreements with the operator of the endpoint.
    </p>

    <p>
      5.4. <strong>Credentials in monitors.</strong> If you put credentials in request headers,
      use credentials that only allow what the check needs. Every member of the tenant can see
      them.
    </p>

    <p>
      5.5. <strong>Alert destinations you provide.</strong> You are responsible for the email
      addresses, webhook URLs and Microsoft Teams workflow URLs you enter, for being allowed to
      send alerts to them, and for what their operators do with the alert data.
    </p>

    <p>
      5.6. <strong>Data retention.</strong> Single check results are kept for 7 days and
      statistics for about 92 days, as described in the Privacy Policy. The Service is a
      monitoring tool, not an archive. We do not back up User Data for you.
    </p>

    <p>
      5.7. <strong>Fair use.</strong> The Service is free. We may limit the number of monitors,
      the check interval or the number of alerts per account at any time, and we may pause
      monitors that cause problems for our systems or for third parties.
    </p>

    <h2>6. The iOS App</h2>

    <p>
      6.1. The iOS app is licensed to you under Apple's Standard Licensed Application End User
      License Agreement, in addition to these Terms. These Terms are between you and us, not
      Apple. Apple is not responsible for the app or its content and has no obligation to provide
      maintenance or support for it.
    </p>

    <p>
      6.2. You can point the iOS app to another Status Tacos server and login service. These Terms
      and our Privacy Policy apply only to the Service we operate, not to servers operated by
      others.
    </p>

    <h2>7. Email Communication</h2>

    <p>
      7.1. We send the alert emails you configure and transactional or service communications
      necessary to operate your account and the Service (e.g., account notices, security
      alerts).
    </p>

    <p>
      7.2. We do not send marketing emails without your prior, explicit consent. If we introduce
      marketing communications in the future, we will request your opt-in and include an
      unsubscribe option in every message.
    </p>

    <p>
      7.3. Deleting your account removes your email address from our systems, after which we will
      no longer contact you.
    </p>

    <p>7.4. We will not sell your email address to any third party company.</p>

    <h2>8. Termination</h2>

    <p>
      8.1. You may stop using the Service at any time and ask us to delete your account, as
      described in the <router-link to="/privacy">Privacy Policy</router-link>.
    </p>

    <p>
      8.2. We reserve the right to suspend, terminate, or modify the Service at any time for any
      reason without notice.
    </p>

    <h2>9. Disclaimer of Warranties</h2>

    <p>
      9.1. The Service is provided "as is" and "as available." We do not make any warranties or
      representations regarding the accuracy, completeness, availability or reliability of the
      Service, its check results or its alerts.
    </p>

    <h2>10. Limitation of Liability</h2>

    <p>
      10.1. To the extent permitted by applicable law, we shall not be liable for any indirect,
      incidental, special, consequential, or punitive damages, or any loss of profits or revenues,
      whether incurred directly or indirectly, or any loss of data, use, goodwill, or other
      intangible losses, including losses caused by an outage that was not detected or an alert
      that was not delivered. Liability for intent, gross negligence and injury to life, body or
      health remains unaffected.
    </p>

    <h2>11. Governing Law</h2>

    <p>
      11.1. These Terms shall be governed by and construed in accordance with the laws of Germany,
      without regard to its conflict of law principles.
    </p>

    <h2>12. Changes to Terms</h2>

    <p>
      12.1. We reserve the right to modify or replace these Terms at any time. It is your
      responsibility to review these Terms periodically. Your continued use of the Service after
      any changes indicates your acceptance of those changes.
    </p>

    <h2>13. Contact</h2>

    <p>
      13.1. If you have any questions or concerns about these Terms, please contact us at
      <a href="mailto:oglimmer@gmail.com">oglimmer@gmail.com</a>.
    </p>
  </LegalPage>
</template>
