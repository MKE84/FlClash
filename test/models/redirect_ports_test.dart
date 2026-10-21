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

    test('the redirect never shares a port with another inbound', () {
      final config = PatchClashConfig(
        mixedPort: defaultRedirPort,
        dns: const Dns().copyWith(listen: ''),
      );

      expect(redirectingPorts(config).redirPort, isNot(defaultRedirPort));
      expect(
        redirectingRedirPort(defaultRedirPort, busy: [defaultRedirPort]),
        defaultRedirPort + 1,
      );
      expect(
        redirectingRedirPort(0, busy: [defaultRedirPort, defaultRedirPort + 1]),
        defaultRedirPort + 2,
      );
      expect(redirectingRedirPort(7893), 7893);
    });

    test('a run that does not redirect keeps the redir listener closed', () {
      const config = PatchClashConfig(redirPort: 7895);

      final applied = appliedPorts(config, redirecting: false);

      expect(applied.redirPort, 0);
      expect(applied.dns.listen, config.dns.listen);
    });

    test('only a redirecting run derives a redir port', () {
      const config = PatchClashConfig();

      expect(
        appliedPorts(config, redirecting: true).redirPort,
        defaultRedirPort,
      );
      expect(appliedPorts(config, redirecting: false).redirPort, 0);
    });

    test('the redirect also steps over the DNS port it points at', () {
      const config = PatchClashConfig(redirPort: 1053, dns: Dns(listen: ''));

      final redirecting = redirectingPorts(config);

      expect(redirecting.redirPort, isNot(1053));
      expect(redirecting.dns.listen, redirectDnsListen);
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
