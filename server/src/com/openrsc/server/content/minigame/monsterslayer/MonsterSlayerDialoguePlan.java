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
		case 2: return steps(n("Hah! You did it! Every last task!"), n("You've earned Elite rank."), n("Take this badge."), n("But listen."), n("The fun is over now."), n("Elite work begins inside the true guilds."), n("Not everyone comes back from that work."), n("And come back any time to slay more with"), n("The best of the best!"));
		case 3: return steps(n("'Grats on making it this far!"), n("I knew you had it in you."), p("Than-"), n("Best not keep the Heroes' sect waiting."), n("You've earned Champion rank!"), n("And I present to you the latest and greatest."), n("Monster Slayer Guild Medal!"), p("..."), n("Well, aren't you going to say thank you?"), p("Th-"), n("Off you go!"));
		case 4: return steps(n("You've fought and slain giants, dragons,"), n("And beasts from the depths of hell."), n("You've done well to protect the world"), n("From all manner of evil."), n("I grant you this crest and the rank of Hero."), n("May your name carry the same weight."), n("And your foes shudder when they hear it."), p("It's been an honor and I won't let you down."));
		case 5: return steps(n("Well done. Very well done."),
			n("You have overcome every trial set before you."),
			n("You have proven your strength, your resolve,"),
			n("And your place among the greatest adventurers of this age."),
			n("You've completed your journey for now."),
			n("You've done well."),
			p("And what's my new rank?"),
			n("And what use would you make of it?"),
			p("Whatever is required of me."),
			n("A suitable answer."),
			n("Then rise as a Legend of the Monster Slayer Guild."),
			p("It's an honor."),
			n("See that it remains one."));
		default: throw new IllegalArgumentException("Unknown promotion tier: " + tier);
		}
	}
	/** One-time, speaker-typed welcome before Doran's first authoritative assignment. */
	public static List<Step> doranFirstTaskWelcome() {
		return steps(n("Welcome to your first true stint in a guild sect."),
			n("You're part of the Champions now!"), p("Than-"),
			n("You're welcome! Best not dilly-dally."),
			n("Monsters won't be slaying themselves."));
	}
	/** One-time, speaker-typed welcome before Sella's first authoritative assignment. */
	public static List<Step> sellaFirstTaskWelcome() {
		return steps(n("I see by your medal a true hero stands before me."),
			n("But I'll put that medal to the test."),
			n("A hero defends the people of this world."),
			n("And to do that you need to defeat some mighty foes."),
			n("I hope you're ready!"), p("I've never been more ready!"));
	}
	/** One-time, speaker-typed welcome before Radimus's first authoritative assignment. */
	public static List<Step> radimusFirstTaskWelcome() {
		return steps(n("Excellent. Your reputation has brought you far."),
			n("But reputation alone does not make a legend."),
			n("The Legends Guild remembers deeds, not promises."),
			n("Complete the trials I set before you."),
			n("And your name may yet earn its place in these halls."),
			p("I'm ready to make history."));
	}
	private static Step n(String text) { return new Step(Speaker.NPC, text); } private static Step p(String text) { return new Step(Speaker.PLAYER, text); }
	private static List<Step> steps(Step... values) { return Collections.unmodifiableList(Arrays.asList(values)); }
}
