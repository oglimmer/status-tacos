/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.CNAMERecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Message;
import org.xbill.DNS.NSRecord;
import org.xbill.DNS.Name;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.Section;
import org.xbill.DNS.Type;

class AuthoritativeDnsResolverTest {

  private static final InetAddress SERVER = InetAddress.getLoopbackAddress();
  private static final Name ZONE = Name.fromConstantString("example.de.");

  private static Name name(String name) throws Exception {
    return Name.fromString(name);
  }

  private static Message response(Name qname, int rcode, boolean authoritative, Record... answers) {
    Message message = Message.newQuery(Record.newRecord(qname, Type.A, DClass.IN));
    message.getHeader().setFlag(Flags.QR);
    message.getHeader().setRcode(rcode);
    if (authoritative) {
      message.getHeader().setFlag(Flags.AA);
    }
    for (Record answer : answers) {
      message.addRecord(answer, Section.ANSWER);
    }
    return message;
  }

  private static ARecord a(Name owner, String ip) throws Exception {
    return new ARecord(owner, DClass.IN, 600, InetAddress.getByName(ip));
  }

  @Test
  void addressOfTheName() throws Exception {
    Name host = name("example.de.");
    var answer =
        AuthoritativeDnsResolver.parse(
            response(host, Rcode.NOERROR, true, a(host, "192.0.2.1")), host, ZONE, Type.A, SERVER);

    assertThat(answer.addresses())
        .extracting(InetAddress::getHostAddress)
        .containsExactly("192.0.2.1");
    assertThat(answer.addresses().get(0).getHostName()).isEqualTo("example.de");
  }

  @Test
  void followsCnameInTheSameZone() throws Exception {
    Name host = name("www.example.de.");
    Name target = name("web.example.de.");
    var answer =
        AuthoritativeDnsResolver.parse(
            response(
                host,
                Rcode.NOERROR,
                true,
                new CNAMERecord(host, DClass.IN, 600, target),
                a(target, "192.0.2.2")),
            host,
            ZONE,
            Type.A,
            SERVER);

    assertThat(answer.addresses())
        .extracting(InetAddress::getHostAddress)
        .containsExactly("192.0.2.2");
  }

  @Test
  void cnameIntoAnotherZoneGivesTheNameToAskNext() throws Exception {
    Name host = name("www.example.de.");
    Name target = name("lb.cdn.example.com.");
    var answer =
        AuthoritativeDnsResolver.parse(
            response(host, Rcode.NOERROR, true, new CNAMERecord(host, DClass.IN, 600, target)),
            host,
            ZONE,
            Type.A,
            SERVER);

    assertThat(answer.addresses()).isEmpty();
    assertThat(answer.cnameTarget()).isEqualTo(target);
  }

  @Test
  void nxdomainIsAnError() throws Exception {
    Name host = name("gone.example.de.");
    assertThatThrownBy(
            () ->
                AuthoritativeDnsResolver.parse(
                    response(host, Rcode.NXDOMAIN, true), host, ZONE, Type.A, SERVER))
        .isInstanceOf(UnknownHostException.class)
        .hasMessageContaining("NXDOMAIN");
  }

  @Test
  void answerThatIsNotAuthoritativeIsNotUsed() throws Exception {
    Name host = name("example.de.");
    assertThat(
            AuthoritativeDnsResolver.parse(
                response(host, Rcode.NOERROR, false, a(host, "192.0.2.1")),
                host,
                ZONE,
                Type.A,
                SERVER))
        .isNull();
  }

  /** fobiapp.aerztekammern.de: the zone aerztekammern.de sends the subdomain to other servers. */
  @Test
  void referralToTheNameserversOfASubdomain() throws Exception {
    Name host = name("app.sub.example.de.");
    Name child = name("sub.example.de.");
    Message message = response(host, Rcode.NOERROR, false);
    message.addRecord(
        new NSRecord(child, DClass.IN, 600, name("ns1.other.com.")), Section.AUTHORITY);
    message.addRecord(
        new NSRecord(child, DClass.IN, 600, name("ns2.other.com.")), Section.AUTHORITY);

    var answer = AuthoritativeDnsResolver.parse(message, host, ZONE, Type.A, SERVER);

    assertThat(answer.addresses()).isEmpty();
    assertThat(answer.referral().name()).isEqualTo(child);
    assertThat(answer.referral().nameservers())
        .containsExactly(name("ns1.other.com."), name("ns2.other.com."));
  }

  @Test
  void referralUpwardsIsNotFollowed() throws Exception {
    Name host = name("app.sub.example.de.");
    Message message = response(host, Rcode.NOERROR, false);
    message.addRecord(
        new NSRecord(name("de."), DClass.IN, 600, name("a.nic.de.")), Section.AUTHORITY);

    assertThat(AuthoritativeDnsResolver.parse(message, host, ZONE, Type.A, SERVER)).isNull();
  }

  @Test
  void localNamesUseTheSystemResolver() throws Exception {
    AuthoritativeDnsResolver resolver = new AuthoritativeDnsResolver(true, Duration.ofSeconds(2));

    assertThat(resolver.resolve("localhost")).isNotEmpty();
    assertThat(resolver.resolve("127.0.0.1")[0].getHostAddress()).isEqualTo("127.0.0.1");
  }

  /** Needs internet: mvn test -Dtest=AuthoritativeDnsResolverTest -Ddns.live=true */
  @Test
  @EnabledIfSystemProperty(named = "dns.live", matches = "true")
  void liveLookup() throws Exception {
    AuthoritativeDnsResolver resolver = new AuthoritativeDnsResolver(true, Duration.ofSeconds(2));
    for (String host :
        System.getProperty("dns.hosts", "mfa-vertrag.de,www.github.com").split(",")) {
      long start = System.nanoTime();
      InetAddress[] addresses = resolver.resolve(host);
      System.out.printf(
          "%s -> %s (%d ms)%n",
          host,
          java.util.Arrays.stream(addresses).map(InetAddress::getHostAddress).toList(),
          (System.nanoTime() - start) / 1_000_000);
      assertThat(addresses).isNotEmpty();
    }
  }
}
