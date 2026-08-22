#!/usr/bin/env python3
"""Focused contracts for the bounded authenticated-network benchmark."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
SERVER = (ROOT / "server/src/com/openrsc/server/Server.java").read_text()
PROBE = (
    ROOT
    / "server/src/com/openrsc/server/diagnostics/AuthenticatedNetworkBenchmarkProbe.java"
).read_text()
TCP_ENCODER = (
    ROOT / "server/src/com/openrsc/server/net/RSCProtocolEncoder.java"
).read_text()
MAIN_ENCODER = (
    ROOT / "server/src/com/openrsc/server/net/RSCProtocolEncoderMain.java"
).read_text()
WEB_ENCODER = (
    ROOT / "server/src/com/openrsc/server/net/RSCProtocolWebEncoder.java"
).read_text()
CLIENT = (ROOT / "tools/benchmarks/authenticated-network-client.py").read_text()
RUNNER = (ROOT / "tools/benchmarks/benchmark-authenticated-network.sh").read_text()
WIRE_TEST = (
    ROOT / "server/test/com/openrsc/server/net/RSCProtocolEncoderWireTest.java"
).read_text()


class AuthenticatedNetworkBenchmarkContractTest(unittest.TestCase):
    def test_listener_is_explicit_foundation_benchmark_only(self):
        self.assertIn("Authenticated network workload requires foundation benchmark mode", SERVER)
        self.assertIn("AuthenticatedNetworkBenchmarkProbe.isEnabled()", SERVER)
        self.assertIn('pipeline.addLast("authenticated-network-benchmark"', SERVER)
        self.assertIn("networkExpectedClients=", PROBE)
        self.assertIn("authenticatedNetworkInvariant=", PROBE)

    def test_runner_is_loopback_disposable_bounded_and_repeated(self):
        self.assertIn('MYWORLD_NETWORK_BENCHMARK_REPETITIONS:-2', RUNNER)
        self.assertIn('(( REPETITIONS >= 2 ))', RUNNER)
        self.assertIn('sock.bind(("127.0.0.1", 0))', RUNNER)
        self.assertIn('cp "$ROOT_DIR/server/inc/sqlite/myworld_seed.db"', RUNNER)
        self.assertIn('rm -f "$config" "$database"', RUNNER)
        self.assertIn("authenticatedNetworkInvariant=pass", RUNNER)
        self.assertIn('"invariant": "pass"', RUNNER)

    def test_client_uses_real_registration_login_and_gameplay_packets(self):
        self.assertIn("sock.sendall(frame(2, payload))", CLIENT)
        self.assertIn("sock.sendall(frame(0, payload))", CLIENT)
        self.assertIn("frame(67)", CLIENT)
        self.assertIn("frame(187", CLIENT)
        self.assertIn("read_exact(sock, declared - 2)", CLIENT)
        self.assertIn("zlib.crc32(wire", CLIENT)

    def test_backpressure_is_observed_without_changing_normal_channels(self):
        self.assertIn('SLOW_USERNAME = "netbenchslow"', PROBE)
        self.assertIn("new WriteBufferWaterMark(512, 1024)", PROBE)
        self.assertIn("UNWRITABLE_TRANSITIONS", PROBE)
        self.assertIn("WRITABLE_RECOVERIES", PROBE)
        self.assertIn("4.0 if index == 0 else 0.0", CLIENT)

    def test_framing_writes_directly_and_exact_wire_families_are_covered(self):
        self.assertIn("encoder.encode(ctx, message, outBuffer);", TCP_ENCODER)
        self.assertNotIn("Unpooled.buffer", MAIN_ENCODER)
        self.assertIn("ctx.alloc().buffer()", WEB_ENCODER)
        self.assertIn("encoded.release();", WEB_ENCODER)
        self.assertIn("assertTcp((short)-1", WIRE_TEST)
        self.assertIn("assertTcp((short)14", WIRE_TEST)
        self.assertIn("assertTcp((short)93", WIRE_TEST)
        self.assertIn("assertIsaacTcp", WIRE_TEST)
        self.assertIn("assertRaw", WIRE_TEST)
        self.assertIn("assertWeb", WIRE_TEST)


if __name__ == "__main__":
    unittest.main()
