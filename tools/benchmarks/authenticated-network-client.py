#!/usr/bin/env python3
"""Bounded custom-client traffic over real loopback TCP sockets."""

from __future__ import annotations

import argparse
import json
import socket
import struct
import threading
import time
import zlib
from dataclasses import dataclass, field


CLIENT_VERSION = 10052
PASSWORD = "benchmarkpass"


def frame(opcode: int, payload: bytes = b"") -> bytes:
    body = bytes((opcode,)) + payload
    if len(body) > 65535:
        raise ValueError("benchmark frame exceeds custom-client limit")
    return struct.pack(">H", len(body)) + body


def line(value: str) -> bytes:
    return value.encode("ascii") + b"\n"


def read_exact(sock: socket.socket, count: int) -> bytes:
    chunks: list[bytes] = []
    remaining = count
    while remaining:
        chunk = sock.recv(remaining)
        if not chunk:
            raise EOFError(f"socket closed with {remaining} bytes pending")
        chunks.append(chunk)
        remaining -= len(chunk)
    return b"".join(chunks)


def register(host: str, port: int, username: str) -> None:
    with socket.create_connection((host, port), timeout=5.0) as sock:
        sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        payload = line(username) + line(PASSWORD) + line(f"{username}@example.com")
        sock.sendall(frame(2, payload))
        response = read_exact(sock, 1)[0]
        if response != 0:
            raise RuntimeError(f"registration failed for {username}: {response}")


@dataclass
class Client:
    host: str
    port: int
    username: str
    slow_seconds: float = 0.0
    sock: socket.socket | None = None
    frames: int = 0
    received_bytes: int = 0
    ordered_crc32: int = 0
    opcode_crc32: int = 0
    sent_packets: int = 0
    errors: list[str] = field(default_factory=list)
    _lock: threading.Lock = field(default_factory=threading.Lock)
    _stop: threading.Event = field(default_factory=threading.Event)

    def connect(self) -> None:
        sock = socket.create_connection((self.host, self.port), timeout=5.0)
        sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF, 1024)
        payload = (
            b"\x00"
            + struct.pack(">I", CLIENT_VERSION)
            + line(self.username)
            + b"\x00"
            + line(PASSWORD)
            + struct.pack(">q", 0)
        )
        sock.sendall(frame(0, payload))
        response = read_exact(sock, 1)[0]
        if response & 0x40 == 0:
            sock.close()
            raise RuntimeError(f"login failed for {self.username}: {response}")
        # A blocking exact-frame reader is intentional. Socket timeouts after a
        # partial body would discard already-consumed bytes and manufacture a
        # false framing failure; close() wakes the reader at benchmark end.
        sock.settimeout(None)
        self.sock = sock

    def run(self) -> None:
        try:
            self.connect()
            sender = threading.Thread(target=self._send_loop, daemon=True)
            sender.start()
            if self.slow_seconds:
                time.sleep(self.slow_seconds)
            self._read_loop()
        except (EOFError, OSError) as exc:
            if not self._stop.is_set() and self.frames == 0:
                self.errors.append(str(exc))
        except Exception as exc:  # benchmark must surface protocol failures
            self.errors.append(str(exc))
        finally:
            self.close()

    def _send_loop(self) -> None:
        toggle = False
        while not self._stop.wait(0.2):
            sock = self.sock
            if sock is None:
                return
            try:
                # One heartbeat and one valid single-step walk form a bounded
                # mix of no-payload and parsed gameplay traffic.
                x = 121 if toggle else 120
                toggle = not toggle
                sock.sendall(frame(67) + frame(187, struct.pack(">HH", x, 648)))
                with self._lock:
                    self.sent_packets += 2
            except OSError as exc:
                if not self._stop.is_set() and self.frames == 0:
                    self.errors.append(str(exc))
                return

    def _read_loop(self) -> None:
        sock = self.sock
        assert sock is not None
        while not self._stop.is_set():
            header = read_exact(sock, 2)
            declared = struct.unpack(">H", header)[0]
            if declared < 3:
                raise RuntimeError(f"invalid server frame length {declared}")
            body = read_exact(sock, declared - 2)
            opcode = body[0]
            wire = header + body
            with self._lock:
                self.frames += 1
                self.received_bytes += len(wire)
                self.ordered_crc32 = zlib.crc32(wire, self.ordered_crc32)
                self.opcode_crc32 = zlib.crc32(bytes((opcode,)), self.opcode_crc32)

    def close(self) -> None:
        self._stop.set()
        sock, self.sock = self.sock, None
        if sock is not None:
            try:
                sock.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
            sock.close()

    def snapshot(self) -> dict[str, object]:
        with self._lock:
            return {
                "username": self.username,
                "frames": self.frames,
                "bytes": self.received_bytes,
                "ordered_crc32": f"{self.ordered_crc32 & 0xFFFFFFFF:08x}",
                "opcode_crc32": f"{self.opcode_crc32 & 0xFFFFFFFF:08x}",
                "sent_packets": self.sent_packets,
                "errors": list(self.errors),
            }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--clients", type=int, default=8)
    parser.add_argument("--seconds", type=float, default=35.0)
    args = parser.parse_args()
    if args.clients < 2:
        parser.error("at least two clients are required")

    usernames = ["netbenchslow"] + [f"netbench{i}" for i in range(args.clients - 1)]
    for username in usernames:
        register(args.host, args.port, username)

    clients = [
        Client(args.host, args.port, username, 4.0 if index == 0 else 0.0)
        for index, username in enumerate(usernames)
    ]
    threads = [threading.Thread(target=client.run, daemon=True) for client in clients]
    for thread in threads:
        thread.start()

    deadline = time.monotonic() + args.seconds
    while time.monotonic() < deadline:
        if any(client.errors for client in clients):
            break
        time.sleep(0.1)
    for client in clients:
        client.close()
    for thread in threads:
        thread.join(timeout=2.0)

    snapshots = [client.snapshot() for client in clients]
    failures = [
        snap for snap in snapshots
        if snap["errors"] or snap["frames"] == 0 or snap["sent_packets"] == 0
    ]
    summary = {
        "clients": len(clients),
        "authenticated": len(clients) - len(failures),
        "frames": sum(int(snap["frames"]) for snap in snapshots),
        "bytes": sum(int(snap["bytes"]) for snap in snapshots),
        "sent_packets": sum(int(snap["sent_packets"]) for snap in snapshots),
        "slow_reader_recovered": snapshots[0]["frames"] > 0 and not snapshots[0]["errors"],
        "invariant": "pass" if not failures else "fail",
        "per_client": snapshots,
    }
    print("AUTHENTICATED_NETWORK_CLIENT " + json.dumps(summary, sort_keys=True))
    return 0 if not failures else 1


if __name__ == "__main__":
    raise SystemExit(main())
