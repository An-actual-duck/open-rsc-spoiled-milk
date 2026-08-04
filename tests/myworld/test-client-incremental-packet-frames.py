#!/usr/bin/env python3
"""Regression coverage for receive-window-safe client packet framing."""

from pathlib import Path
import subprocess
import tempfile
import textwrap
import unittest


ROOT = Path(__file__).resolve().parents[2]
NETWORK_BASE = ROOT / "Client_Base/src/orsc/net/Network_Base.java"


class ClientIncrementalPacketFramesTest(unittest.TestCase):
    def test_partial_payload_is_consumed_before_the_frame_is_complete(self):
        buffer_stub = textwrap.dedent(
            """
            package orsc.buffers;

            public final class RSBuffer_Bits {
                public byte[] dataBuffer;
                public int packetEnd;

                public RSBuffer_Bits(int size) {
                    dataBuffer = new byte[size];
                }

                public void ensureCapacity(int size) {
                    if (dataBuffer.length >= size) {
                        return;
                    }
                    byte[] expanded = new byte[size];
                    System.arraycopy(
                        dataBuffer, 0, expanded, 0, dataBuffer.length);
                    dataBuffer = expanded;
                }

                public void putByte(int value) {
                    dataBuffer[packetEnd++] = (byte) value;
                }
            }
            """
        )
        gen_util_stub = textwrap.dedent(
            """
            package orsc.util;

            public final class GenUtil {
                public static RuntimeException makeThrowable(
                        RuntimeException cause, String context) {
                    return cause;
                }
            }
            """
        )
        harness = textwrap.dedent(
            """
            package orsc.net;

            import java.io.IOException;
            import orsc.buffers.RSBuffer_Bits;

            public final class NetworkBaseIncrementalHarness {
                public static void main(String[] arguments) {
                    byte[] wire = new byte[] {
                        0, 9, (byte) 143, 1, 2, 3, 4, 5, 6,
                        0, 4, (byte) 51, 99
                    };
                    ScriptedNetwork network = new ScriptedNetwork(wire);
                    network.m_d = 2;
                    RSBuffer_Bits packet = new RSBuffer_Bits(2);

                    network.deliverThrough(5);
                    require(network.readIncomingPacket(packet) == 0,
                        "partial frame is not delivered early");
                    require(network.position() == 5,
                        "available partial payload is consumed");
                    require(network.readIncomingPacket(packet) == 0,
                        "an empty poll keeps the partial frame pending");

                    network.deliverThrough(7);
                    require(network.readIncomingPacket(packet) == 0,
                        "a second partial chunk remains pending");
                    require(network.position() == 7,
                        "second partial chunk is consumed");
                    require(network.readIncomingPacket(packet) == 0,
                        "progress reset prevents a premature watchdog trip");

                    network.deliverThrough(9);
                    require(network.readIncomingPacket(packet) == 7,
                        "complete first frame is delivered once");
                    require((packet.dataBuffer[0] & 255) == 143,
                        "first frame opcode survives incremental assembly");
                    for (int index = 1; index < 7; index++) {
                        require((packet.dataBuffer[index] & 255) == index,
                            "first frame payload byte " + index);
                    }

                    network.deliverThrough(wire.length);
                    require(network.readIncomingPacket(packet) == 2,
                        "following frame remains aligned");
                    require((packet.dataBuffer[0] & 255) == 51,
                        "following frame opcode");
                    require((packet.dataBuffer[1] & 255) == 99,
                        "following frame payload");
                }

                private static void require(
                        boolean condition, String label) {
                    if (!condition) {
                        throw new AssertionError(label);
                    }
                }

                private static final class ScriptedNetwork
                        extends Network_Base {
                    private final byte[] wire;
                    private int delivered;
                    private int position;

                    private ScriptedNetwork(byte[] wire) {
                        this.wire = wire;
                    }

                    private void deliverThrough(int end) {
                        delivered = Math.max(delivered, end);
                    }

                    private int position() {
                        return position;
                    }

                    @Override
                    int available() {
                        return delivered - position;
                    }

                    @Override
                    int read() {
                        return wire[position++] & 255;
                    }

                    @Override
                    void read(byte[] data, int offset, int count)
                            throws IOException {
                        if (count > available()) {
                            throw new IOException("read exceeds delivery");
                        }
                        System.arraycopy(
                            wire, position, data, offset, count);
                        position += count;
                    }
                }
            }
            """
        )

        with tempfile.TemporaryDirectory() as temporary:
            work = Path(temporary)
            files = {
                work / "orsc/buffers/RSBuffer_Bits.java": buffer_stub,
                work / "orsc/util/GenUtil.java": gen_util_stub,
                work / "orsc/net/NetworkBaseIncrementalHarness.java": harness,
            }
            for path, contents in files.items():
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(contents, encoding="utf-8")
            subprocess.run(
                [
                    "javac",
                    "-Xlint:all",
                    "-source",
                    "8",
                    "-target",
                    "8",
                    "-d",
                    str(work),
                    str(NETWORK_BASE),
                    *(str(path) for path in files),
                ],
                cwd=ROOT,
                check=True,
            )
            subprocess.run(
                [
                    "java",
                    "-cp",
                    str(work),
                    "orsc.net.NetworkBaseIncrementalHarness",
                ],
                cwd=ROOT,
                check=True,
            )

    def test_reader_no_longer_waits_for_the_whole_payload(self):
        source = NETWORK_BASE.read_text(encoding="utf-8")
        self.assertNotIn(
            "this.available() >= this.incomingPacketLength", source
        )
        self.assertIn("incomingPacketBytesRead", source)
        self.assertIn("Math.min(remaining, available)", source)


if __name__ == "__main__":
    unittest.main()
