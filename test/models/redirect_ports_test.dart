import 'package:fl_clash/enum/enum.dart';
import 'package:fl_clash/models/models.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('the root redirect ports', () {
    test('a run without a saved redir port gets the default one', () {
      const config = PatchClashConfig();

      expect(config.redirPort, 0);
      expect(redirectingPorts(config).redirPort, defaultRedirPort);
      expect(redirectingRedirPort(defaultRedirPort + 1), defaultRedirPort + 1);
    });

    test('a DNS address the core cannot bind moves to loopback', () {
      for (final listen in ['', '0.0.0.0:53', '127.0.0.1:53', '::53']) {
        final redirecting = redirectingPorts(
          PatchClashConfig(dns: const Dns().copyWith(listen: listen)),
        );

        expect(redirecting.dns.listen, redirectDnsListen, reason: listen);
        expect(redirecting.dns.enable, isTrue, reason: listen);
        expect(
          redirecting.dnsOverrideKeys,
          containsAll([DnsOverrideKey.enable, DnsOverrideKey.listen]),
          reason: listen,
        );
        expect(redirectingDnsListen(listen), redirectDnsListen, reason: listen);
      }
    });

    test(
      'a loopback DNS address on an unprivileged port stays where it is',
      () {
        const listen = '127.0.0.1:5353';
        final redirecting = redirectingPorts(
          const PatchClashConfig(dns: Dns(listen: listen)),
        );

        expect(redirecting.dns.listen, listen);
        expect(redirecting.dns.enable, const Dns().enable);
        expect(
          redirecting.dnsOverrideKeys,
          const PatchClashConfig().dnsOverrideKeys,
        );
        expect(redirectingDnsListen(listen), listen);
      },
    );
  });
}
