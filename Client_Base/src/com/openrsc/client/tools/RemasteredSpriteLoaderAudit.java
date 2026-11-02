package com.openrsc.client.tools;

import com.openrsc.client.model.Sprite;
import orsc.remastered.RemasteredSpriteCatalog;
import orsc.remastered.RemasteredSpriteResolver;
import orsc.remastered.RemasteredSpriteSettings;

import java.util.Properties;

public final class RemasteredSpriteLoaderAudit {
	private RemasteredSpriteLoaderAudit() {
	}

	public static void main(String[] args) {
		require(!RemasteredSpriteSettings.isEnabled(), "default setting is not off");
		Properties settings = new Properties();
		settings.setProperty(RemasteredSpriteSettings.PROPERTY_KEY, "true");
		RemasteredSpriteSettings.loadFromClientSettings(settings);
		require(RemasteredSpriteSettings.isEnabled() == RemasteredSpriteSettings.isAvailable(),
			"persisted true bypassed sprite availability");
		settings.setProperty(RemasteredSpriteSettings.PROPERTY_KEY, "false");
		RemasteredSpriteSettings.loadFromClientSettings(settings);
		require(!RemasteredSpriteSettings.isEnabled(), "persisted false setting was not loaded");
		RemasteredSpriteSettings.setEnabled(true);
		RemasteredSpriteSettings.applyClassicProfile();
		require(!RemasteredSpriteSettings.isEnabled(), "Classic profile did not force the setting off");

		String key = args.length == 0 ? "sprite/items/0/0" : args[0];
		RemasteredSpriteCatalog.Entry entry = RemasteredSpriteCatalog.get(key);
		if (entry == null) {
			throw new IllegalArgumentException("Unknown audit key: " + key);
		}
		Sprite canonical = canonical(entry);
		RemasteredSpriteResolver resolver = new RemasteredSpriteResolver();

		RemasteredSpriteSettings.setEnabled(false);
		require(resolver.resolve(key, canonical) == canonical, "disabled mode changed canonical identity");

		RemasteredSpriteSettings.setEnabled(true);
		if (!RemasteredSpriteSettings.isAvailable()) {
			require(!RemasteredSpriteSettings.isEnabled(), "manual enable bypassed owner pause");
			require(!RemasteredSpriteSettings.toggle(), "toggle bypassed owner pause");
			require(resolver.resolve(key, canonical) == canonical, "paused catalog changed canonical identity");
			Properties saved = new Properties();
			RemasteredSpriteSettings.saveToClientSettings(saved);
			require("false".equals(saved.getProperty(RemasteredSpriteSettings.PROPERTY_KEY)),
				"paused sprite setting was not saved off");
			System.out.println("PASS: remastered sprites paused; canonical fallback " + resolver.diagnostics());
			return;
		}
		Sprite override = resolver.resolve(key, canonical);
		require(override != canonical, "enabled mode did not select a side-by-side override");
		require(hasVisiblePixel(override), "decoded override contains no visible pixels");
		require(!hasVisiblePixel(canonical), "side-by-side decode mutated canonical pixels");
		require(override.getWidth() == canonical.getWidth() && override.getHeight() == canonical.getHeight(),
			"override changed canonical pixel dimensions");
		require(override.getXShift() == canonical.getXShift()
			&& override.getYShift() == canonical.getYShift()
			&& override.getSomething1() == canonical.getSomething1()
			&& override.getSomething2() == canonical.getSomething2(),
			"override did not inherit canonical placement metadata");
		require(override == resolver.resolve(key, canonical), "override was decoded more than once");
		require(resolver.resolve("sprite/items/not-covered/0", canonical) == canonical,
			"missing coverage did not return exact canonical object");

		Sprite incompatible = canonical(entry);
		incompatible.setShift(entry.getShiftX() + 1, entry.getShiftY());
		require(resolver.resolve(key, incompatible) == incompatible,
			"metadata mismatch did not return exact incompatible canonical object");

		RemasteredSpriteSettings.setEnabled(false);
		require(resolver.resolve(key, canonical) == canonical, "toggle off did not restore canonical identity");
		Properties saved = new Properties();
		RemasteredSpriteSettings.saveToClientSettings(saved);
		require("false".equals(saved.getProperty(RemasteredSpriteSettings.PROPERTY_KEY)),
			"disabled setting was not persisted");
		System.out.println("PASS: remastered sprite side-by-side loader " + resolver.diagnostics());
	}

	private static Sprite canonical(RemasteredSpriteCatalog.Entry entry) {
		Sprite sprite = new Sprite(new int[entry.getPixelWidth() * entry.getPixelHeight()],
			entry.getPixelWidth(), entry.getPixelHeight());
		sprite.setRequiresShift(entry.requiresShift());
		sprite.setShift(entry.getShiftX(), entry.getShiftY());
		sprite.setSomething(entry.getBoundWidth(), entry.getBoundHeight());
		sprite.setName(123, "audit-canonical");
		return sprite;
	}

	private static boolean hasVisiblePixel(Sprite sprite) {
		for (int pixel : sprite.getPixels()) {
			if (pixel != 0) {
				return true;
			}
		}
		return false;
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalStateException(message);
		}
	}
}
