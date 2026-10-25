package dev.spacebod.aetherium.dev.bench;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.spacebod.aetherium.Aetherium;
import dev.spacebod.aetherium.shaders.config.ShaderPackSettings;
import dev.spacebod.aetherium.shaders.engine.ShaderPackEngine;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Development-only GPU smoke test: an isolated pool scene, every selected pack, and three captures per pack. */
final class PackSmokeAudit {
	private enum Phase { START, BUILD, NEXT, LOAD, SETTLE, CAPTURE, DONE }
	private static final String[] SCENES = {"day", "night", "underwater"};
	private final String[] packs;
	private final Path output;
	private final JsonArray results = new JsonArray();
	private final JsonObject report = new JsonObject();
	private Phase phase = Phase.START;
	private long since = System.nanoTime();
	private long loadStart;
	private int index = -1;
	private int scene;
	private JsonObject entry;
	private JsonArray images;
	private String screenshot;
	private volatile boolean captured;

	PackSmokeAudit(String[] packs, String saveName) {
		if (!saveName.startsWith("aetherium-shader-audit")) {
			throw new IllegalArgumentException("Shader smoke tests require a dedicated aetherium-shader-audit save");
		}
		this.packs = packs;
		String file = System.getenv("AETHERIUM_PLAY_AUDIT_REPORT");
		output = Path.of(file == null ? "aetherium/bench/shader-audit.json" : file);
		report.addProperty("startedAt", Instant.now().toString());
		report.addProperty("save", saveName);
		report.add("packs", results);
	}

	void tick(Minecraft mc) {
		ShaderPackEngine engine = ShaderPackEngine.get();
		double elapsed = (System.nanoTime() - since) / 1e9;
		switch (phase) {
			case START -> {
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
				commands(mc, "gamemode spectator @a", "gamerule advance_time false", "gamerule advance_weather false",
						"gamerule spawn_mobs false", "weather clear", "forceload add -32 -32 32 32", "tp @a 0 202 -20 0 15");
				enter(Phase.BUILD);
			}
			case BUILD -> {
				if (elapsed < 6) {
					return;
				}
				commands(mc, "fill -24 190 -24 24 190 24 minecraft:stone_bricks",
						"fill -18 191 -8 18 194 24 minecraft:water",
						"fill -4 191 -24 4 194 24 minecraft:stone_bricks",
						"fill -24 191 -24 -20 198 -20 minecraft:gold_block",
						"fill 11 191 14 13 203 16 minecraft:polished_diorite",
						"fill -15 191 12 -13 198 14 minecraft:oak_log",
						"fill -18 199 9 -10 201 17 minecraft:oak_leaves",
						"fill 6 191 20 15 196 20 minecraft:blue_stained_glass",
						"setblock 10 190 8 minecraft:sea_lantern", "setblock -10 190 8 minecraft:sea_lantern",
						"setblock 1 195 5 minecraft:chest", "setblock -1 195 5 minecraft:lantern",
						"kill @e[tag=aetherium_shader_audit]",
						"summon minecraft:sheep 1 195 10 {NoAI:1b,Silent:1b,Tags:[\"aetherium_shader_audit\"]}");
				enter(Phase.NEXT);
			}
			case NEXT -> {
				if (elapsed < 3) {
					return;
				}
				if (++index >= packs.length) {
					report.addProperty("finishedAt", Instant.now().toString());
					write();
					Aetherium.LOG.info("ShaderAudit: complete, {} pack cases", results.size());
					ShaderPackSettings.setActivePack(null);
					engine.reload();
					enter(Phase.DONE);
					return;
				}
				entry = new JsonObject();
				entry.addProperty("pack", packs[index]);
				entry.addProperty("status", "loading");
				images = new JsonArray();
				entry.add("screenshots", images);
				results.add(entry);
				write();
				scene = 0;
				setScene(mc);
				Aetherium.LOG.info("ShaderAudit: BEGIN {} ({}/{})", packs[index], index + 1, packs.length);
				ShaderPackSettings.setActivePack(packs[index].equalsIgnoreCase("off") ? null : packs[index]);
				engine.reload();
				loadStart = System.nanoTime();
				enter(Phase.LOAD);
			}
			case LOAD -> {
				boolean off = packs[index].equalsIgnoreCase("off");
				if (elapsed > 180 || !engine.loading() && elapsed > 2 && !off && !engine.packActive()) {
					entry.addProperty("status", "failed");
					entry.addProperty("error", elapsed > 180 ? "load timeout" : engine.lastError());
					write();
					Aetherium.LOG.error("ShaderAudit: FAILED {}: {}", packs[index], entry.get("error"));
					enter(Phase.NEXT);
				} else if (!engine.loading() && elapsed > 2 && (off || packs[index].equals(engine.loadedPack()))) {
					entry.addProperty("loadSeconds", (System.nanoTime() - loadStart) / 1e9);
					entry.addProperty("status", "capturing");
					enter(Phase.SETTLE);
				}
			}
			case SETTLE -> {
				if (mc.gui.screen() != null) {
					mc.gui.setScreen(null);
					enter(Phase.SETTLE);
					return;
				}
				if (elapsed < 6 || engine.loading()) {
					return;
				}
				screenshot = "audit_" + String.format(java.util.Locale.ROOT, "%02d", index) + "_"
						+ packs[index].replaceAll("[^A-Za-z0-9_.-]", "_") + "_" + SCENES[scene] + ".png";
				captured = false;
				Screenshot.grab(mc.gameDirectory, screenshot, mc.gameRenderer.mainRenderTarget(), 1, message -> captured = true);
				enter(Phase.CAPTURE);
			}
			case CAPTURE -> {
				if (!captured && elapsed < 15) {
					return;
				}
				Path file = mc.gameDirectory.toPath().resolve("screenshots").resolve(screenshot);
				if (Files.isRegularFile(file)) {
					images.add(file.toAbsolutePath().toString());
				} else {
					entry.addProperty("captureError", "Screenshot not written: " + file);
				}
				if (++scene < SCENES.length) {
					setScene(mc);
					enter(Phase.SETTLE);
				} else {
					entry.addProperty("status", engine.packActive() || packs[index].equalsIgnoreCase("off") ? "rendered" : "failed");
					entry.addProperty("error", engine.lastError());
					write();
					Aetherium.LOG.info("ShaderAudit: END {} ({})", packs[index], entry.get("status"));
					enter(Phase.NEXT);
				}
			}
			case DONE -> {
				if (elapsed > 3 && !engine.loading() && !engine.packActive()) mc.stop();
			}
		}
	}

	private void setScene(Minecraft mc) {
		commands(mc, scene == 1 ? "time set 18000" : "time set 6000",
				scene == 2 ? "tp @a 8 193 5 0 -4" : "tp @a 0 202 -20 0 15");
	}

	private static void commands(Minecraft mc, String... commands) {
		var server = mc.getSingleplayerServer();
		if (server != null) {
			server.execute(() -> {
				for (String command : commands) {
					server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
				}
			});
		}
	}

	private void enter(Phase phase) {
		this.phase = phase;
		since = System.nanoTime();
	}

	private void write() {
		try {
			Files.createDirectories(output.toAbsolutePath().getParent());
			Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
		} catch (IOException e) {
			throw new IllegalStateException("Cannot write shader smoke-test report", e);
		}
	}
}
