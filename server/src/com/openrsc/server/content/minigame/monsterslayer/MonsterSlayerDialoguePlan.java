package com.openrsc.server.content.minigame.monsterslayer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Immutable, speaker-typed player dialogue plans for Monster Slayer contacts. */
public final class MonsterSlayerDialoguePlan {
	public enum Speaker { NPC, PLAYER }
	public static final class Step {
		private final Speaker speaker; private final String text;
		public Step(Speaker speaker, String text) { if (speaker == null || text == null || text.length() == 0 || text.length() > 255) throw new IllegalArgumentException("Invalid dialogue step"); this.speaker = speaker; this.text = text; }
		public Speaker getSpeaker() { return speaker; } public String getText() { return text; }
	}
	private MonsterSlayerDialoguePlan() { }
	public static List<Step> promotion(int tier) {
		switch (tier) {
		case 0: return steps(n("That was your final Fledgling task."), n("You've done a fine job culling those monsters."), p("There seem to be just as many as before."), n("Imagine how many there would be without you."), n("You've earned a promotion."), n("You are now an Adept."), n("Here is your official Adept sticker."), p("A sticker? What happened to the stamp?"), n("Stamps were too impermanent."), p("And stickers are better?"), n("Much better."), n("Wear it with pride."), n("You can now access the Fledgling shop."), n("Just speak with my associate over there."), n("He knows a thing or two about satchels as well."));
		case 1: return steps(n("Well that was it, the last one."), n("At this point I'd say you've proven yourself."), n("I award you Veteran status."), n("Please accept this button as proof of your rank."), p("I'm honored. Thank you."), p("But um..."), p("Why does it say 'I heart PS'?"), n("To show your Port Sarim pride!"), p("Right, of course."));
		case 2: return steps(n("Hah. I knew you had it in you. You're Elite now. Take the badge."), n("Listen, though. You're off to play with the big boys now."), n("Not all adventurers survive the big leagues. I didn't."), n("That's why I'm here telling stories instead of making them."), n("My associate will trade Veteran Slayer Points with an Elite."));
		case 3: return steps(n("Splendid work! You faced the test and did not blink."), n("You are a Champion now. Take this medal, and try not to polish it on your sleeve."), n("The quartermaster nearby takes Elite Slayer Points."));
		case 4: return steps(n("You completed the work, even when it was hard. That is the part people remember."), n("You are a Hero. Carry this crest with care."), n("The supplier nearby accepts Champion Slayer Points. A Hero has earned access."));
		case 5: return steps(n("You've completed your journey for now. You've done well."), p("And what's my new rank?"), n("And what use would you make of it?"), p("...Legend, then?"), n("If you continue to earn it."));
		default: throw new IllegalArgumentException("Unknown promotion tier: " + tier);
		}
	}
	private static Step n(String text) { return new Step(Speaker.NPC, text); } private static Step p(String text) { return new Step(Speaker.PLAYER, text); }
	private static List<Step> steps(Step... values) { return Collections.unmodifiableList(Arrays.asList(values)); }
}
