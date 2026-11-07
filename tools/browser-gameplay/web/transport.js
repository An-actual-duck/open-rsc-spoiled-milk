// Application-owned private WebSocket transport; no Tailscale/vendor backend.
export function createTransport(WebSocketType, report, url = 'ws://127.0.0.1:43529/') {
  const connections = new Map(); let next = 1;
  const LIMIT = 32 * 1024 * 1024;
  return {
    connections,
    async open() {
      const id = next++;
      try {
        const socket = new WebSocketType(url, 'binary'); socket.binaryType = 'arraybuffer';
        const entry = {socket, chunks: [], offset: 0, available: 0, waiter: null, closed: false, received: 0, sent: 0}; connections.set(id, entry);
        socket.onmessage = event => {
          const bytes = new Uint8Array(event.data);
          if (entry.available + bytes.length > LIMIT) {
            entry.closed = true; socket.close(1009, 'Private receive buffer limit'); entry.waiter?.(); return;
          }
          entry.chunks.push(bytes); entry.available += bytes.length; entry.received += bytes.length; entry.waiter?.();
        };
        socket.onclose = () => { entry.closed = true; entry.waiter?.(); report('WebSocket closed'); };
        await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject; });
        report('Private WebSocket connected'); return id;
      } catch (error) { connections.delete(id); report('WebSocket failed: ' + error); return -1; }
    },
    available(id) { return connections.get(id)?.available || 0; },
    async read(id, maximum) {
      const entry = connections.get(id); if (!entry) return new Int8Array();
      while (!entry.available && !entry.closed) await new Promise(resolve => { entry.waiter = resolve; });
      entry.waiter = null;
      const count = Math.min(entry.available, maximum), result = new Int8Array(count); let copied = 0;
      while (copied < count) {
        const chunk = entry.chunks[0], amount = Math.min(count - copied, chunk.length - entry.offset);
        result.set(chunk.subarray(entry.offset, entry.offset + amount), copied);
        copied += amount; entry.offset += amount; entry.available -= amount;
        if (entry.offset === chunk.length) { entry.chunks.shift(); entry.offset = 0; }
      }
      return result;
    },
    write(id, bytes) {
      const entry = connections.get(id); if (!entry || entry.closed) throw new Error('Game socket is closed');
      entry.socket.send(bytes); entry.sent += bytes.length;
    },
    close(id) {
      const entry = connections.get(id); if (!entry) return;
      entry.closed = true; entry.waiter?.(); entry.socket.close(); connections.delete(id);
    },
  };
}
