package orsc.net;

import java.io.IOException;

import orsc.buffers.RSBuffer_Bits;
import orsc.util.GenUtil;

class Network_Base {

	private static final int OUTGOING_PACKET_BUFFER_INITIAL_SIZE = 8192;
	private static final int OUTGOING_PACKET_FLUSH_THRESHOLD = OUTGOING_PACKET_BUFFER_INITIAL_SIZE * 4 / 5;
	private static final boolean BOUNDARY_DIAGNOSTICS =
		Boolean.parseBoolean(System.getProperty(
			"spoiledmilk.boundaryDiagnostics",
			System.getenv("SPOILED_MILK_BOUNDARY_DIAGNOSTICS")));
	private static final int MAX_DIAGNOSTIC_FRAMES = 256;
	private static final int PENDING_FRAME_DIAGNOSTIC_INTERVAL = 120;
	public int m_d = 0;
	public RSBuffer_Bits bufferBits;
	String errorCode = "";
	boolean errorHappened = false;
	private int packetReadAttempts = 0;
	private int incomingPacketLength = 0;
	private int incomingPacketBytesRead = 0;
	private int readyPackets = 0;
	private int packetStart = 0;
	private int diagnosticFrameCount = 0;
	private int lastPendingFrameDiagnosticAttempt = 0;

	Network_Base() {
		try {
			this.bufferBits = new RSBuffer_Bits(OUTGOING_PACKET_BUFFER_INITIAL_SIZE);
			this.bufferBits.packetEnd = 3;
		} catch (RuntimeException var2) {
			throw GenUtil.makeThrowable(var2, "b.<init>()");
		}
	}

	public final void flush(int minReady, boolean var2) throws IOException {
		try {
			if (this.errorHappened) {
				this.bufferBits.packetEnd = 3;
				this.packetStart = 0;
				this.errorHappened = false;
				throw new IOException(this.errorCode);
			} else {
				++this.readyPackets;
				if (minReady <= this.readyPackets) {
					if (this.packetStart > 0) {
						this.readyPackets = 0;
						this.send(this.bufferBits.dataBuffer, 0, this.packetStart);
					}

					this.bufferBits.packetEnd = 3;
					this.packetStart = 0;
				}
			}
		} catch (RuntimeException var4) {
			throw GenUtil.makeThrowable(var4, "b.R(" + minReady + ',' + true + ')');
		}
	}

	public final void finishPacketAndFlush() throws IOException {
		try {
			this.finishPacket();
			this.flush(0, true);
		} catch (RuntimeException var3) {
			throw GenUtil.makeThrowable(var3, "b.P(" + -6924 + ')');
		}
	}

	int read() throws IOException {
		try {
			return 0;
		} catch (RuntimeException var3) {
			throw GenUtil.makeThrowable(var3, "b.A(" + true + ')');
		}
	}

	void send(byte[] data, int offset, int count) throws IOException {
		try {
		} catch (RuntimeException var6) {
			throw GenUtil.makeThrowable(var6,
				"b.D(" + (data != null ? "{...}" : "null") + ',' + offset + ',' + count + ',' + "dummy" + ')');
		}
	}

	public final int decodeIncomingOpcode(int opcode) {
		try {
			return opcode;
		} catch (RuntimeException var4) {
			throw GenUtil.makeThrowable(var4, "b.K(" + "dummy" + ',' + opcode + ')');
		}
	}

	private int readIncomingPacketPayload(RSBuffer_Bits data) {
		try {
			try {
				++this.packetReadAttempts;
				if (this.m_d > 0 && this.packetReadAttempts > this.m_d) {
					logFrameDiagnostic(
						"NETWORK_FRAME_TIMEOUT attempts="
							+ this.packetReadAttempts
							+ " received=" + this.incomingPacketBytesRead
							+ "/" + this.incomingPacketLength
							+ " available=" + this.available());
					this.errorHappened = true;
					this.errorCode = "time-out";
					this.m_d += this.m_d;
					return 0;
				}

				if (this.incomingPacketLength == 0 && this.available() >= 2) {
					final int declaredFrameLength =
						((read() & 0xff) << 8) | (read() & 0xff);
					if (declaredFrameLength < 3) {
						this.errorHappened = true;
						this.errorCode = "invalid-frame-length";
						return 0;
					}
					this.incomingPacketLength = declaredFrameLength - 2;
					this.incomingPacketBytesRead = 0;
					this.packetReadAttempts = 0;
					logFrameDiagnostic(
						"NETWORK_FRAME_HEADER declaredBytes="
							+ declaredFrameLength
							+ " pendingBytes=" + this.incomingPacketLength
							+ " available=" + this.available());
				}
				/*
				 * Consume any bytes already offered by the socket even when a
				 * complete frame has not arrived. Waiting for the whole payload
				 * in InputStream.available() can deadlock a large ordered burst:
				 * the unread partial frame holds the TCP receive window closed,
				 * while the sender needs that window to finish the same frame.
				 * Gameplay still sees the packet only after full assembly.
				 */
				if (this.incomingPacketLength > 0) {
					data.ensureCapacity(this.incomingPacketLength);
					final int remaining = this.incomingPacketLength
						- this.incomingPacketBytesRead;
					final int available = this.available();
					final int readableNow = Math.min(remaining, available);
					if (readableNow > 0) {
						this.read(
							data.dataBuffer,
							this.incomingPacketBytesRead,
							readableNow);
						this.incomingPacketBytesRead += readableNow;
						this.packetReadAttempts = 0;
						logFrameDiagnostic(
							"NETWORK_FRAME_PROGRESS read=" + readableNow
								+ " received="
								+ this.incomingPacketBytesRead + "/"
								+ this.incomingPacketLength
								+ " available=" + this.available());
					}
				}
				if (this.incomingPacketLength > 0
					&& this.incomingPacketBytesRead
						== this.incomingPacketLength) {
					int packetLength = this.incomingPacketLength;
					logFrameDiagnostic(
						"NETWORK_FRAME_COMPLETE opcode="
							+ (data.dataBuffer[0] & 0xFF)
							+ " bytes=" + packetLength
							+ " available=" + this.available());
					this.packetReadAttempts = 0;
					this.lastPendingFrameDiagnosticAttempt = 0;
					this.incomingPacketLength = 0;
					this.incomingPacketBytesRead = 0;
					return packetLength;
				}
				if (this.incomingPacketLength > 0
					&& this.packetReadAttempts
						- this.lastPendingFrameDiagnosticAttempt
						>= PENDING_FRAME_DIAGNOSTIC_INTERVAL) {
					this.lastPendingFrameDiagnosticAttempt =
						this.packetReadAttempts;
					logFrameDiagnostic(
						"NETWORK_FRAME_PENDING attempts="
							+ this.packetReadAttempts
							+ " received=" + this.incomingPacketBytesRead
							+ "/" + this.incomingPacketLength
							+ " available=" + this.available());
				}
			} catch (IOException var4) {
				this.errorHappened = true;
				this.errorCode = var4.getMessage();
			}

			return 0;
		} catch (RuntimeException var5) {
			throw GenUtil.makeThrowable(var5, "b.O(" + (data != null ? "{...}" : "null") + ',' + "dummy" + ')');
		}
	}

	private void logFrameDiagnostic(final String message) {
		if (!BOUNDARY_DIAGNOSTICS
			|| this.diagnosticFrameCount >= MAX_DIAGNOSTIC_FRAMES) {
			return;
		}
		this.diagnosticFrameCount++;
		System.out.println(message);
	}

	int available() throws IOException {
		try {
			return 0;
		} catch (RuntimeException var3) {
			throw GenUtil.makeThrowable(var3, "b.B(" + -124 + ')');
		}
	}

	private void read(byte[] data, int count) throws IOException {
		try {
			this.read(data, 0, count);
		} catch (RuntimeException var5) {
			throw GenUtil.makeThrowable(var5,
				"b.M(" + (data != null ? "{...}" : "null") + ',' + "dummy" + ',' + count + ')');
		}
	}

	public final boolean hasFinishedPackets() {
		try {
			return this.packetStart > 0;
		} catch (RuntimeException var3) {
			throw GenUtil.makeThrowable(var3, "b.S(" + "dummy" + ')');
		}
	}

	public final void newPacket(int opcode) {
		try {
			// (4/5) of write buffer is filled; flush
			if (OUTGOING_PACKET_FLUSH_THRESHOLD < this.packetStart) {
				try {
					this.flush(0, true);
				} catch (IOException var4) {
					this.errorHappened = true;
					this.errorCode = var4.getMessage();
				}
			}

			this.bufferBits.packetEnd = this.packetStart + 2;
			this.bufferBits.putByte(opcode);
		} catch (RuntimeException var5) {
			throw GenUtil.makeThrowable(var5, "b.N(" + opcode + ',' + "dummy" + ')');
		}
	}

	public final int readIncomingPacket(RSBuffer_Bits data) {
		try {
			data.packetEnd = 0;
			return this.readIncomingPacketPayload(data);
		} catch (RuntimeException var4) {
			throw GenUtil.makeThrowable(var4, "b.Q(" + "dummy" + ',' + "{...}" + ')');
		}
	}

	void close() {
		try {
		} catch (RuntimeException var3) {
			throw GenUtil.makeThrowable(var3, "b.G(" + true + ')');
		}
	}

	void read(byte[] data, int offset, int count) throws IOException {
		try {
		} catch (RuntimeException var6) {
			throw GenUtil.makeThrowable(var6,
				"b.F(" + (data != null ? "{...}" : "null") + ',' + count + ',' + offset + ',' + "dummy" + ')');
		}
	}

	public final void finishPacket() {
		try {
			int packetLen = this.bufferBits.packetEnd - this.packetStart - 2;

			this.bufferBits.dataBuffer[this.packetStart] = (byte) (packetLen >> 8);
			this.bufferBits.dataBuffer[this.packetStart + 1] = (byte) packetLen;


			this.packetStart = this.bufferBits.packetEnd;
		} catch (RuntimeException var4) {
			throw GenUtil.makeThrowable(var4, "b.L(" + "dummy" + ')');
		}
	}
}
