#!/usr/bin/env node
import assert from 'node:assert/strict';
import {createTransport} from '../../tools/browser-gameplay/web/transport.js';

class FakeSocket {
  static current;
  constructor(url, protocol) {
    assert.equal(url, 'ws://127.0.0.1:43529/'); assert.equal(protocol, 'binary');
    FakeSocket.current = this; this.sent = [];
    queueMicrotask(() => this.onopen());
  }
  send(bytes) { this.sent.push(Int8Array.from(bytes)); }
  close() { queueMicrotask(() => this.onclose?.()); }
  receive(bytes) { this.onmessage({data: Uint8Array.from(bytes).buffer}); }
}
const transport = createTransport(FakeSocket, () => {});
const id = await transport.open(), socket = FakeSocket.current;
assert.equal(transport.connected(id),true);
const pending = transport.read(id, 7);
socket.receive([0, 128, 255]);
assert.deepEqual([...await pending], [0, -128, -1]);
socket.receive([1, 2, 3]); socket.receive([4, 5]);
assert.equal(transport.available(id), 5);
assert.deepEqual([...await transport.read(id, 4)], [1, 2, 3, 4]);
assert.equal(transport.available(id), 1);
assert.deepEqual([...await transport.read(id, 4)], [5]);
// Larger than common JS function-argument limits; queueing must not use spread.
const large = Uint8Array.from({length: 1024 * 1024}, (_, i) => i & 255);
socket.receive(large);
let offset = 0;
while (transport.available(id)) {
  const bytes = await transport.read(id, 7777);
  assert.deepEqual(new Uint8Array(bytes.buffer), large.subarray(offset, offset + bytes.length)); offset += bytes.length;
}
assert.equal(offset, large.length);
transport.write(id, new Int8Array([0, -128, -1]));
assert.deepEqual([...socket.sent[0]], [0, -128, -1]);
const eof = transport.read(id, 8); transport.close(id);
assert.equal(transport.connected(id),false);
assert.equal((await eof).length, 0); assert.equal(transport.available(id), 0);
assert.equal(transport.connections.size, 0);
const overflow = await transport.open();
FakeSocket.current.receive(new Uint8Array(33 * 1024 * 1024));
assert.equal(transport.connections.get(overflow).closed, true);
assert.equal(transport.available(overflow), 0);
assert.equal(transport.connected(overflow),false);
transport.close(overflow);
const remote=await transport.open();FakeSocket.current.receive([1,2,3]);FakeSocket.current.onclose();
assert.equal(transport.connected(remote),false);assert.equal(transport.available(remote),3,'final controlled messages remain readable after close');
assert.deepEqual([...await transport.read(remote,3)],[1,2,3]);assert.equal(transport.available(remote),0);transport.close(remote);
const disposing = await transport.open();
const waiting = transport.read(disposing, 10);
transport.dispose(); transport.dispose();
assert.equal((await waiting).length, 0);
assert.equal(transport.connections.size, 0);
assert.equal(transport.connected(disposing),false);
assert.equal(await transport.open(), -1);
assert.throws(() => transport.write(disposing, new Int8Array([1])), /closed/);
console.log('PASS private browser transport: fragmented frames, partial reads, 1 MiB frame, signed bytes, EOF, receive limit and irreversible disposal');
