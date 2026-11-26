package com.openrsc.interfaces.misc;

import com.openrsc.interfaces.NComponent;
import com.openrsc.interfaces.NCustomComponent;
import java.awt.Rectangle;
import orsc.mudclient;

public class FishingTrawlerInterface extends NComponent {

	public NComponent netStatus;
	public NComponent fishCaught;
	public NComponent timeLeft;

	public int waterLevel;
	private NCustomComponent waterLevelComponent;
	private NComponent waterText;
	private boolean netRipped;
	private int fishCaughtNumber;
	private int minutesLeft;
	private boolean receivedUpdate;
	private final mudclient client;

	public FishingTrawlerInterface(final mudclient graphics) {
		super(graphics);
		client = graphics;
		setVisible(false);
		setSize(310, 40);
		setLocation(0, 15);

		waterLevelComponent = new NCustomComponent(graphics) {
			@Override
			public void render() {
				float progressBarWidth = 260;

				float waterF = (float) waterLevel;
				float waterPercentage = waterF > 0 ? waterF / 1000 : 0;
				float percentToWidth = (waterPercentage * progressBarWidth);
				graphics().drawBoxAlpha(getX() - 2, getY() - 2, (int) progressBarWidth + 4, 10 + 4, 0, 128);
				graphics().drawBoxAlpha(getX(), getY(), (int) progressBarWidth, 10, 0xffffff, 125);
				if (percentToWidth > progressBarWidth)
					percentToWidth = progressBarWidth;
				else if (percentToWidth < 0)
					percentToWidth = 0;

				graphics().drawBoxAlpha(getX(), getY(), (int) percentToWidth - 2, 10, 0x0000ff, 200);
			}
		};
		waterLevelComponent.setLocation(45, 5);

		waterText = new NComponent(graphics);
		waterText.setText("Water:");
		waterText.setTextSize(2);
		waterText.setLocation(5, 0);
		waterText.setSize(75, 25);
		waterText.setFontColor(0xFFFFFF, 0xFFFFFF);

		netStatus = new NComponent(graphics);
		netStatus.setTextSize(2);
		netStatus.setLocation(5, 15);
		netStatus.setText("Net: @red@Ripped!");
		netStatus.setSize(75, 25);
		netStatus.setFontColor(0xFFFFFF, 0xFFFFFF);

		fishCaught = new NComponent(graphics);
		fishCaught.setTextSize(2);
		fishCaught.setLocation(105, 15);
		fishCaught.setText("Catch: 600 fish");
		fishCaught.setSize(75, 35);
		fishCaught.setFontColor(0xFFFFFF, 0xFFFFFF);

		timeLeft = new NComponent(graphics);
		timeLeft.setSize(75, 25);
		timeLeft.setFontColor(0xFFFFFF, 0xFFFFFF);
		timeLeft.setTextSize(2);
		timeLeft.setLocation(215, 15);
		timeLeft.setText("Time left: 11 min");

		addComponent(waterText);
		addComponent(netStatus);
		addComponent(waterLevelComponent);
		addComponent(timeLeft);
		addComponent(fishCaught);

	}

	@Override
	public void update() {
		netStatus.setText(netRipped ? "Net: @red@Ripped!" : "Net: @gre@OK");
		fishCaught.setText("Catch: " + fishCaughtNumber + " fish");
		// if under one minute: Under 1 min
		if (minutesLeft == 0) {
			timeLeft.setText("Time left: Under 1 min");
		} else
			timeLeft.setText("Time left: " + minutesLeft + " mins");
	}

	public void setVariables(int water, int fish, int time, boolean net) {
		waterLevel = water;
		netRipped = net;
		fishCaughtNumber = fish;
		minutesLeft = time;
		receivedUpdate = true;
	}

	public void show() {
		receivedUpdate = false;
		waterLevel = fishCaughtNumber = minutesLeft = 0;
		netRipped = false;
		setVisible(true);
	}

	public void hide() {
		setVisible(false);
	}

	@Override
	public void renderComponent() throws Exception {
		if (!client.usesCraftingInterfacePreview()) {
			super.renderComponent();
			return;
		}
		ItemWorkspaceStyle style = new ItemWorkspaceStyle(client, () -> false);
		int width = Math.min(client.getGameWidth() - 2 * style.gap, ItemWorkspaceStyle.size(335));
		int height = style.header + 3 * (style.line + style.gap) + style.gap;
		int x = (client.getGameWidth() - width) / 2;
		int y = style.header + style.gap;
		style.panel(new Rectangle(x, y, width, height), "Fishing Trawler");
		int left = x + style.gap;
		int right = x + width - style.gap;
		int lineY = y + style.header + style.gap + style.line - 3;
		if (!receivedUpdate) {
			style.draw("Waiting for voyage status", left, lineY, ItemWorkspaceStyle.MUTED);
			return;
		}

		int level = Math.max(0, Math.min(1000, waterLevel));
		String water = "Water " + (level / 10) + "%";
		style.draw(water, left, lineY, ItemWorkspaceStyle.TEXT);
		int trackLeft = left + style.measure(water) + style.gap;
		int trackWidth = right - trackLeft;
		int trackHeight = Math.max(6, style.line / 2);
		int trackY = lineY - (style.line + trackHeight) / 2 + 2;
		client.getSurface().drawBox(trackLeft, trackY, trackWidth, trackHeight, 0x131E2A);
		int fillWidth = trackWidth * level / 1000;
		if (fillWidth > 0) client.getSurface().drawBox(trackLeft, trackY, fillWidth, trackHeight,
			level >= 800 ? 0xDD8762 : 0x5295CF);
		client.getSurface().drawBoxBorder(trackLeft, trackWidth, trackY, trackHeight, ItemWorkspaceStyle.BORDER);

		lineY += style.line + style.gap;
		style.draw(netRipped ? "Net ripped" : "Net intact", left, lineY,
			netRipped ? 0xFFA78D : 0x91E2A0);
		String catchLabel = "Catch  " + fishCaughtNumber;
		style.draw(catchLabel, right - style.measure(catchLabel), lineY, ItemWorkspaceStyle.TEXT);
		lineY += style.line + style.gap;
		String time = minutesLeft == 0 ? "Time left  Under 1 min" : "Time left  " + minutesLeft + " min";
		style.draw(time, left, lineY, ItemWorkspaceStyle.TEXT);
	}
}
