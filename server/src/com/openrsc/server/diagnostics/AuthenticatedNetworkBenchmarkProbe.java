package com.openrsc.server.diagnostics;

import com.openrsc.server.net.ConnectionAttachment;
import com.openrsc.server.net.RSCConnectionHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.util.AttributeKey;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Explicitly enabled observability for the authenticated loopback benchmark.
 * It is absent from ordinary channel pipelines and never records credentials,
 * addresses, or packet payloads. The benchmark client verifies complete frame
 * ordering and computes payload CRCs without adding work to the server loop.
 */
public final class AuthenticatedNetworkBenchmarkProbe
		extends ChannelDuplexHandler {
	private static final AttributeKey<ChannelState> STATE =
		AttributeKey.valueOf("authenticated-network-benchmark-state");
	private static final String CLIENTS_PROPERTY =
		"openrsc.benchmarkAuthenticatedNetworkClients";
	private static final String SLOW_USERNAME = "netbenchslow";
	private static final AtomicLong CHANNELS = new AtomicLong();
	private static final AtomicLong INBOUND_BYTES = new AtomicLong();
	private static final AtomicLong OUTBOUND_WRITES = new AtomicLong();
	private static final AtomicLong OUTBOUND_BYTES = new AtomicLong();
	private static final AtomicLong SERIALIZATION_CALLS = new AtomicLong();
	private static final AtomicLong SERIALIZATION_NANOS = new AtomicLong();
	private static final AtomicLong SERIALIZATION_BYTES = new AtomicLong();
	private static final AtomicLong UNWRITABLE_TRANSITIONS = new AtomicLong();
	private static final AtomicLong WRITABLE_RECOVERIES = new AtomicLong();
	private static final AtomicLong DISCONNECTS = new AtomicLong();
	private static final Set<String> AUTHENTICATED =
		Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

	public static boolean isEnabled() {
		return Integer.getInteger(CLIENTS_PROPERTY, 0).intValue() > 0;
	}

	public static int expectedClients() {
		return Math.max(0, Integer.getInteger(CLIENTS_PROPERTY, 0).intValue());
	}

	public static void reset() {
		CHANNELS.set(0L);
		INBOUND_BYTES.set(0L);
		OUTBOUND_WRITES.set(0L);
		OUTBOUND_BYTES.set(0L);
		SERIALIZATION_CALLS.set(0L);
		SERIALIZATION_NANOS.set(0L);
		SERIALIZATION_BYTES.set(0L);
		UNWRITABLE_TRANSITIONS.set(0L);
		WRITABLE_RECOVERIES.set(0L);
		DISCONNECTS.set(0L);
		AUTHENTICATED.clear();
	}

	public static void recordSerialization(final long nanos, final int bytes) {
		if (!isEnabled()) return;
		SERIALIZATION_CALLS.incrementAndGet();
		SERIALIZATION_NANOS.addAndGet(Math.max(0L, nanos));
		SERIALIZATION_BYTES.addAndGet(Math.max(0, bytes));
	}

	public static String summary() {
		final long calls = SERIALIZATION_CALLS.get();
		final int expected = expectedClients();
		final boolean passed = expected > 0
			&& AUTHENTICATED.size() >= expected
			&& INBOUND_BYTES.get() > 0L
			&& OUTBOUND_WRITES.get() > 0L
			&& OUTBOUND_BYTES.get() > 0L
			&& calls > 0L
			&& UNWRITABLE_TRANSITIONS.get() > 0L
			&& WRITABLE_RECOVERIES.get() > 0L;
		return " networkExpectedClients=" + expected
			+ " networkAuthenticatedClients=" + AUTHENTICATED.size()
			+ " networkChannels=" + CHANNELS.get()
			+ " networkInboundBytes=" + INBOUND_BYTES.get()
			+ " networkOutboundWrites=" + OUTBOUND_WRITES.get()
			+ " networkOutboundBytes=" + OUTBOUND_BYTES.get()
			+ " networkSerializationCalls=" + calls
			+ " networkSerializationMs=" + nanosToMillis(SERIALIZATION_NANOS.get())
			+ " networkSerializationBytes=" + SERIALIZATION_BYTES.get()
			+ " networkUnwritableTransitions=" + UNWRITABLE_TRANSITIONS.get()
			+ " networkWritableRecoveries=" + WRITABLE_RECOVERIES.get()
			+ " networkDisconnects=" + DISCONNECTS.get()
			+ " authenticatedNetworkInvariant=" + (passed ? "pass" : "fail");
	}

	@Override
	public void channelActive(final ChannelHandlerContext ctx) throws Exception {
		CHANNELS.incrementAndGet();
		ctx.channel().attr(STATE).set(new ChannelState());
		super.channelActive(ctx);
	}

	@Override
	public void channelRead(final ChannelHandlerContext ctx, final Object message)
		throws Exception {
		if (message instanceof ByteBuf) {
			INBOUND_BYTES.addAndGet(((ByteBuf) message).readableBytes());
		}
		super.channelRead(ctx, message);
	}

	@Override
	public void write(final ChannelHandlerContext ctx, final Object message,
		final ChannelPromise promise) throws Exception {
		final ChannelState state = state(ctx);
		observeAuthenticatedPlayer(ctx, state);
		if (message instanceof ByteBuf) {
			final ByteBuf bytes = (ByteBuf) message;
			final int readable = bytes.readableBytes();
			OUTBOUND_WRITES.incrementAndGet();
			OUTBOUND_BYTES.addAndGet(readable);
		}
		super.write(ctx, message, promise);
	}

	@Override
	public void channelWritabilityChanged(final ChannelHandlerContext ctx)
		throws Exception {
		final ChannelState state = state(ctx);
		if (ctx.channel().isWritable()) {
			if (state.wasUnwritable.compareAndSet(true, false)) {
				WRITABLE_RECOVERIES.incrementAndGet();
			}
		} else if (state.wasUnwritable.compareAndSet(false, true)) {
			UNWRITABLE_TRANSITIONS.incrementAndGet();
		}
		super.channelWritabilityChanged(ctx);
	}

	@Override
	public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
		DISCONNECTS.incrementAndGet();
		super.channelInactive(ctx);
	}

	private static ChannelState state(final ChannelHandlerContext ctx) {
		ChannelState state = ctx.channel().attr(STATE).get();
		if (state == null) {
			state = new ChannelState();
			ctx.channel().attr(STATE).set(state);
		}
		return state;
	}

	private static void observeAuthenticatedPlayer(final ChannelHandlerContext ctx,
		final ChannelState state) {
		final ConnectionAttachment attachment = ctx.channel()
			.attr(RSCConnectionHandler.attachment).get();
		if (attachment == null || attachment.player.get() == null) return;
		final String username = attachment.player.get().getUsername();
		AUTHENTICATED.add(username);
		if (SLOW_USERNAME.equalsIgnoreCase(username)
			&& state.pressureConfigured.compareAndSet(false, true)) {
			// Benchmark-only pressure proof; steady readers keep production values.
			ctx.channel().config().setWriteBufferWaterMark(
				new WriteBufferWaterMark(512, 1024));
		}
	}

	private static String nanosToMillis(final long nanos) {
		return String.format(java.util.Locale.ROOT, "%.3f", nanos / 1_000_000.0d);
	}

	private static final class ChannelState {
		private final AtomicBoolean pressureConfigured = new AtomicBoolean();
		private final AtomicBoolean wasUnwritable = new AtomicBoolean();
	}
}
