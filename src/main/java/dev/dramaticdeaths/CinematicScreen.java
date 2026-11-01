package dev.dramaticdeaths;

import dev.dramaticdeaths.mixin.DeathScreenAccessor;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Replaces the vanilla death screen. First plays the cinematic over the
 * slowed-down world, then becomes the dark "aftermath" screen with our own
 * Respawn (or Spectate, in hardcore) and Leave buttons.
 */
public class CinematicScreen extends Screen {
	/** Ignore input at first, so mid-fight clicks don't skip the scene instantly. */
	private static final float SKIP_ALLOWED_AT = 1.5f;
	private static final float DARKNESS = 0.78f;
	private static final float KILLCAM_FROM = 2.3f;
	private static final float KILLCAM_TO = 5.6f;
	private static final Identifier VIGNETTE = Identifier.withDefaultNamespace("textures/misc/vignette.png");
	private static final Identifier FROST = Identifier.withDefaultNamespace("textures/misc/powder_snow_outline.png");

	private enum Particles { NONE, BUBBLES, EMBERS, SNOW, DUST, MAGIC }

	/**
	 * How one death type looks on screen.
	 *
	 * @param vignette how much each colour channel (r, g, b) darkens at the edges
	 * @param flashes  times of full-screen flashes, e.g. explosions and lightning
	 */
	private record Style(int titleColor, int accent, float titleAt,
			int tint, int tintAlpha, float[] vignette, Particles particles, int particleColor,
			float[] flashes, int flashColor) {
	}

	private final Component causeOfDeath;
	private final Component score;
	private final boolean hardcore;
	private final DeathInfo info;
	private final Style style;
	/** What the killer was holding, kept even if it dies or despawns meanwhile. */
	private final ItemStack weapon;
	private final TitleFx.Effect effect;
	private Button respawnButton;
	private Button leaveButton;

	public CinematicScreen(DeathScreen deathScreen) {
		super(Component.empty());
		DeathScreenAccessor accessor = (DeathScreenAccessor) deathScreen;
		this.causeOfDeath = accessor.dramaticdeaths$getCauseOfDeath();
		this.score = accessor.dramaticdeaths$getDeathScore();
		this.hardcore = accessor.dramaticdeaths$isHardcore();
		this.info = DeathCinematic.info();
		this.style = styleFor(info.type());
		this.weapon = info.killer() instanceof LivingEntity living ? living.getMainHandItem().copy() : ItemStack.EMPTY;
		this.effect = TitleFx.effectFor(info, weapon);
	}

	private static Style styleFor(DeathType type) {
		float[] none = {};
		return switch (type) {
			case MOB -> new Style(0xE02020, 0xFF6A3D, 0.6f, 0x300000, 90,
					new float[] {0.0f, 0.8f, 0.8f}, Particles.NONE, 0, new float[] {0.0f}, 0xB00000);
			case PLAYER -> new Style(0xFFC22E, 0xFFFFFF, 0.6f, 0x201000, 70,
					new float[] {0.6f, 0.6f, 0.9f}, Particles.NONE, 0, new float[] {0.0f}, 0xFFFFFF);
			case EXPLOSION -> new Style(0xFF8A1E, 0xFFE07A, 0.7f, 0x281400, 80,
					new float[] {0.7f, 0.8f, 0.9f}, Particles.DUST, 0x8A7F70, new float[] {0.0f}, 0xFFFFFF);
			case FALL -> new Style(0xEDEDED, 0x9C8460, 1.3f, 0x100800, 70,
					new float[] {0.8f, 0.8f, 0.8f}, Particles.DUST, 0x9C8460, new float[] {0.0f}, 0xFFFFFF);
			case DROWN -> new Style(0x5CC8FF, 0xBFF4FF, 1.3f, 0x001A40, 120,
					new float[] {0.9f, 0.6f, 0.2f}, Particles.BUBBLES, 0xBFE8FF, none, 0);
			case FIRE -> new Style(0xFFE6C0, 0xFFD34A, 1.3f, 0x401000, 110,
					new float[] {0.2f, 0.6f, 0.9f}, Particles.EMBERS, 0xFFA030, none, 0);
			case FREEZE -> new Style(0xBFF0FF, 0xFFFFFF, 1.3f, 0x102838, 90,
					new float[] {0.6f, 0.3f, 0.1f}, Particles.SNOW, 0xFFFFFF, none, 0);
			case VOID -> new Style(0xB070FF, 0x5A1FA0, 1.3f, 0x080010, 140,
					new float[] {0.8f, 0.9f, 0.6f}, Particles.MAGIC, 0x9A50E0, none, 0);
			case LIGHTNING -> new Style(0xFFF59A, 0x9AD8FF, 0.5f, 0x101020, 80,
					new float[] {0.6f, 0.6f, 0.4f}, Particles.NONE, 0, new float[] {0.0f, 0.25f}, 0xFFFFFF);
			case CRUSH -> new Style(0xC8C8C8, 0x8A8A8A, 1.0f, 0x101010, 80,
					new float[] {0.8f, 0.8f, 0.8f}, Particles.DUST, 0x707070, new float[] {0.0f}, 0xFFFFFF);
			case MAGIC -> new Style(0xF0E6FF, 0xFF7AD9, 1.3f, 0x200030, 100,
					new float[] {0.5f, 0.9f, 0.4f}, Particles.MAGIC, 0xD080FF, none, 0);
			case STARVE -> new Style(0xC8A060, 0x6A4A20, 1.3f, 0x201408, 110,
					new float[] {0.7f, 0.7f, 0.8f}, Particles.NONE, 0, none, 0);
			case GENERIC -> new Style(0xC81818, 0xFF4040, 1.3f, 0x3A0000, 110,
					new float[] {0.0f, 0.7f, 0.7f}, Particles.NONE, 0, none, 0);
		};
	}

	private boolean aftermath() {
		return DeathCinematic.phase() == DeathCinematic.Phase.AFTERMATH;
	}

	@Override
	protected void init() {
		if (!aftermath()) {
			return;
		}
		int y = (int) (height * 0.62f);
		Component respawnText = Component.translatable(hardcore ? "dramaticdeaths.spectate" : "dramaticdeaths.respawn");
		respawnButton = addRenderableWidget(Button.builder(respawnText, button -> onRespawn())
				.bounds(width / 2 - 100, y, 200, 20).build());
		leaveButton = addRenderableWidget(Button.builder(Component.translatable("dramaticdeaths.leave"), button -> onLeave())
				.bounds(width / 2 - 100, y + 24, 200, 20).build());
		updateButtons();
	}

	private void toAftermath() {
		if (aftermath()) {
			return;
		}
		DeathCinematic.enterAftermath();
		rebuildWidgets();
	}

	@Override
	public void tick() {
		if (!DeathCinematic.validate()) {
			// Respawned, left, or something else took over.
			if (minecraft.gui.screen() == this) {
				minecraft.gui.setScreen(null);
			}
			return;
		}
		if (minecraft.level != null && minecraft.player != null) {
			WorldFx.ambient(minecraft.level, info.type(), minecraft.player.position(), DeathCinematic.seconds(), aftermath() ? 0.4f : 1.0f);
		}
		if (!aftermath() && DeathCinematic.seconds() >= DeathCinematic.DURATION) {
			toAftermath();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		float t = DeathCinematic.seconds();
		if (!aftermath() && t >= DeathCinematic.DURATION) {
			toAftermath();
		}
		DeathCinematic.playDueSounds();

		drawAtmosphere(graphics, t);
		drawScreenParticles(graphics, t);
		drawFlashes(graphics, t);
		drawDarkness(graphics, t);
		drawLetterbox(graphics, t);
		drawTexts(graphics, t);
		if (!aftermath()) {
			drawKillcamCard(graphics, t);
		} else {
			updateButtons();
			drawRespawning(graphics);
			super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		}
	}

	private void drawAtmosphere(GuiGraphicsExtractor graphics, float t) {
		float in = CameraDirector.smooth(t / 1.5f);
		graphics.fill(0, 0, width, height, (int) (in * style.tintAlpha()) << 24 | style.tint());

		float[] v = style.vignette();
		int r = (int) (v[0] * in * 255);
		int g = (int) (v[1] * in * 255);
		int b = (int) (v[2] * in * 255);
		graphics.blit(RenderPipelines.VIGNETTE, VIGNETTE, 0, 0, 0.0f, 0.0f, width, height, width, height, 0xFF000000 | r << 16 | g << 8 | b);

		if (info.type() == DeathType.FREEZE) {
			float frost = CameraDirector.smooth(t / 2.5f);
			graphics.blit(RenderPipelines.GUI_TEXTURED, FROST, 0, 0, 0.0f, 0.0f, width, height, width, height, TitleFx.argb(0xFFFFFF, frost));
		}
	}

	/** Cheap screen-space particles on top; positions come from the index, so no state is kept. */
	private void drawScreenParticles(GuiGraphicsExtractor graphics, float t) {
		if (style.particles() == Particles.NONE) {
			return;
		}
		float strength = CameraDirector.smooth(t / 0.8f);
		for (int i = 0; i < 70; i++) {
			float rx = TitleFx.hash(i, 1);
			float ry = TitleFx.hash(i, 2);
			float speed = 0.4f + TitleFx.hash(i, 3);
			int size = 1 + (int) (TitleFx.hash(i, 4) * 3);
			float x;
			float y;
			float alpha = strength;
			switch (style.particles()) {
				case BUBBLES -> {
					y = height - wrap(ry * height + t * speed * height * 0.35f, height + 10);
					x = rx * width + Mth.sin(t * 2.5f + i) * 6.0f;
					size += 1;
				}
				case EMBERS -> {
					y = height - wrap(ry * height + t * speed * height * 0.25f, height + 10);
					x = rx * width + Mth.sin(t * 1.7f + i * 0.7f) * 10.0f;
					alpha *= 0.55f + 0.45f * Mth.sin(t * 11.0f + i);
				}
				case SNOW -> {
					y = wrap(ry * height + t * speed * height * 0.12f, height + 10);
					x = rx * width + Mth.sin(t * 0.9f + i) * 12.0f;
				}
				case DUST -> {
					y = wrap(ry * height + t * speed * height * 0.06f, height + 10);
					x = rx * width + t * (TitleFx.hash(i, 5) - 0.5f) * 30.0f;
					alpha *= 0.6f;
				}
				default -> {
					float angle = t * (0.6f + speed) + i;
					x = width / 2.0f + Mth.cos(angle) * (rx * width * 0.6f);
					y = height - wrap(ry * height + t * speed * height * 0.15f, height + 10);
					alpha *= 0.5f + 0.5f * Mth.sin(t * 4.0f + i);
				}
			}
			int px = (int) x;
			int py = (int) y;
			graphics.fill(px, py, px + size, py + size, TitleFx.argb(style.particleColor(), alpha));
		}
	}

	private void drawFlashes(GuiGraphicsExtractor graphics, float t) {
		for (float at : style.flashes()) {
			float since = t - at;
			if (since >= 0.0f && since < 0.6f) {
				float alpha = (float) Math.exp(-since * 7.0f);
				graphics.fill(0, 0, width, height, TitleFx.argb(style.flashColor(), alpha * 0.9f));
			}
		}
	}

	/** Darkens towards the end of the cinematic and stays dark behind the buttons. */
	private void drawDarkness(GuiGraphicsExtractor graphics, float t) {
		float dark = DeathCinematic.progress(DeathCinematic.DURATION - 1.0f, DeathCinematic.DURATION) * DARKNESS;
		if (aftermath()) {
			dark = Math.max(dark, DARKNESS * CameraDirector.smooth(DeathCinematic.aftermathSeconds() / 0.5f));
		}
		if (dark > 0.01f) {
			graphics.fill(0, 0, width, height, TitleFx.argb(0x000000, dark));
		}
	}

	private void drawLetterbox(GuiGraphicsExtractor graphics, float t) {
		int bar = (int) (height * 0.12f * CameraDirector.easeOut(t / 0.6f));
		graphics.fill(0, 0, width, bar, 0xFF000000);
		graphics.fill(0, height - bar, width, height, 0xFF000000);
		// Thin accent lines on the bars' inner edges.
		int line = TitleFx.argb(style.titleColor(), 0.35f * CameraDirector.smooth(t / 1.0f));
		graphics.fill(0, bar, width, bar + 1, line);
		graphics.fill(0, height - bar - 1, width, height - bar, line);
		if (!aftermath() && bar > 12) {
			graphics.centeredText(font, Component.translatable("dramaticdeaths.skip"), width / 2, height - bar / 2 - 4,
					TitleFx.argb(0x808080, DeathCinematic.progress(SKIP_ALLOWED_AT, SKIP_ALLOWED_AT + 0.5f)));
		}
	}

	/** Title, tagline and death message; they glide up when the buttons appear. */
	private void drawTexts(GuiGraphicsExtractor graphics, float t) {
		float titleAt = style.titleAt();
		float raise = 0.0f;
		if (aftermath()) {
			// If the scene was skipped early, bring everything in right away.
			titleAt = Math.min(titleAt, t - DeathCinematic.aftermathSeconds());
			raise = CameraDirector.easeOut(DeathCinematic.aftermathSeconds() / 0.7f);
		}
		float titleY = Mth.lerp(raise, height / 2.0f - 16.0f, height * 0.27f);
		float titleSize = Mth.lerp(raise, 4.2f, 3.4f);
		Lines.Picked lines = DeathCinematic.lines();

		TitleFx.draw(graphics, font, lines.title(), effect, info, weapon, width / 2.0f, titleY, titleSize, width * 0.9f,
				t - titleAt, style.titleColor(), 1.0f);

		float taglineAt = titleAt + 0.8f;
		TitleFx.typewriter(graphics, font, lines.tagline(), width / 2.0f, titleY + 22.0f, 1.4f,
				t - taglineAt, 32.0f, 0xE6E6E6, 1.0f);

		float causeIn = DeathCinematic.progress(taglineAt + 1.0f, taglineAt + 1.6f);
		if (causeIn > 0.0f && causeOfDeath != null) {
			graphics.centeredText(font, causeOfDeath, width / 2, (int) (titleY + 44), TitleFx.argb(0xA0A0A0, causeIn));
		}
		if (aftermath() && score != null) {
			float scoreIn = CameraDirector.smooth((DeathCinematic.aftermathSeconds() - 0.3f) / 0.5f);
			if (scoreIn > 0.0f) {
				graphics.centeredText(font, score, width / 2, (int) (titleY + 58), TitleFx.argb(0x808080, scoreIn));
			}
		}
	}

	/** Call of Duty style card: who killed you, their health, weapon and distance. */
	private void drawKillcamCard(GuiGraphicsExtractor graphics, float t) {
		Entity killer = info.killer();
		if (!info.type().isKillcam() || !(killer instanceof LivingEntity living) || t < KILLCAM_FROM || t > KILLCAM_TO) {
			return;
		}
		float in = CameraDirector.easeOut((t - KILLCAM_FROM) / 0.35f);
		float out = CameraDirector.smooth((t - (KILLCAM_TO - 0.3f)) / 0.3f);
		float slide = in * (1.0f - out);
		int w = 180;
		int h = 50;
		int x = (int) Mth.lerp(slide, -w - 10, 14);
		int bar = (int) (height * 0.12f);
		int y = height - bar - h - 10;

		graphics.fill(x, y, x + w, y + h, 0xC80C0C0C);
		graphics.fill(x, y, x + 3, y + h, 0xFFE02020);
		graphics.fill(x + 3, y, x + w, y + 1, 0x40FFFFFF);

		String distance = String.format(Locale.ROOT, "%.1f", living.distanceTo(minecraft.player));
		Component header = TitleFx.cinematic(Component.translatable("dramaticdeaths.killcam").getString() + "  ·  "
				+ Component.translatable("dramaticdeaths.distance", distance).getString().toUpperCase(Locale.ROOT));
		graphics.text(font, header, x + 10, y + 6, 0xFFE04040, false);

		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x + 10, y + 17);
		pose.scale(1.6f, 1.6f);
		graphics.text(font, TitleFx.cinematic(living.getName().getString().toUpperCase(Locale.ROOT)), 0, 0, 0xFFFFFFFF, true);
		pose.popMatrix();

		float health = living.isRemoved() ? 0.0f : Math.max(0.0f, living.getHealth());
		float ratio = Mth.clamp(health / Math.max(1.0f, living.getMaxHealth()), 0.0f, 1.0f);
		int barX = x + 10;
		int barY = y + h - 10;
		int barW = 110;
		graphics.fill(barX, barY, barX + barW, barY + 4, 0xFF3A0808);
		graphics.fill(barX, barY, barX + (int) (barW * ratio), barY + 4, 0xFFE02020);
		Component hp = Component.translatable("dramaticdeaths.health",
				String.format(Locale.ROOT, "%.1f", health), String.format(Locale.ROOT, "%.0f", living.getMaxHealth()));
		graphics.text(font, hp, barX + barW + 6, barY - 2, 0xFFFF8080, false);

		ItemStack weapon = living.getMainHandItem();
		if (!weapon.isEmpty()) {
			pose.pushMatrix();
			pose.translate(x + w - 34, y + 10);
			pose.scale(1.5f, 1.5f);
			graphics.item(weapon, 0, 0);
			pose.popMatrix();
		}
	}

	private void drawRespawning(GuiGraphicsExtractor graphics) {
		if (!DeathCinematic.isRespawning()) {
			return;
		}
		float pulse = 0.6f + 0.4f * Mth.sin(DeathCinematic.seconds() * 5.0f);
		TitleFx.typewriter(graphics, font, Component.translatable("dramaticdeaths.respawning").getString().toUpperCase(Locale.ROOT),
				width / 2.0f, height * 0.62f + 6, 1.6f, 10.0f, 40.0f, 0xFFFFFF, pulse);
	}

	private void updateButtons() {
		if (respawnButton == null) {
			return;
		}
		float since = DeathCinematic.aftermathSeconds();
		float fade = CameraDirector.smooth((since - 0.4f) / 0.5f);
		boolean ready = since > 0.9f && !DeathCinematic.isRespawning();
		for (Button button : new Button[] {respawnButton, leaveButton}) {
			button.setAlpha(fade);
			button.active = ready;
			button.visible = fade > 0.01f && !DeathCinematic.isRespawning();
		}
	}

	private void onRespawn() {
		DeathCinematic.respawn();
		updateButtons();
	}

	private void onLeave() {
		if (hardcore) {
			exitWorld();
			return;
		}
		minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
			if (confirmed) {
				exitWorld();
			} else {
				minecraft.gui.setScreen(this);
			}
		}, Component.translatable("deathScreen.quit.confirm"), CommonComponents.EMPTY,
				Component.translatable("deathScreen.titleScreen"), CommonComponents.GUI_CANCEL));
	}

	/** Same as the vanilla death screen's "Title Screen" button. */
	private void exitWorld() {
		DeathCinematic.reset();
		if (minecraft.level != null) {
			minecraft.level.disconnect(ClientLevel.DEFAULT_QUIT_MESSAGE);
		}
		minecraft.disconnectWithSavingScreen();
		minecraft.gui.setScreen(new TitleScreen());
	}

	private static float wrap(float value, float range) {
		float wrapped = value % range;
		return wrapped < 0.0f ? wrapped + range : wrapped;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// No blur or menu background: the world must stay visible.
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!aftermath()) {
			trySkip();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (!aftermath()) {
			trySkip();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void trySkip() {
		if (DeathCinematic.seconds() >= SKIP_ALLOWED_AT) {
			toAftermath();
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
