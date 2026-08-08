package com.openrsc.server.combat;

import com.openrsc.server.event.rsc.impl.PoisonEvent;
import com.openrsc.server.model.combat.dot.PoisonDurableRecord;
import com.openrsc.server.model.entity.player.Player;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Child-JVM fixture for the A08 durable-poison restart boundary. The parent
 * test intentionally launches write and read in separate JVMs because the
 * production Skill catalog has one JVM-wide initialization lifecycle.
 */
public final class PoisonProcessRestartFixture {
	private static final String SOURCE_NAME = "dot restart source";
	private static final String TARGET_NAME = "dot restart target";

	private PoisonProcessRestartFixture() {
	}

	public static void main(final String[] args) throws Exception {
		if (args.length != 2) {
			throw new IllegalArgumentException("expected write/read and persistence path");
		}
		if ("write".equals(args[0])) {
			write(Paths.get(args[1]));
		} else if ("read".equals(args[0])) {
			read(Paths.get(args[1]));
		} else {
			throw new IllegalArgumentException("unknown restart phase: " + args[0]);
		}
	}

	private static void write(final Path persistedRecord) throws Exception {
		try (CurrentCombatHarness harness = new CurrentCombatHarness()) {
			final Player source = harness.player(SOURCE_NAME, 730, 680);
			final Player target = harness.player(TARGET_NAME, 731, 680);
			target.applyPoison(40, 60, source);
			final String record = target.getCache().getString(
				PoisonDurableRecord.CACHE_KEY);
			check(record != null && !record.isEmpty(),
				"pre-restart player has one serializable poison record");
			Files.write(persistedRecord, record.getBytes(StandardCharsets.UTF_8));
		}
	}

	private static void read(final Path persistedRecord) throws Exception {
		final String record = new String(Files.readAllBytes(persistedRecord),
			StandardCharsets.UTF_8);
		try (CurrentCombatHarness harness = new CurrentCombatHarness()) {
			final Player source = harness.player(SOURCE_NAME, 730, 680);
			final Player target = harness.player(TARGET_NAME, 731, 680,
				player -> player.getCache().store(PoisonDurableRecord.CACHE_KEY,
					record));
			final PoisonEvent event = target.getAttribute("poisonEvent", null);
			check(event != null, "post-restart login restores one poison event");
			check(target.getCurrentPoisonPower() == 40,
				"post-restart poison current power");
			check(target.getPoisonMaxPower() == 60,
				"post-restart poison maximum power");
			check(source.getUUID().equals(target.getPoisonProvenance().getSourceId()),
				"post-restart poison retains stable source identity");
			check(event.getTicksBeforeRun() == 8L,
				"post-restart poison receives the approved full countdown");
			check(eventCount(harness, target) == 1,
				"post-restart poison has exactly one scheduler stream");
		}
	}

	private static int eventCount(final CurrentCombatHarness harness,
			final Player owner) {
		int count = 0;
		for (final com.openrsc.server.event.rsc.GameTickEvent event
				: harness.server().getGameEventHandler().getEvents()) {
			if (event.isRunning() && event.getOwner() == owner
					&& "Poison Event".equals(event.getDescriptor())) {
				count++;
			}
		}
		return count;
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
