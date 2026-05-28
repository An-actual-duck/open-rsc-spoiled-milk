package com.openrsc.server.plugins.custom.minigames;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.Damage;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.triggers.OpInvTrigger;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;
import com.openrsc.server.plugins.triggers.UseInvTrigger;
import com.openrsc.server.util.rsc.MessageType;

import static com.openrsc.server.plugins.Functions.compareItemsIds;
import static com.openrsc.server.plugins.RuneScript.*;

public class ArmyOfObscurity implements OpInvTrigger, UseInvTrigger, TalkNpcTrigger {
	public static final String CACHE_KEY = "army_of_obscurity";

	public static final int STAGE_COMPLETED = -1;
	public static final int STAGE_NOT_STARTED = 0;
	public static final int STAGE_AGREED_TO_GET_BOOK = 1;
	public static final int STAGE_TALKED_TO_RELDO = 2;
	public static final int STAGE_ATTEMPTED_TO_TAKE_BOOK = 3;
	public static final int STAGE_TOLD_ASH_WORDS_BAD = 4;
	public static final int STAGE_GOT_NEW_WORD = 5;
	public static final int STAGE_OBTAINED_NECRONOMICON = 6;
	public static final int STAGE_OFF_TO_MUSEUM = 7;
	public static final int STAGE_OBTAINED_AMULET = 8;

	private static void teleportPlayer(Player player) {
		mes("You reach for the book...");
		delay(5);
		mes("As you do so you feel a low rumble");
		delay(5);
		mes("An otherworldy voice cries out"); // this is ash's dad in the future reaching back into the past to chastize the player
		delay(5);
		mes("@yel@MORTAL FOOL! YOU HAVE USED THE WRONG WORDS!");
		delay(5);
		player.teleport(161, 453, true);
	}

	public static void searchBookcase(Player player, int stage) {
		if (stage == STAGE_TALKED_TO_RELDO) {
			mes("You search the bookcase...");
			delay(5);
			mes("You see an odd-looking book");
			delay(5);
			mes("Necronomicon ex mortis");
			delay(5);
			say("This must be it",
				"Now what were the words...",
				"Klatoo...",
				"Verata...",
				"Nectar!");
			teleportPlayer(player);
			setStage(player, STAGE_ATTEMPTED_TO_TAKE_BOOK);
		} else if (stage == STAGE_ATTEMPTED_TO_TAKE_BOOK) {
			mes("As you reach for the book");
			delay(5);
			mes("You stop to reconsider");
			delay(5);
			mes("Perhaps it wouldn't be very smart to try taking the book again");
			delay(5);
			mes("without knowing the correct words");
		} else if (stage == STAGE_GOT_NEW_WORD) {
			mes("You search the bookcase...");
			delay(5);
			mes("And locate the book again");
			delay(5);
			mes("Necronomicon ex mortis");
			delay(5);
			say("Here it is",
				"Now what were the words...");

			String[] words = new String[]{ "Kapo!", "Klatoo!", "Klomo!" };
			int word1 = multi(words);
			if (word1 == -1) {
				return;
			}
			say(words[word1]);

			words[0] = "Verata!";
			words[1] = "Veranda!";
			words[2] = "Vactata!";
			int word2 = multi(words);
			if (word2 == -1) {
				return;
			}
			say(words[word2]);

			words = new String[]{ "Necktie!", "Nectar!", "Nicto!", "Nec-*cough*" };
			words[0] = "Necktie!";
			words[1] = "Nectar!";
			words[2] = "Nicto!";
			words[3] = "Nec-*cough*";
			int word3 = multi(words);
			if (word3 == -1) {
				return;
			}
			say(words[word3]);

			if (word1 != 1 || word2 != 0 || word3 != 2) {
				teleportPlayer(player);
				return;
			}

			mes("You reach for the book...");
			delay(5);
			mes("And remove it from the shelf");
			delay(5);
			give(ItemId.NECRONOMICON.id(), 1);
			setStage(player, STAGE_OBTAINED_NECRONOMICON);
		} else if (stage >= STAGE_OBTAINED_NECRONOMICON) {
			if (ifheld(ItemId.NECRONOMICON.id(), 1)) {
				mes("There's nothing of interest now that you've taken the Necronomicon");
				return;
			}

			mes("You search the bookcase...");
			delay(5);
			mes("Somehow the Necronomicon returned!");
			delay(5);
			mes("You reach out to take the book...");
			delay(5);
			mes("But wait...");
			delay(5);
			mes("Do you say the words again?");
			delay(5);
			int option = multi("Yes", "No");
			if (option == 0) {
				mes("You've heard the words so many times at this point");
				mes("you say them without really having to think");
				delay(5);
				say("Klatoo!",
					"Verata!",
					"Nicto!");
				mes("You remove the book from the shelf");
				give(ItemId.NECRONOMICON.id(), 1);
				delay(5);
				mes("Good thing you said the words again");
				delay(5);
				mes("Better safe than sorry");
			} else if (option == 1) {
				mes("You take the book from the shelf");
				give(ItemId.NECRONOMICON.id(), 1);
				delay(5);
				mes("After waiting a few seconds");
				delay(5);
				mes("Nothing happens");
				delay(5);
				mes("Looks like you didn't need to say the words again after all!");
				delay(5);
				mes("Good thing you didn't say them");
				mes("You probably would've felt rather silly");
			}
		}
	}

	public static void reldoDialogue(Player player, Npc npc, int stage) {
		if (stage == ArmyOfObscurity.STAGE_AGREED_TO_GET_BOOK) {
			say("I was told to look for a particularly evil magic book");
			npcsay("An evil magical book you say?",
				"I don't think we have any books like that in my library",
				"Although there was a strange fellow",
				"who came by recently",
				"He was messing with the books in the bookcase over there",
				"and there has been a foul smell in that corner since",
				"I suspect that he may have left something there",
				"I just haven't been able to find it");
			setStage(player, ArmyOfObscurity.STAGE_TALKED_TO_RELDO);
		} else if (stage == STAGE_TALKED_TO_RELDO) {
			npcsay("Did you find whatever was making that awful smell?",
				"Perhaps try checking that bookcase in the corner");
		} else if (stage == STAGE_GOT_NEW_WORD) {
			npcsay("Oh you're back",
				"I honestly didn't even see you leave",
				"Did you get rid of that weird book yet?");
			say("no");
		} else if (ifheld(ItemId.NECRONOMICON.id(), 1)) {
			npcsay("Oh that smell...",
				"Thankyou for finding that, please make haste now");
		}
	}

	public static void recoverBoomstick(Player player, Npc npc) {
		npcsay("Yes yes of course you did",
			"I have it",
			"Don't bother asking me how I got it",
			"Just take it back and get out of here");
		mes("Father Urhney hands you the Boomstick");
		give(ItemId.BOOMSTICK.id(), 1);
	}

	public static void fatherUrhneyDialogue(Player player, Npc npc) {
		int stage = getStage(player);
		if (stage >= STAGE_NOT_STARTED && stage <= STAGE_ATTEMPTED_TO_TAKE_BOOK) {
			npcsay("Won't you simpletons leave me alone?",
				"It's bad enough that this loudmouth braggart suddenly moved in",
				"Now he's bringing his friends!");
		} else if (stage == STAGE_TOLD_ASH_WORDS_BAD) {
			npcsay("Of course I do!",
				"It's a simple enchantment-breaking spell",
				"But of course you got it wrong",
				"I'm surprised this fool even knows his own name",
				"Perhaps he hit his head when he \"travelled back to our time\"",
				"Such nonsense");
			mes("You decide to interrupt Father Urhney");
			delay(5);
			mes("Otherwise this may go on for some time");
			delay(5);
			say("What's the proper magic words then?");
			mes("Father Urhney looks upset at being interrupted");
			delay(5);
			npcsay("Well alright then you impatient baboon",
				"The first two words you were given were surprisingly correct",
				"Klatoo and Verata are indeed words that should be said",
				"But this dunderheaded villain told you \"nectar\" for the third word",
				"The actual third word is in fact",
				"Nicto!",
				"Saying those three words precisely should allow you to retrieve the book");
			setStage(player, STAGE_GOT_NEW_WORD);
		} else if (stage == STAGE_GOT_NEW_WORD) {
			npcsay("What about it?",
				"I've already given you the words!",
				"Or have you forgotten them now, too?");
			mes("Father Urhney lets out a huge sigh");
			delay(5);
			npcsay("One more time",
				"The words are",
				"Klatoo!",
				"Verata!",
				"Nicto!",
				"Now leave my home!");
		} else if (stage == STAGE_COMPLETED) {
			npcsay("Finally that troublesome fellow is gone",
				"I'm not sure where he went, but at least he's not here anymore",
				"Off with you too now, shoo");
		}
	}

	private static void ashDialogue(Player player, Npc npc) {
		int option;
		int stage = getStage(player);
		switch (stage) {
			case STAGE_NOT_STARTED:
				npcsay("Get inside!",
					"How did you make it past all the zombites?");

				option = multi("What are \"zombites\"?",
					"There's nothing out there",
					"I'm outta here");

				if (option == -1 || option == 2) {
					return;
				}

				npcsay("What are you talking about?",
					"Obviously there's evil hellspawn all over the place",
					"If we don't stop them they're gonna kill everyone!",
					"And then I'll never be able to get back home");

				option = 0;
				while (option != 2) {
					option = multi("Who are you?",
						"Where are you from?",
						"Alright how can we stop the zombites?",
						"You sound insane I'm leaving");
					if (option == -1 || option == 3) {
						return;
					} else if (option == 0) {
						npcsay("Name's Ash - housewares",
							"I'm the guy that's gonna save all our asses");
					} else if (option == 1) {
						npcsay("I'm from a little place known as \"the future\"",
							"And I'd kinda like to get back there as soon as possible");
					}
				}

				npcsay("Well, we're going to need to get our hands on a special book",
					"It's called the Necronomicon Ex-Mortis",
					"The Book of the Dead",
					"Thankfully I've got a lead on where we can find it",
					"Apparently there's a library in some lousy castle",
					"In some lousy town called \"Far-rock\" or something",
					"You should head there and take a look",
					"There's this guy named Reldo or something that can probably help you");

				option = multi("Alright I'll see what I can do",
					"No way you're crazy");

				if (option == -1 || option == 1) {
					return;
				}

				npcsay("Before you go you need to know one more thing",
					"In order to take the book",
					"You need to say some magic words",
					"Or at least that's what I was told",
					"The words are",
					"Klatoo!",
					"Verata!",
					"uhh...",
					"Nectar!",
					"Have fun");
				setStage(player, STAGE_AGREED_TO_GET_BOOK);
				break;
			case STAGE_AGREED_TO_GET_BOOK:
			case STAGE_TALKED_TO_RELDO:
				npcsay("What are you sitting around talking to me for?",
					"Go get that book so I can get out of here!",
					"And so that you can be rid of those zombites",
					"Remember when you go to grab the book",
					"you need to say the magic words",
					"Klatoo!",
					"Varata!",
					"uhh...",
					"Nectar!",
					"Now get out of here",
					"Go bother that Reldo guy I told you about");
				break;
			case STAGE_ATTEMPTED_TO_TAKE_BOOK:
				npcsay("I didn't expect to see you again",
					"Well did you get the book?");
				if (multi("The words you gave me were wrong", "No not yet") != 0) {
					return;
				}

				npcsay("What?",
					"That's ridiculous",
					"Look maybe I don't remember every single syllable",
					"but those are basically the words",
					"Maybe you could try-");
				setStage(player, STAGE_TOLD_ASH_WORDS_BAD);

				// If Father Urhney is busy
				if (!ifnearnpc(NpcId.URHNEY.id())) {
					mes("Urhney suddenly looks very annoyed");
					delay(5);
					mes("Perhaps he has something to say");
					return;
				}

				// If we make it here, Urhney is now the interacting NPC
				npcsay("You're a couple of unschooled, uneducated charlatans",
					"Neither of you know anything about getting rid of evil",
					"I guess I will have to lend my expertise");

				// Try to switch back to Ash
				if (ifnearnpc(NpcId.ASH.id())) {
					npcsay("Well hello Mr. Fancy Pants",
						"Why don't you go talk to this guy then",
						"If he's so smart");
				}
				break;
			case STAGE_TOLD_ASH_WORDS_BAD:
				npcsay("Why don't you talk to Mr. Fancy Pants over there",
					"Obviously he has something to say");
				break;
			case STAGE_GOT_NEW_WORD:
				npcsay("Well you got the right word from Padre over there",
					"didn't you?",
					"See if you can get the book now");
				break;
			case STAGE_OBTAINED_NECRONOMICON:
				say("I got the book");
				npcsay("You made it back again?",
					"You seem to be pretty good at surviving out there",
					"So it's time for another suicide mission",
					"Now that you have the book",
					"We need one more item to get rid of these zombites",
					"Once and for all",
					"And most importantly",
					"To get me back home");

				if (multi("What do we need?", "I'm done doing stuff right now") != 0) {
					return;
				}

				npcsay("We need a special, ancient amulet",
					"Luckily I know where to get it");

				option = multi("How do you always know where to find the things we need?",
					"Alright, where should I look?");

				if (option == -1) {
					return;
				} else if (option == 0) {
					npcsay("Because I'm just that good",
						"But anyway");
				}

				npcsay("Where do you go to find old stuff?",
					"That's right",
					"A museum",
					"There's a museum in that town where you found the book",
					"Go talk to the curator there",
					"That guy probably knows something");
				setStage(player, STAGE_OFF_TO_MUSEUM);
				break;
			case STAGE_OFF_TO_MUSEUM:
				npcsay("Aren't you supposed to be headed off to the museum?",
					"We need that amulet");
				break;
			case STAGE_OBTAINED_AMULET:
				if (!ifheld(ItemId.ZOMBITE_AMULET.id(), 1)) {
					npcsay("Did you get the amulet?");
					say("Well I did");
					if (player.getCarriedItems().getEquipment().hasEquipped(ItemId.ZOMBITE_AMULET.id())) {
						say("But it looked so sinister i just had to try it on");
						npcsay("Are you kidding me?");
						int justKidding = multi("Right I'll get it off then", "Honestly i don't want to part with it");
						if (justKidding != 1) {
							return;
						} else {
							npcsay("Snap out of it!!",
								"Remember what we've been fighting for");
							say("... We?");
							return;
						}
					} else {
						say("But I seem to have misplaced it");
						npcsay("Are you kidding me?",
							"Well you'd best go find it",
							"Or I'll never get home!",
							"Oh",
							"And we won't be able to stop the zombites");
						say("Where should I look?");
						npcsay("Why would I know?",
							"Retrace your steps",
							"Start by talking to the museum curator or something");
					}
					return;
				}

				say("I've got the amulet");

				if (!ifheld(ItemId.NECRONOMICON.id(), 1)) {
					npcsay("Great",
						"Now just hand it to me",
						"Along with the book-");
					mes("Ash pauses");
					delay(5);
					npcsay("You do have the book right?");
					say("I did",
						"But I must have lost it at some point");
					npcsay("Well you'd better go find it again!",
						"Maybe try looking where you first got it?");
					return;
				}

				npcsay("Great",
					"Now we just need to say some more hocus pocus so I can go home",
					"What were those words now...",
					"Candy Salmon Robe... Nosferatu... Raising Arizona...");

				// We need Father Urhney for this next part, so if we can't grab him
				// we have to stop
				if (!ifnearnpc(NpcId.URHNEY.id())) {
					mes("Despite being occupied...");
					delay(5);
					mes("Father Urhney looks like he really wants to interject");
					delay(5);
					mes("Perhaps you should try to talk to Ash again when Father Urhney isn't busy");
					return;
				}

				// If we make it here, Father Urhney is now the interacting NPC
				npcsay("Are you at it again, you halfwitted macaque",
					"If any spellcastings and incantations are to be done around here",
					"Then let me handle it before you two send us all into the netherworld",
					"It seems that you're trying to use ancient kharidian magic",
					"I happen to have a book on the matter by one A. Al-Hazred");
				mes("Father Urhney finds a book and briefly flips through the pages");
				delay(5);
				npcsay("Yes, here we are",
					"Place the book on the table and the amulet on top if you please");

				mes("You do as father Urhney instructs");
				remove(ItemId.NECRONOMICON.id(), 1);
				remove(ItemId.ZOMBITE_AMULET.id(), 1);
				delay(5);
				
				npcsay("Now this should be the end of all this nonsense",
					"Kanda! Samonda Roba Areda Gyes Indy En-zeen Nos-Feratos");
				mes("The ground begins to rumble");
				delay(5);
				npcsay("Nos-Feratos Amen-non. Ak-adeem! Razin Arozonia!");
				mes("The cabin begins to shake and you hear screams from the outside");
				delay(5);
				npcsay("Kanda!");
				mes("Everything immediately goes still");
				delay(5);

				if (ifnearnpc(NpcId.ASH.id())) {
					// The interacting NPC is now Ash
					npcsay("Hail to the king, baby");
					mes("Ash is teleported away");
					delay(5);
				} else {
					// The interacting NPC is not Ash
					mes("Ash begins to be teleported away");
					delay(5);
					mes("As this happens you hear him say something");
					delay(5);
					mes("@yel@Ash: Hail to the king, baby");
				}

				// "Teleport" Ash
				setStage(player, STAGE_COMPLETED);
				// The "npc" will be Ash, regardless of who is the interacting NPC
				ActionSender.sendTeleBubble(player, npc.getX(), npc.getY(), false);

				// Spawn Boomstick
				setcoord(new Point(npc.getX(), npc.getY()));
				addobject(ItemId.BOOMSTICK.id(), 1, 200);

				// Spawn Bones
				int[][] boneSpawns = new int[][]{
					new int[]{120, 708},
					new int[]{118, 707},
					new int[]{116, 706},
					new int[]{114, 709},
					new int[]{111, 710},
					new int[]{112, 712},
					new int[]{112, 714},
					new int[]{116, 713},
					new int[]{118, 714},
					new int[]{120, 712},
					new int[]{121, 711}
				};

				for (int i = 0; i < boneSpawns.length; ++i) {
					setcoord(new Point(boneSpawns[i][0], boneSpawns[i][1]));
					addobject(ItemId.BONES.id(), 1, 200);
				}

				mes("An odd item falls to the ground where Ash once stood");
				delay(5);

				player.playerServerMessage(MessageType.QUEST, "@gre@Congratulations! You have completed Army of Obscurity!");
				break;
			case STAGE_COMPLETED:
				// Admin only dialogue, Ash is invisible now otherwise.
				npcsay("Great Scott, why didn't that work?!",
					"Urhney I thought you knew what you were doing!!",
					"I'm supposed to be back to the future right now!");
				// === Trivia ===
				// * This is possibly a reference to the movie ''Back to The Future'' (1985)
				//   in which Doc's signature catchphrase was "Great Scott"
				boolean screaming = false;
				if (!ifnearnpc(NpcId.URHNEY.id())) {
					mes("Despite being occupied...");
					delay(5);
					mes("Father Urhney looks like he really wants to interject");
					delay(5);
					screaming = true;
					npcsay("AAaaauaaugh");
				} else {
					// Urhney now
					delay(2);
					npcsay("I wish my mother had just spelled my name Ernie.");
					delay(2);
				}
				if (ifnearnpc(NpcId.ASH.id()) && !screaming) {
					// The interacting NPC is now Ash
					npcsay("AAaaauaaugh");
				}
				mes("Ash seems very distressed and confused.");

		}
	}

	public static void burnNecronomicon(Player player) {
		mes("You throw the book into the fire.");
		delay(5);
		mes("The book is unaffected by the flames");
		delay(5);
		mes("Strange.");
		delay(5);
		mes("You feel like that should've worked");
	}

	@Override
	public void onTalkNpc(Player player, Npc npc) {
		if (npc.getID() == NpcId.ASH.id()) {
			ashDialogue(player, npc);
		}
	}

	@Override
	public boolean blockTalkNpc(Player player, Npc npc) {
		return npc.getID() == NpcId.ASH.id();
	}

	public static int getStage(Player player) {
		return player.getCache().hasKey(CACHE_KEY) ? player.getCache().getInt(CACHE_KEY) : 0;
	}

	public static void setStage(Player player, int stage) {
		player.getCache().set(CACHE_KEY, stage);
	}

	@Override
	public boolean blockUseInv(Player player, Integer invIndex, Item item1, Item item2) {
		return compareItemsIds(item1, item2, ItemId.NECRONOMICON.id(), ItemId.ZOMBITE_AMULET.id());
	}

	@Override
	public void onUseInv(Player player, Integer invIndex, Item item1, Item item2) {
		mes("It does kind of look like those would go together, eh?");
		delay(3);
		mes("Uhrney would know how.");
	}

	@Override
	public void onOpInv(Player player, Integer invIndex, Item item, String command) {
		mes("You try to read the Necronomicon...");
		delay(5);
		if (player.getSkills().getLevel(Skill.HITS.id()) > 3) {
			player.getUpdateFlags().setDamage(new Damage(player, 1));
			player.getSkills().subtractLevel(Skill.HITS.id(), 1);
			mes("The contents are so vile that it physically harms you");
		} else {
			mes("but you cannot find the strength...");
		}
	}

	@Override
	public boolean blockOpInv(Player player, Integer invIndex, Item item, String command) {
		return item.getCatalogId() == ItemId.NECRONOMICON.id();
	}
}
