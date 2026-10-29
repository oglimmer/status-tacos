/* Copyright (c) 2025 by oglimmer.com / Oliver Zimpasser. All rights reserved. */
package de.oglimmer.status_tacos.service;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.xbill.DNS.AAAARecord;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.CNAMERecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Message;
import org.xbill.DNS.NSRecord;
import org.xbill.DNS.Name;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.Section;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

/**
 * Finds the IP of a monitored host at the nameservers of its domain, not at the DNS server of this
 * machine.
 *
 * <p>A DNS server keeps an answer for its TTL. After a domain moves to a new server, the DNS server
 * of this machine can still give the old IP for hours (the TTL of the old record), and the check
 * then measures the old server. The nameservers of the domain always know the current IP.
 *
 * <p>Only the nameservers of the top-level domain come from the DNS server of this machine. Hosts
 * that have no public zone (localhost, IP literals, cluster-internal names) use the DNS server of
 * this machine.
 */
@Component
@Slf4j
public class AuthoritativeDnsResolver implements DnsResolver {

  private static final Pattern IPV4_LITERAL = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
  private static final int MAX_CNAME_DEPTH = 8;
  private static final int MAX_SERVERS = 3;
  private static final int MAX_REFERRALS = 8;

  private final boolean enabled;
  private final Duration timeout;

  public AuthoritativeDnsResolver(
      @Value("${monitor.http.dns.authoritative:true}") boolean enabled,
      @Value("${monitor.http.dns.timeout:2s}") Duration timeout) {
    this.enabled = enabled;
    this.timeout = timeout;
  }

  @Override
  public InetAddress[] resolve(String host) throws UnknownHostException {
    if (!enabled || isLiteralOrLocal(host)) {
      return SystemDefaultDnsResolver.INSTANCE.resolve(host);
    }
    return resolveAtZone(host, 0);
  }

  @Override
  public String resolveCanonicalHostname(String host) {
    return host;
  }

  private InetAddress[] resolveAtZone(String host, int depth) throws UnknownHostException {
    if (depth > MAX_CNAME_DEPTH) {
      throw new UnknownHostException(host + ": too many CNAMEs");
    }
    Name name;
    try {
      name = Name.fromString(host, Name.root);
    } catch (TextParseException e) {
      throw new UnknownHostException(host + ": " + e.getMessage());
    }
    Zone zone = findZone(name);
    if (zone == null) {
      // No public zone, for example a name of the cluster or of /etc/hosts
      return SystemDefaultDnsResolver.INSTANCE.resolve(host);
    }

    for (int hop = 0; hop <= MAX_REFERRALS; hop++) {
      Answer a = askZone(name, zone, Type.A);
      if (a.referral() != null) {
        // A part of the zone has its own nameservers (a delegated subdomain)
        zone = a.referral();
        continue;
      }
      Answer aaaa;
      try {
        aaaa = askZone(name, zone, Type.AAAA);
      } catch (UnknownHostException e) {
        // The A query of this zone worked: a failed AAAA query is no reason for DOWN
        aaaa = null;
      }
      List<InetAddress> result = new ArrayList<>(a.addresses());
      if (aaaa != null) {
        result.addAll(aaaa.addresses());
      }
      if (!result.isEmpty()) {
        return result.toArray(InetAddress[]::new);
      }
      // The CNAME points into another zone: ask the nameservers of that zone
      if (a.cnameTarget() != null) {
        return resolveAtZone(a.cnameTarget().toString(true), depth + 1);
      }
      if (aaaa != null && aaaa.cnameTarget() != null) {
        return resolveAtZone(aaaa.cnameTarget().toString(true), depth + 1);
      }
      throw new UnknownHostException(
          host + ": no A or AAAA record in zone " + zone.name().toString(true));
    }
    throw new UnknownHostException(host + ": too many referrals");
  }

  /** A zone and its nameservers. */
  record Zone(Name name, List<Name> nameservers) {}

  /**
   * Asks the nameservers of the zone until one gives a usable answer. A final answer (NXDOMAIN) is
   * thrown at once.
   */
  private Answer askZone(Name name, Zone zone, int type) throws UnknownHostException {
    String host = name.toString(true);
    UnknownHostException lastError = null;
    int tried = 0;
    for (Name nameserver : zone.nameservers()) {
      for (InetAddress serverAddress : addressesOf(nameserver)) {
        if (tried++ >= MAX_SERVERS) {
          throw lastError != null
              ? lastError
              : new UnknownHostException(host + ": no nameserver of the domain answered");
        }
        try {
          Answer answer = query(serverAddress, name, zone.name(), type);
          if (answer != null) {
            return answer;
          }
          lastError =
              new UnknownHostException(
                  host + ": no valid answer from " + nameserver.toString(true));
        } catch (UnknownHostException e) {
          // A final answer of the domain, not a failed server
          throw e;
        } catch (IOException e) {
          lastError =
              new UnknownHostException(
                  host
                      + ": nameserver "
                      + nameserver.toString(true)
                      + " failed: "
                      + e.getMessage());
        }
      }
    }
    throw lastError != null
        ? lastError
        : new UnknownHostException(host + ": the nameservers of the domain have no IP");
  }

  /**
   * The top-level domain of the name, with its NS records. The questions start there and follow the
   * referrals down to the zone of the name, as a resolver without cache does. The NS records of the
   * domain itself are not taken from the DNS server of this machine: after a move to a new DNS
   * provider it can keep the old nameservers for a day, and they still give the old IP.
   */
  private Zone findZone(Name name) {
    if (name.labels() < 3) {
      return null;
    }
    Name tld = new Name(name, name.labels() - 2);
    Lookup lookup = new Lookup(tld, Type.NS);
    // No shared cache: the DNS server of this machine already caches
    lookup.setCache(null);
    Record[] records = lookup.run();
    if (records == null) {
      return null;
    }
    List<Name> nameservers = new ArrayList<>();
    for (Record record : records) {
      if (record instanceof NSRecord ns && ns.getName().equals(tld)) {
        nameservers.add(ns.getTarget());
      }
    }
    return nameservers.isEmpty() ? null : new Zone(tld, nameservers);
  }

  /** IPv4 first: a container often has no IPv6 route. */
  private static List<InetAddress> addressesOf(Name nameserver) {
    try {
      List<InetAddress> addresses =
          new ArrayList<>(List.of(InetAddress.getAllByName(nameserver.toString(true))));
      addresses.sort(Comparator.comparing(address -> address instanceof Inet4Address ? 0 : 1));
      return addresses;
    } catch (UnknownHostException e) {
      log.debug("Nameserver {} has no IP: {}", nameserver, e.getMessage());
      return List.of();
    }
  }

  /**
   * @return null if the server gave no usable answer (server error, not authoritative and no
   *     referral)
   */
  private Answer query(InetAddress server, Name name, Name zone, int type)
      throws IOException, UnknownHostException {
    Resolver resolver = new SimpleResolver(server);
    resolver.setTimeout(timeout);
    Message query = Message.newQuery(Record.newRecord(name, type, DClass.IN));
    query.getHeader().unsetFlag(Flags.RD);
    Message response = resolver.send(query);
    return parse(response, name, zone, type, server);
  }

  /**
   * The addresses, or the name to ask next (CNAME into another zone), or the nameservers of a
   * delegated subdomain (referral).
   */
  record Answer(List<InetAddress> addresses, Name cnameTarget, Zone referral) {}

  /**
   * Follows the CNAMEs in the answer section. If the chain leaves the zone, the addresses are empty
   * and cnameTarget is the name to ask next.
   */
  static Answer parse(Message response, Name name, Name zone, int type, InetAddress server)
      throws UnknownHostException {
    int rcode = response.getRcode();
    if (rcode == Rcode.NXDOMAIN) {
      throw new UnknownHostException(
          name.toString(true) + ": does not exist (NXDOMAIN from " + server.getHostAddress() + ")");
    }
    if (rcode != Rcode.NOERROR) {
      return null;
    }
    if (!response.getHeader().getFlag(Flags.AA)) {
      Zone referral = referral(response, name, zone);
      return referral != null ? new Answer(List.of(), null, referral) : null;
    }
    List<Record> answers = response.getSection(Section.ANSWER);
    Name current = name;
    for (int i = 0; i <= MAX_CNAME_DEPTH; i++) {
      List<InetAddress> addresses = new ArrayList<>();
      Name next = null;
      for (Record record : answers) {
        if (!record.getName().equals(current)) {
          continue;
        }
        if (type == Type.A && record instanceof ARecord a) {
          addresses.add(withHostName(name, a.getAddress()));
        } else if (type == Type.AAAA && record instanceof AAAARecord aaaa) {
          addresses.add(withHostName(name, aaaa.getAddress()));
        } else if (record instanceof CNAMERecord cname) {
          next = cname.getTarget();
        }
      }
      if (!addresses.isEmpty()) {
        return new Answer(addresses, null, null);
      }
      if (next == null) {
        return new Answer(List.of(), current.equals(name) ? null : current, null);
      }
      current = next;
    }
    throw new UnknownHostException(name.toString(true) + ": too many CNAMEs");
  }

  /**
   * The NS records in the authority section, if they are for a subdomain of the zone that holds the
   * name. Only a deeper zone counts, so a referral can not go up or sideways.
   */
  private static Zone referral(Message response, Name name, Name zone) {
    Name child = null;
    List<Name> nameservers = new ArrayList<>();
    for (Record record : response.getSection(Section.AUTHORITY)) {
      if (record instanceof NSRecord ns
          && name.subdomain(ns.getName())
          && ns.getName().subdomain(zone)
          && !ns.getName().equals(zone)
          && (child == null || child.equals(ns.getName()))) {
        child = ns.getName();
        nameservers.add(ns.getTarget());
      }
    }
    return child != null ? new Zone(child, nameservers) : null;
  }

  /** The host name is kept, as with the system resolver. */
  private static InetAddress withHostName(Name name, InetAddress address)
      throws UnknownHostException {
    return InetAddress.getByAddress(name.toString(true), address.getAddress());
  }

  private static boolean isLiteralOrLocal(String host) {
    return host.indexOf('.') < 0
        || host.indexOf(':') >= 0
        || IPV4_LITERAL.matcher(host).matches()
        || host.endsWith(".local")
        || host.endsWith(".localhost")
        || host.endsWith(".internal");
  }
}
