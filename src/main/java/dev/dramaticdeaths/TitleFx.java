package dev.dramaticdeaths;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import org.joml.Matrix3x2fStack;

/**
 * The big death title. The letters stay solid and readable; instead of
 * wobbling, something physical happens to them that matches the death: they
 * burn, sink into water, get crushed by an anvil, bitten, sliced, blown apart.
 *
 * <p>Everything is a pure function of time, so nothing is stored per letter.
 * Positions are in "title units": one unit is one pixel of the unscaled font,
 * with (0, 0) in the middle of the word.
 */
public final class TitleFx {
	/** Bebas Neue, bundled in assets/dramaticdeaths/font. */
	public static final FontDescription CINEMATIC =
			new FontDescription.Resource(Identifier.fromNamespaceAndPath("dramaticdeaths", "cinematic"));

	private static final Identifier FIRE = Identifier.withDefaultNamespace("textures/block/fire_0.png");
	private static final Identifier WATER = Identifier.withDefaultNamespace("textures/block/water_still.png");
	private static final Identifier ICE = Identifier.withDefaultNamespace("textures/block/ice.png");
	private static final Identifier[] EXPLOSION = new Identifier[16];

	static {
		for (int i = 0; i < EXPLOSION.length; i++) {
			EXPLOSION[i] = Identifier.withDefaultNamespace("textures/particle/explosion_" + i + ".png");
		}
	}

	public enum Effect {
		/** A blade slices through the letters and the top halves slide off. */
		SLASH,
		/** Three claw cuts rip through the letters. */
		CLAWS,
		/** Arrows (or a trident) fly in and stick into the letters. */
		ARROWS,
		/** A TNT lands, blinks, and blows the letters apart. */
		BLAST,
		/** The letters fall from the sky and crash into the ground. */
		IMPACT,
		/** The letters fall into water and sink. */
		SINK,
		/** The letters catch fire and burn away from the bottom. */
		BURN,
		/** The letters freeze into ice blocks one by one. */
		FROZEN,
		/** The letters slip into a rift in the void. */
		ABYSS,
		/** A lightning bolt strikes the word. */
		STRIKE,
		/** A splash potion smashes on the word and stains it. */
		POTION,
		/** An anvil drops on the word and squashes it. */
		CRUSH,
		/** Something takes bites out of the letters. */
		EATEN,
		/** Plain and heavy, with one glint across. */
		CLEAN
	}

	/** Title time of the previous frame, to fire each sound exactly once. */
	private static float lastT = -1.0f;

	private TitleFx() {
	}

	public static Effect effectFor(DeathInfo info, ItemStack weapon) {
		return switch (info.type()) {
			case MOB, PLAYER -> switch (info.cause()) {
				case "arrow", "trident" -> Effect.ARROWS;
				default -> weapon.isEmpty() ? Effect.CLAWS : Effect.SLASH;
			};
			case EXPLOSION -> Effect.BLAST;
			case FALL -> Effect.IMPACT;
			case DROWN -> Effect.SINK;
			case FIRE -> Effect.BURN;
			case FREEZE -> Effect.FROZEN;
			case VOID -> Effect.ABYSS;
			case LIGHTNING -> Effect.STRIKE;
			case CRUSH -> Effect.CRUSH;
			case MAGIC -> Effect.POTION;
			case STARVE -> Effect.EATEN;
			case GENERIC -> Effect.CLEAN;
		};
	}

	public static Component cinematic(String text) {
		return Component.literal(text).withStyle(style -> style.withFont(CINEMATIC));
	}

	/**
	 * @param t       seconds since the title started appearing
	 * @param size    scale of the whole word; the text is shrunk to fit if needed
	 * @param opacity extra fade applied to everything
	 */
	public static void draw(GuiGraphicsExtractor graphics, Font font, String text, Effect effect, DeathInfo info,
			ItemStack weapon, float centerX, float centerY, float size, float maxWidth, float t, int color, float opacity) {
		// Time went backwards: a new death started.
		float previous = t < lastT - 0.05f ? -1.0f : lastT;
		lastT = t;
		if (t <= 0.0f || text.isEmpty()) {
			return;
		}
		new Title(graphics, font, text, info, weapon, centerX, centerY, size, maxWidth, t, previous, color, opacity).draw(effect);
	}

	/** Typewriter text with a blinking cursor while it types. */
	public static void typewriter(GuiGraphicsExtractor graphics, Font font, String text, float centerX, float y,
			float scale, float t, float charsPerSecond, int color, float opacity) {
		if (t <= 0.0f || text.isEmpty()) {
			return;
		}
		int shown = Math.min(text.length(), (int) (t * charsPerSecond));
		boolean typing = shown < text.length();
		Component full = cinematic(text);
		Component part = cinematic(text.substring(0, shown) + (typing && ((int) (t * 8)) % 2 == 0 ? "_" : ""));

		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(centerX, y);
		pose.scale(scale, scale);
		// Left-aligned to where the finished line will start, so it doesn't jump around.
		graphics.text(font, part, -font.width(full) / 2, 0, argb(color, opacity), true);
		pose.popMatrix();
	}

	/** One frame of the title. */
	private static final class Title {
		// Timings in seconds of title time.
		private static final float CUT = 0.6f;
		private static final float TNT_DROP = 0.35f;
		private static final float TNT_LAND = 0.6f;
		private static final float BOOM = 1.9f;
		private static final float ANVIL_DROP = 0.35f;
		private static final float ANVIL_LAND = 1.0f;
		private static final float BOLT = 0.5f;
		private static final float POTION_HIT = 0.85f;

		private final GuiGraphicsExtractor g;
		private final Font font;
		private final Matrix3x2fStack pose;
		private final DeathInfo info;
		private final ItemStack weapon;
		private final int n;
		private final Component[] letters;
		/** Centre x and width of every letter. */
		private final float[] cx;
		private final float[] w;
		private final boolean[] blank;
		private final float left;
		private final float right;
		/** Vertical extent of the glyphs, with a little room for the shadow. */
		private final float top;
		private final float bottom;
		private final float height;
		private final float scale;
		private float ox;
		private float oy;
		private final float t;
		private final float prev;
		private final int color;
		private final float opacity;

		Title(GuiGraphicsExtractor g, Font font, String text, DeathInfo info, ItemStack weapon, float centerX, float centerY,
				float size, float maxWidth, float t, float prev, int color, float opacity) {
			this.g = g;
			this.font = font;
			this.pose = g.pose();
			this.info = info;
			this.weapon = weapon;
			this.t = t;
			this.prev = prev;
			this.color = color;
			this.opacity = opacity;
			this.ox = centerX;
			this.oy = centerY;

			n = text.length();
			letters = new Component[n];
			cx = new float[n];
			w = new float[n];
			blank = new boolean[n];
			float spacing = 1.0f;
			float total = 0.0f;
			for (int i = 0; i < n; i++) {
				letters[i] = cinematic(String.valueOf(text.charAt(i)));
				w[i] = font.width(letters[i]);
				blank[i] = Character.isWhitespace(text.charAt(i));
				total += w[i] + (i > 0 ? spacing : 0.0f);
			}
			scale = Math.min(size, maxWidth / Math.max(1.0f, total));
			float x = -total / 2.0f;
			for (int i = 0; i < n; i++) {
				cx[i] = x + w[i] / 2.0f;
				x += w[i] + spacing;
			}
			left = -total / 2.0f;
			right = total / 2.0f;
			top = -font.lineHeight / 2.0f - 0.5f;
			bottom = font.lineHeight / 2.0f + 0.5f;
			height = bottom - top;
		}

		void draw(Effect effect) {
			float shake = switch (effect) {
				case SLASH, CLAWS -> impulse(CUT, 1.0f);
				case ARROWS -> impulse(arrowHit(0), 0.6f) + impulse(arrowHit(1), 0.6f) + impulse(arrowHit(2), 0.6f);
				case BLAST -> impulse(BOOM, 4.0f);
				case CRUSH -> impulse(ANVIL_LAND, 3.5f);
				case STRIKE -> impulse(BOLT, 2.5f);
				case POTION -> impulse(POTION_HIT, 0.8f);
				case IMPACT -> {
					float sum = 0.0f;
					for (int i = 0; i < n; i++) {
						sum += blank[i] ? 0.0f : impulse(landTime(i), 0.5f);
					}
					yield Math.min(sum, 2.0f);
				}
				default -> 0.0f;
			};
			if (shake > 0.01f) {
				ox += (hash(1, (int) (t * 40)) - 0.5f) * 2.0f * shake * scale;
				oy += (hash(2, (int) (t * 40)) - 0.5f) * 2.0f * shake * scale;
			}

			pose.pushMatrix();
			pose.translate(ox, oy);
			pose.scale(scale, scale);
			switch (effect) {
				case SLASH -> slash(false);
				case CLAWS -> slash(true);
				case ARROWS -> arrows();
				case BLAST -> blast();
				case IMPACT -> impact();
				case SINK -> sink();
				case BURN -> burn();
				case FROZEN -> frozen();
				case ABYSS -> abyss();
				case STRIKE -> strike();
				case POTION -> potion();
				case CRUSH -> crush();
				case EATEN -> eaten();
				case CLEAN -> clean();
			}
			pose.popMatrix();
		}

		// ---------------------------------------------------------------- effects

		private void clean() {
			float settle = 1.0f + 0.06f * (1.0f - easeOut(t / 1.6f));
			for (int i = 0; i < n; i++) {
				float a = appear(i);
				glyph(i, 0.0f, 2.5f * (1.0f - easeOut(a)), 0.0f, settle, settle, 0.0f, color, a);
			}
			// One glint sweeping across.
			float p = (t - 1.1f) / 0.7f;
			if (p > 0.0f && p < 1.0f) {
				float x = Mth.lerp(p, left - 6.0f, right + 6.0f);
				if (clip(x - 2.5f, top - 2.0f, x + 2.5f, bottom + 2.0f)) {
					for (int i = 0; i < n; i++) {
						glyph(i, 0.0f, 0.0f, 0.0f, settle, settle, 0.0f, 0xFFFFFF, 0.85f * Mth.sin(p * Mth.PI));
					}
					unclip();
				}
			}
		}

		/** One blade cut (or three claw cuts) sweeping left to right; the slices slide apart. */
		private void slash(boolean claws) {
			float[] cuts = claws ? new float[] {0.3f, 0.52f, 0.74f} : new float[] {0.5f};
			float sweep = 0.14f;
			for (int i = 0; i < n; i++) {
				if (blank[i]) {
					continue;
				}
				float a = clamp01((t - i * 0.03f) / 0.14f);
				float s = 1.0f + 0.35f * (1.0f - easeOut(a));
				float cutAt = CUT + sweep * (cx[i] - left) / Math.max(1.0f, right - left);
				if (t < cutAt) {
					glyph(i, 0.0f, 0.0f, 0.0f, s, s, 0.0f, color, a);
					continue;
				}
				float e = easeOut((t - cutAt) / 0.4f);
				for (int j = 0; j <= cuts.length; j++) {
					float y0 = j == 0 ? top - 2.0f : top + cuts[j - 1] * height;
					float y1 = j == cuts.length ? bottom + 2.0f : top + cuts[j] * height;
					float dx;
					float dy = 0.0f;
					if (claws) {
						dx = (j % 2 == 0 ? -1.0f : 1.0f) * (0.5f + 0.7f * hash(i, 10 + j)) * e;
					} else {
						dx = j == 0 ? 2.6f * e : 0.0f;
						dy = j == 0 ? 0.7f * e : 0.0f;
					}
					if (clip(cx[i] - w[i] / 2.0f - 3.0f + dx, y0 + dy, cx[i] + w[i] / 2.0f + 3.0f + dx, y1 + dy)) {
						glyph(i, dx, dy, color, 1.0f);
						unclip();
					}
				}
				// Bright fresh cut edges, and a spray of red.
				float glint = 1.0f - clamp01((t - cutAt) / 0.5f);
				for (float cut : cuts) {
					if (glint > 0.0f) {
						rect(cx[i] - w[i] / 2.0f - 0.5f, top + cut * height - 0.2f, w[i] + 1.0f, 0.4f, argb(0xFFFFFF, glint * 0.9f));
					}
				}
				for (int k = 0; k < 6; k++) {
					debris(cutAt, 0.7f, cx[i], top + cuts[k % cuts.length] * height,
							(hash(i, 20 + k) - 0.3f) * 30.0f, -(5.0f + hash(i, 30 + k) * 15.0f), 70.0f, 0.6f, 0xC81414);
				}
			}

			// The streaks of the blade.
			float p = (t - CUT) / sweep;
			if (p > 0.0f) {
				float fade = p < 1.0f ? 1.0f : 1.0f - (t - CUT - sweep) / 0.25f;
				float head = Mth.lerp(Math.min(p, 1.0f), left - 8.0f, right + 8.0f);
				if (fade > 0.0f) {
					for (float cut : cuts) {
						float y = top + cut * height;
						rect(left - 8.0f, y - 0.8f, head - left + 8.0f, 1.6f, argb(0xFF3030, 0.3f * fade));
						rect(left - 8.0f, y - 0.25f, head - left + 8.0f, 0.5f, argb(0xFFFFFF, fade));
					}
				}
				if (!claws && !weapon.isEmpty() && p < 1.6f) {
					// The killer's weapon swinging through, pointing along the cut.
					float x = Mth.lerp(p, left - 8.0f, right + 8.0f);
					item(weapon, x, top + cuts[0] * height, 15.0f, Mth.PI / 4.0f);
				}
			}
			if (claws) {
				cue(CUT, SoundEvents.PLAYER_ATTACK_STRONG, 0.7f, 1.0f);
				cue(CUT + 0.05f, SoundEvents.PLAYER_ATTACK_SWEEP, 1.3f, 0.7f);
			} else {
				cue(CUT, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 1.0f);
			}
		}

		private float arrowHit(int k) {
			return 0.7f + k * 0.28f;
		}

		/** Arrows thunk into letters one after another and knock them back. */
		private void arrows() {
			boolean trident = "trident".equals(info.cause());
			ItemStack stack = new ItemStack(trident ? Items.TRIDENT : Items.ARROW);
			int[] targets = spread(trident ? new float[] {0.5f} : new float[] {0.2f, 0.55f, 0.85f});
			float size = trident ? 20.0f : 13.0f;

			for (int i = 0; i < n; i++) {
				float knock = 0.0f;
				for (int k = 0; k < targets.length; k++) {
					if (targets[k] == i && t >= arrowHit(k)) {
						knock += easeOut((t - arrowHit(k)) / 0.12f);
					}
				}
				// Knocked back around its base, like a sign that got hit.
				glyph(i, 1.2f * knock, 0.0f, 0.12f * knock, 1.0f, 1.0f, bottom, color, appear(i));
			}
			for (int k = 0; k < targets.length; k++) {
				int target = targets[k];
				if (target < 0) {
					continue;
				}
				float hit = arrowHit(k);
				float flight = 0.16f;
				if (t < hit - flight) {
					continue;
				}
				float tipX = cx[target] + (hash(k, 40) - 0.5f) * w[target] * 0.3f;
				float tipY = top + (0.3f + 0.4f * hash(k, 41)) * height;
				float angle = Mth.PI / 4.0f;
				if (t < hit) {
					float p = (t - (hit - flight)) / flight;
					tipY -= 5.0f * (1.0f - p) * (1.0f - p);
					tipX = Mth.lerp(p, left - 90.0f, tipX);
				} else {
					tipX += 1.2f * easeOut((t - hit) / 0.12f);
					// The shaft quivers for a moment after it sticks.
					angle += 0.09f * (float) Math.exp(-(t - hit) * 6.0f) * Mth.sin((t - hit) * 55.0f);
					for (int c = 0; c < 6; c++) {
						debris(hit, 0.6f, tipX, tipY, -(5.0f + hash(k * 6 + c, 42) * 18.0f), -(4.0f + hash(k * 6 + c, 43) * 10.0f),
								70.0f, 0.5f, color);
					}
				}
				// The sprite points up-right; turned 45 degrees it points right. Its tip sits ~0.32 size from the centre.
				item(stack, tipX + 1.5f - size * 0.32f, tipY, size, angle);
				cue(hit - flight, trident ? SoundEvents.TRIDENT_THROW.value() : SoundEvents.ARROW_SHOOT, 1.0f, 0.8f);
				cue(hit, trident ? SoundEvents.TRIDENT_HIT : SoundEvents.ARROW_HIT, 0.9f + 0.2f * k, 1.0f);
			}
		}

		/** A TNT drops in front of the word, blinks faster and faster, then blows the letters apart. */
		private void blast() {
			float mid = (top + bottom) / 2.0f;
			for (int i = 0; i < n; i++) {
				float a = appear(i);
				if (t < BOOM) {
					glyph(i, 0.0f, 0.0f, color, a);
					continue;
				}
				float e = easeOutExpo((t - BOOM) / 0.6f);
				float side = cx[i] >= 0.0f ? 1.0f : -1.0f;
				float near = 1.0f / (1.0f + Math.abs(cx[i]) / 25.0f);
				float dx = side * (3.0f + 7.0f * near + 3.0f * hash(i, 50)) * e;
				float dy = -(1.5f + 6.0f * near * hash(i, 51)) * e;
				float rot = side * (0.06f + 0.22f * hash(i, 52)) * e;
				float burnt = clamp01((t - BOOM) / 0.15f);
				glyph(i, dx, dy, rot, 1.0f, 1.0f, 0.0f, mix(color, 0x2A1E16, 0.65f * burnt), 1.0f);
				smoke(i, cx[i] + dx, top + dy, BOOM + 0.2f);
			}

			ItemStack tnt = new ItemStack(Items.TNT);
			float size = 13.0f;
			if (t >= TNT_DROP && t < BOOM) {
				float p = clamp01((t - TNT_DROP) / (TNT_LAND - TNT_DROP));
				float y = Mth.lerp(p * p, top - 60.0f, bottom - size * 0.35f);
				item(tnt, 0.0f, y, size, 0.0f);
				float lit = t - TNT_LAND;
				if (lit > 0.0f && ((int) (lit * (3.0f + 4.0f * lit))) % 2 == 0) {
					rect(-size * 0.4f, y - size * 0.4f, size * 0.8f, size * 0.8f, argb(0xFFFFFF, 0.45f));
				}
			}
			if (t >= BOOM) {
				float since = t - BOOM;
				rect(-2000.0f, -2000.0f, 4000.0f, 4000.0f, argb(0xFFFFFF, 0.8f * (float) Math.exp(-since * 8.0f)));
				// A few explosion puffs, the same sprites the game uses.
				for (int k = 0; k < 5; k++) {
					float start = k * 0.07f;
					int frame = (int) ((since - start) / 0.5f * 16.0f);
					if (frame >= 0 && frame < 16) {
						float s = k == 0 ? 36.0f : 20.0f;
						float x = k == 0 ? 0.0f : (hash(k, 53) - 0.5f) * (right - left) * 0.7f;
						float y = mid + (k == 0 ? 0.0f : (hash(k, 54) - 0.5f) * 12.0f);
						sprite(EXPLOSION[frame], x - s / 2.0f, y - s / 2.0f, s, s, 0, 1, 0xFFFFFFFF);
					}
				}
				for (int k = 0; k < 30; k++) {
					debris(BOOM, 1.4f, 0.0f, mid, (hash(k, 55) - 0.5f) * 120.0f, -(20.0f + hash(k, 56) * 60.0f), 120.0f,
							0.5f + hash(k, 57) * 0.8f, hash(k, 58) > 0.5f ? 0x2A1E16 : 0xFF8A1E);
				}
			}
			cue(TNT_LAND, SoundEvents.TNT_PRIMED, 1.0f, 1.0f);
			cue(BOOM, SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 1.0f);
		}

		private float landTime(int i) {
			return 0.15f + hash(i, 60) * 0.45f;
		}

		/** Letters drop out of the sky in random order and crash into the ground; some crack. */
		private void impact() {
			float fall = 0.3f;
			float firstLand = Float.MAX_VALUE;
			for (int i = 0; i < n; i++) {
				if (!blank[i]) {
					firstLand = Math.min(firstLand, landTime(i));
				}
			}
			float ground = clamp01((t - firstLand) / 0.1f);
			if (ground > 0.0f) {
				rect(left - 6.0f, bottom + 0.6f, right - left + 12.0f, 0.5f, argb(0x8A7A66, 0.7f * ground));
			}
			for (int i = 0; i < n; i++) {
				if (blank[i]) {
					continue;
				}
				float land = landTime(i);
				if (t < land - fall) {
					continue;
				}
				if (t < land) {
					float p = (t - (land - fall)) / fall;
					glyph(i, 0.0f, -70.0f * (1.0f - p * p), color, 1.0f);
					continue;
				}
				float since = t - land;
				// One hard squash on impact, then it stays put.
				float squash = since < 0.12f ? Mth.sin(since / 0.12f * Mth.PI) : 0.0f;
				float sx = 1.0f + 0.12f * squash;
				float sy = 1.0f - 0.28f * squash;
				if (hash(i, 61) > 0.55f) {
					// Cracked in two: the right half sinks a bit.
					float e = easeOut(since / 0.15f);
					float split = cx[i] + (hash(i, 62) - 0.5f) * w[i] * 0.3f;
					if (clip(cx[i] - w[i] / 2.0f - 3.0f, top - 3.0f, split, bottom + 3.0f)) {
						glyph(i, 0.0f, 0.0f, 0.0f, sx, sy, bottom, color, 1.0f);
						unclip();
					}
					if (clip(split, top - 3.0f, cx[i] + w[i] / 2.0f + 3.0f, bottom + 4.0f)) {
						glyph(i, 0.5f * e, 0.8f * e, 0.0f, sx, sy, bottom, color, 1.0f);
						unclip();
					}
					int crack = argb(0x3A322A, 0.8f * e);
					line(split, bottom + 0.8f, split - 1.0f, bottom + 2.0f, 0.4f, crack);
					line(split - 1.0f, bottom + 2.0f, split + 0.5f, bottom + 3.2f, 0.4f, crack);
				} else {
					float tilt = (hash(i, 63) - 0.5f) * 0.14f * easeOut(since / 0.1f);
					glyph(i, 0.0f, 0.0f, tilt, sx, sy, bottom, color, 1.0f);
				}
				// Dust kicked out sideways.
				for (int k = 0; k < 4; k++) {
					float life = 0.8f;
					if (since < life) {
						float side = k % 2 == 0 ? -1.0f : 1.0f;
						float x = cx[i] + side * (w[i] / 2.0f + 10.0f * easeOut(since / life) * (0.4f + 0.6f * hash(i * 4 + k, 64)));
						float y = bottom - 0.5f - 2.0f * since * hash(i * 4 + k, 65);
						square(x, y, 1.0f + 2.0f * since, 0x9C8C78, 0.5f * (1.0f - since / life));
					}
				}
				if (i % 2 == 0) {
					cue(land, SoundEvents.STONE_PLACE, 0.6f + 0.3f * hash(i, 66), 0.6f);
				}
			}
			cue(firstLand, SoundEvents.PLAYER_BIG_FALL, 0.8f, 1.0f);
		}

		/** The letters fall into water one by one and sink just under the surface. */
		private void sink() {
			float surface = top - 0.5f;
			float drop = 5.5f;
			float gravity = 500.0f;
			float toWater = Mth.sqrt(2.0f * drop / gravity);
			float entryDy = surface - bottom;
			int water = 0x2F6FD0;
			float waterIn = clamp01(t / 0.25f);
			float wl = left - 7.0f;
			float wr = right + 7.0f;

			for (int i = 0; i < n; i++) {
				if (blank[i]) {
					continue;
				}
				float start = 0.1f + i * 0.085f;
				if (t < start) {
					continue;
				}
				float since = t - start;
				float dy;
				float rot = 0.0f;
				if (since < toWater) {
					dy = entryDy - drop + 0.5f * gravity * since * since;
				} else {
					// Water drag: quickly slows down and settles just under the surface.
					float inWater = since - toWater;
					dy = Mth.lerp(1.0f - (float) Math.exp(-inWater * 6.0f), entryDy, 1.0f);
					rot = (hash(i, 70) - 0.5f) * 0.12f * (1.0f - (float) Math.exp(-inWater * 3.0f));
				}
				// Above the water: as it is.
				if (clip(cx[i] - w[i] / 2.0f - 4.0f, -500.0f, cx[i] + w[i] / 2.0f + 4.0f, surface)) {
					glyph(i, 0.0f, dy, rot, 1.0f, 1.0f, 0.0f, color, 1.0f);
					unclip();
				}
				// Under the water: tinted, and bent a little by the surface.
				int under = mix(color, water, 0.45f);
				for (float y = surface; y < bottom + dy + 2.0f; y += 1.0f) {
					float bend = 0.3f * Mth.sin(t * 2.0f + y * 0.8f);
					if (clip(cx[i] - w[i] / 2.0f - 4.0f + bend, y, cx[i] + w[i] / 2.0f + 4.0f + bend, y + 1.0f)) {
						glyph(i, bend, dy, rot, 1.0f, 1.0f, 0.0f, under, 0.9f);
						unclip();
					}
				}
				float entry = start + toWater;
				// Splash and ripples where it went in.
				for (int k = 0; k < 7; k++) {
					debris(entry, 0.6f, cx[i] + (hash(i * 7 + k, 71) - 0.5f) * w[i], surface,
							(hash(i * 7 + k, 72) - 0.5f) * 25.0f, -(10.0f + hash(i * 7 + k, 73) * 18.0f), 90.0f, 0.6f, 0xCFE8FF);
				}
				float ripple = t - entry;
				if (ripple > 0.0f && ripple < 0.6f) {
					float spread = w[i] / 2.0f + 8.0f * ripple / 0.6f;
					int c = argb(0xFFFFFF, 0.8f * (1.0f - ripple / 0.6f));
					rect(cx[i] - spread - 2.0f, surface - 0.4f, 2.0f, 0.4f, c);
					rect(cx[i] + spread, surface - 0.4f, 2.0f, 0.4f, c);
				}
				// Bubbles escaping to the surface.
				for (int k = 0; k < 2; k++) {
					float age = t - entry - k * 0.65f;
					if (age > 0.0f) {
						int cycle = (int) (age / 1.3f);
						float p = (age % 1.3f) / 1.3f;
						float x = cx[i] + (hash(i * 2 + k, cycle + 74) - 0.5f) * w[i] * 0.8f + 0.3f * Mth.sin(age * 8.0f);
						square(x, Mth.lerp(p, bottom + dy - 1.0f, surface + 0.5f), 0.8f, 0xDDF4FF, 0.7f);
					}
				}
				if (i % 3 == 0) {
					cue(entry, i == 0 ? SoundEvents.PLAYER_SPLASH_HIGH_SPEED : SoundEvents.PLAYER_SPLASH, 1.0f + 0.3f * hash(i, 75), 0.5f);
				}
			}

			// The water itself, over the sunken letters.
			float depth = bottom + 1.0f - surface;
			rect(wl, surface, wr - wl, depth, argb(water, 0.28f * waterIn));
			for (int k = 0; k < 5; k++) {
				rect(wl, surface + depth + k * 0.6f, wr - wl, 0.6f, argb(water, 0.28f * waterIn * (1.0f - (k + 1) / 6.0f)));
			}
			int frame = (int) (t * 10.0f) % 32;
			for (float x = wl; x < wr; x += 6.0f) {
				sprite(WATER, x, surface - 0.8f, Math.min(6.0f, wr - x), 1.6f, frame, 32, argb(0x3F76E4, 0.9f * waterIn));
			}
			rect(wl, surface - 0.8f, wr - wl, 0.3f, argb(0xCFEFFF, 0.7f * waterIn));
			cue(0.1f + 0.085f * n + 0.4f, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, 0.8f, 0.8f);
		}

		/** Fire catches each letter and burns it away from the bottom, leaving charred, glowing edges. */
		private void burn() {
			float maxBurn = 0.38f;
			int columns = 5;
			float firstIgnite = ignite(0);
			// Flames go behind the letters.
			for (int i = 0; i < n; i++) {
				if (blank[i] || t < ignite(i)) {
					continue;
				}
				float since = t - ignite(i);
				float front = bottom - burnt(since) * maxBurn * height;
				float strength = clamp01(since / 0.25f) * (1.0f - 0.45f * clamp01((since - 2.4f) / 1.0f));
				float fh = height * 1.25f * strength;
				float fw = w[i] * 1.35f;
				int frame = ((int) (t * 24.0f) + i * 7) % 32;
				sprite(FIRE, cx[i] - fw / 2.0f, front + 1.5f - fh, fw, fh, frame, 32, 0xE6FFFFFF);
			}
			for (int i = 0; i < n; i++) {
				if (blank[i]) {
					continue;
				}
				float a = appear(i);
				float since = t - ignite(i);
				if (since <= 0.0f) {
					glyph(i, 0.0f, 0.0f, color, a);
					continue;
				}
				int hot = mix(color, 0xFF8A2A, 0.75f * clamp01(since / 1.0f));
				float charH = 1.8f * clamp01(since / 0.6f);
				float colW = (w[i] + 2.0f) / columns;
				float x0 = cx[i] - w[i] / 2.0f - 1.0f;
				float frontSum = 0.0f;
				for (int c = 0; c < columns; c++) {
					// Each column burns at its own pace, which makes a ragged edge.
					float f = burnt(since) * maxBurn * (0.75f + 0.5f * hash(i * columns + c, 80));
					float fy = bottom - f * height;
					frontSum += fy;
					float c0 = x0 + c * colW - (c == 0 ? 1.0f : 0.0f);
					float c1 = x0 + (c + 1) * colW + (c == columns - 1 ? 1.0f : 0.0f);
					if (clip(c0, top - 2.0f, c1, fy - charH)) {
						glyph(i, 0.0f, 0.0f, hot, a);
						unclip();
					}
					if (clip(c0, fy - charH, c1, fy)) {
						glyph(i, 0.0f, 0.0f, 0x1E130C, a);
						unclip();
					}
					if (clip(c0, fy - 0.6f, c1, fy)) {
						glyph(i, 0.0f, 0.0f, mix(0xFFD040, 0xFF4010, 0.5f + 0.5f * Mth.sin(t * 6.0f + c + i)), a);
						unclip();
					}
				}
				float front = frontSum / columns;
				// Embers rising off the burning edge.
				for (int k = 0; k < 4; k++) {
					float age = since + k * 0.225f;
					int cycle = (int) (age / 0.9f);
					float p = (age % 0.9f) / 0.9f;
					float x = cx[i] + (hash(i * 4 + k, cycle + 81) - 0.5f) * w[i] + Mth.sin(p * 5.0f + k) * 0.8f;
					square(x, front - p * 14.0f, 0.5f, mix(0xFFE060, 0xFF3000, p), 1.0f - p);
				}
				smoke(i, cx[i], top - 1.0f, ignite(i) + 1.0f);
			}
			cue(firstIgnite, SoundEvents.FIRECHARGE_USE, 1.0f, 0.8f);
			cue(firstIgnite + 0.5f, SoundEvents.GENERIC_BURN, 0.8f, 0.8f);
			cue(firstIgnite + 1.4f, SoundEvents.FIRE_AMBIENT, 1.0f, 1.0f);
		}

		private float ignite(int i) {
			return 0.45f + i * 0.06f + hash(i, 82) * 0.12f;
		}

		private static float burnt(float since) {
			return easeOut(since / 2.6f);
		}

		/** Ice blocks grow around the letters one by one. */
		private void frozen() {
			for (int i = 0; i < n; i++) {
				if (blank[i]) {
					continue;
				}
				float a = appear(i);
				float at = 0.5f + i * 0.08f;
				float p = easeOut((t - at) / 0.45f);
				glyph(i, 0.0f, 0.0f, mix(color, 0xEFFFFF, p), a);
				if (p <= 0.0f) {
					continue;
				}
				float bx0 = cx[i] - w[i] / 2.0f - 1.2f;
				float bx1 = cx[i] + w[i] / 2.0f + 1.2f;
				float by0 = top - 0.8f;
				float by1 = bottom + 0.3f;
				float grown = Mth.lerp(p, by1, by0);
				if (clip(bx0, grown, bx1, by1)) {
					sprite(ICE, bx0, by0, bx1 - bx0, by1 - by0, 0, 1, argb(0xDDF4FF, 0.85f));
					rect(bx0, by0, bx1 - bx0, 0.35f, argb(0xFFFFFF, 0.7f));
					rect(bx0, by0, 0.35f, by1 - by0, argb(0xFFFFFF, 0.5f));
					rect(bx1 - 0.35f, by0, 0.35f, by1 - by0, argb(0x7FB8D8, 0.6f));
					rect(bx0, by1 - 0.35f, bx1 - bx0, 0.35f, argb(0x7FB8D8, 0.6f));
					unclip();
				}
				rect(bx0, grown - 0.2f, bx1 - bx0, 0.4f, argb(0xFFFFFF, (1.0f - p) * 0.9f));
				// The odd twinkle on the ice.
				float tw = (t + hash(i, 90) * 1.7f) % 1.7f;
				if (p >= 1.0f && tw < 0.25f) {
					int cycle = (int) ((t + hash(i, 90) * 1.7f) / 1.7f);
					float size = Mth.sin(tw / 0.25f * Mth.PI) * 1.4f;
					float x = Mth.lerp(hash(i, cycle + 91), bx0 + 1.0f, bx1 - 1.0f);
					float y = Mth.lerp(hash(i, cycle + 92), by0 + 1.0f, by1 - 1.0f);
					rect(x - size / 2.0f, y - 0.15f, size, 0.3f, argb(0xFFFFFF, 0.9f));
					rect(x - 0.15f, y - size / 2.0f, 0.3f, size, argb(0xFFFFFF, 0.9f));
				}
				if (i % 2 == 0) {
					cue(at, SoundEvents.GLASS_PLACE, 1.4f + 0.3f * hash(i, 93), 0.5f);
				}
			}
			cue(0.5f, SoundEvents.POWDER_SNOW_PLACE, 0.8f, 1.0f);
		}

		/** A rift opens under the word and the letters slip into it. */
		private void abyss() {
			float rift = bottom + 0.5f;
			float open = easeOut((t - 0.3f) / 0.5f);
			float half = (right - left) / 2.0f + 8.0f;
			for (int k = 0; k < 5; k++) {
				float alpha = 0.12f * (5 - k) / 5.0f * open;
				rect(-half * open, rift - (k + 1) * 0.8f, 2.0f * half * open, 0.8f, argb(0x7A2AD0, alpha));
				rect(-half * open, rift + k * 0.8f, 2.0f * half * open, 0.8f, argb(0x7A2AD0, alpha));
			}
			for (int i = 0; i < n; i++) {
				if (blank[i]) {
					continue;
				}
				float slip = 0.6f + hash(i, 100) * 1.2f;
				float e = smooth((t - slip) / 1.4f);
				float rot = (hash(i, 101) - 0.5f) * 0.1f * e;
				if (clip(left - 50.0f, top - 30.0f, right + 50.0f, rift)) {
					glyph(i, 0.0f, e * height * 0.35f, rot, 1.0f, 1.0f, 0.0f, mix(color, 0x1A0830, 0.3f * e), appear(i));
					unclip();
				}
				if (i % 3 == 0) {
					cue(slip, SoundEvents.ENDERMAN_TELEPORT, 0.5f + 0.2f * hash(i, 102), 0.4f);
				}
			}
			if (open > 0.0f) {
				rect(-half * open, rift - 0.4f, 2.0f * half * open, 0.9f, argb(0x000000, 0.95f));
				rect(-half * open, rift + 0.5f, 2.0f * half * open, 0.2f, argb(0xB070FF, 0.8f));
			}
			// Specks of the world being pulled down into the rift.
			for (int k = 0; k < 40; k++) {
				float age = t - 0.4f + hash(k, 103) * 1.6f;
				if (age <= 0.0f) {
					continue;
				}
				int cycle = (int) (age / 1.6f);
				float p = (age % 1.6f) / 1.6f;
				float x = (hash(k, cycle + 104) - 0.5f) * 2.0f * half;
				float y = Mth.lerp(p * p, top - 8.0f - hash(k, cycle + 105) * 10.0f, rift);
				square(x, y, 0.6f, k % 3 == 0 ? 0xE0B0FF : 0xB070FF, 0.8f * (1.0f - p) * open);
			}
			cue(0.3f, SoundEvents.PORTAL_AMBIENT, 0.6f, 0.5f);
		}

		/** A bolt hits the middle letter: it splits, the ones around it char and spark. */
		private void strike() {
			int target = nearestToCentre();
			for (int i = 0; i < n; i++) {
				if (blank[i]) {
					continue;
				}
				float a = appear(i);
				if (t < BOLT) {
					glyph(i, 0.0f, 0.0f, color, a);
					continue;
				}
				int dist = Math.abs(i - target);
				float charred = (dist == 0 ? 0.85f : dist == 1 ? 0.55f : 0.2f) * clamp01((t - BOLT) / 0.08f);
				int c = mix(color, 0x1A1A22, charred);
				if (dist == 0) {
					float e = easeOutExpo((t - BOLT) / 0.25f);
					if (clip(cx[i] - w[i] / 2.0f - 4.0f, top - 3.0f, cx[i], bottom + 3.0f)) {
						glyph(i, -1.3f * e, 0.0f, c, 1.0f);
						unclip();
					}
					if (clip(cx[i], top - 3.0f, cx[i] + w[i] / 2.0f + 4.0f, bottom + 3.0f)) {
						glyph(i, 1.3f * e, 0.0f, c, 1.0f);
						unclip();
					}
					smoke(i, cx[i], top - 1.0f, BOLT + 0.1f);
				} else {
					// Jolted up by the shock, then straight back down.
					float jolt = -2.0f / dist * (float) Math.exp(-(t - BOLT) * 10.0f);
					glyph(i, 0.0f, jolt, c, 1.0f);
					if (dist == 1) {
						smoke(i, cx[i], top - 1.0f, BOLT + 0.3f);
					}
				}
			}
			if (target < 0) {
				return;
			}

			float since = t - BOLT;
			if (since >= -0.02f && since < 0.35f && ((int) (since * 28.0f)) % 3 != 1) {
				int points = 10;
				float px = cx[target];
				float py = -140.0f;
				for (int j = 1; j < points; j++) {
					float y = Mth.lerp(j / (points - 1.0f), -140.0f, top);
					float x = j == points - 1 ? cx[target] : cx[target] + (hash(j, 110) - 0.5f) * 14.0f * (1.0f - j / (float) points * 0.6f);
					line(px, py, x, y, 3.0f, argb(0x9AD8FF, 0.35f));
					line(px, py, x, y, 1.0f, 0xFFFFFFFF);
					if (j == 4) {
						// One branch forking off.
						float bx = x;
						float by = y;
						for (int b = 1; b <= 3; b++) {
							float nx = x + b * 6.0f * (hash(target, 111) > 0.5f ? 1.0f : -1.0f);
							float ny = y + b * 7.0f;
							line(bx, by, nx, ny, 0.6f, argb(0xFFFFFF, 0.8f));
							bx = nx;
							by = ny;
						}
					}
					px = x;
					py = y;
				}
			}
			if (since >= 0.0f) {
				rect(-2000.0f, -2000.0f, 4000.0f, 4000.0f, argb(0xFFFFFF, 0.85f * (float) Math.exp(-since * 9.0f)));
				// Little arcs jumping between the letters near the hit.
				for (int i = 0; i + 1 < n; i++) {
					if (blank[i] || blank[i + 1] || Math.abs(i - target) > 3) {
						continue;
					}
					float period = 0.5f + hash(i, 112);
					if ((since + hash(i, 113) * 3.0f) % period < 0.07f) {
						int cycle = (int) ((since + hash(i, 113) * 3.0f) / period);
						float x0 = cx[i] + w[i] / 2.0f - 0.5f;
						float x1 = cx[i + 1] - w[i + 1] / 2.0f + 0.5f;
						float y = Mth.lerp(hash(i, cycle + 114), top + 1.0f, bottom - 1.0f);
						float xm = (x0 + x1) / 2.0f;
						float ym = y + (hash(i, cycle + 115) - 0.5f) * 3.0f;
						line(x0, y, xm, ym, 0.4f, argb(0xBFF4FF, 0.95f));
						line(xm, ym, x1, y + (hash(i, cycle + 116) - 0.5f) * 2.0f, 0.4f, argb(0xBFF4FF, 0.95f));
					}
				}
			}
			cue(BOLT, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.0f, 1.0f);
		}

		/** A splash potion (or wither skull, or dragon's breath) smashes on the word and stains it. */
		private void potion() {
			int stain;
			ItemStack prop;
			switch (info.cause()) {
				case "wither" -> {
					stain = 0x262626;
					prop = new ItemStack(Items.WITHER_SKELETON_SKULL);
				}
				case "dragonBreath" -> {
					stain = 0xD040C0;
					prop = new ItemStack(Items.DRAGON_BREATH);
				}
				default -> {
					stain = 0x8E2FD6;
					prop = new ItemStack(Items.SPLASH_POTION);
					prop.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.HARMING));
				}
			}
			float fly = 0.5f;
			for (int i = 0; i < n; i++) {
				if (blank[i]) {
					continue;
				}
				float stainAt = POTION_HIT + Math.abs(cx[i]) / 80.0f;
				float s = easeOut((t - stainAt) / 0.35f);
				int c = mix(color, stain, 0.8f * s);
				glyph(i, 0.0f, 0.0f, c, appear(i));
				// Drips running off the bottom.
				for (int d = 0; d < 2; d++) {
					float phase = t - stainAt - 0.3f - hash(i * 2 + d, 120) * 1.8f;
					if (phase <= 0.0f) {
						continue;
					}
					float x = cx[i] + (hash(i * 2 + d, 121) - 0.5f) * w[i] * 0.7f;
					float cyc = phase % 1.8f;
					float length = clamp01(cyc / 1.1f) * 3.2f;
					if (cyc < 1.1f) {
						rect(x - 0.35f, bottom - 1.0f, 0.7f, length + 0.5f, argb(stain, 0.9f));
					} else {
						float fall = cyc - 1.1f;
						rect(x - 0.35f, bottom - 1.0f, 0.7f, 1.0f, argb(stain, 0.9f));
						square(x, bottom + 3.2f + 30.0f * fall * fall, 0.8f, stain, 1.0f - fall / 0.7f);
					}
				}
			}
			if (t >= POTION_HIT - fly && t < POTION_HIT) {
				float p = (t - (POTION_HIT - fly)) / fly;
				float x = Mth.lerp(p, left - 70.0f, 0.0f);
				float y = Mth.lerp(p, top - 50.0f, top + 2.0f) - 25.0f * Mth.sin(p * Mth.PI);
				item(prop, x, y, 12.0f, p * 6.0f);
			}
			float mid = (top + bottom) / 2.0f;
			for (int k = 0; k < 26; k++) {
				float angle = k / 26.0f * Mth.TWO_PI;
				float speed = 20.0f + hash(k, 122) * 25.0f;
				debris(POTION_HIT, 0.9f, 0.0f, mid, Mth.cos(angle) * speed, Mth.sin(angle) * speed - 15.0f, 80.0f, 0.8f, stain);
			}
			for (int k = 0; k < 8; k++) {
				debris(POTION_HIT, 0.6f, 0.0f, mid, (hash(k, 123) - 0.5f) * 50.0f, -(10.0f + hash(k, 124) * 20.0f), 90.0f, 0.5f, 0xFFFFFF);
			}
			switch (info.cause()) {
				case "wither" -> {
					cue(POTION_HIT - fly, SoundEvents.WITHER_SHOOT, 1.0f, 0.8f);
					cue(POTION_HIT, SoundEvents.GENERIC_EXPLODE.value(), 1.5f, 0.5f);
				}
				case "dragonBreath" -> {
					cue(POTION_HIT - fly, SoundEvents.ENDER_DRAGON_SHOOT, 1.0f, 0.8f);
					cue(POTION_HIT, SoundEvents.SPLASH_POTION_BREAK, 1.0f, 1.0f);
				}
				default -> {
					cue(POTION_HIT - fly, SoundEvents.WITCH_THROW, 1.0f, 0.8f);
					cue(POTION_HIT, SoundEvents.SPLASH_POTION_BREAK, 1.0f, 1.0f);
				}
			}
		}

		/** An anvil falls on the middle of the word and flattens it; the rest gets knocked aside. */
		private void crush() {
			float span = Mth.clamp((right - left) * 0.32f, 14.0f, 24.0f);
			float squashTo = 0.45f;
			float since = t - ANVIL_LAND;
			float pressed = since >= 0.0f ? easeOutExpo(since / 0.07f) : 0.0f;
			for (int i = 0; i < n; i++) {
				float a = appear(i);
				float d = Math.abs(cx[i]) - span / 2.0f;
				float under = clamp01(1.0f - d / 5.0f);
				float sy = 1.0f - (1.0f - squashTo) * under * pressed;
				float sx = 1.0f + 0.2f * under * pressed;
				float dx = 0.0f;
				float hop = 0.0f;
				float tilt = 0.0f;
				if (since > 0.0f) {
					float side = cx[i] >= 0.0f ? 1.0f : -1.0f;
					float near = (1.0f - under) / (1.0f + Math.max(0.0f, d) / 10.0f);
					float p = clamp01(since / 0.35f);
					hop = 3.0f * near * 4.0f * p * (1.0f - p);
					dx = side * 1.5f * near * easeOut(p);
					tilt = side * 0.1f * near * easeOut(p);
				}
				glyph(i, dx, -hop, tilt, sx, sy, bottom, mix(color, 0x5A5A5A, 0.35f * under * pressed), a);
			}
			// Its shadow grows as it comes down.
			if (t > ANVIL_DROP && t < ANVIL_LAND) {
				float p = (t - ANVIL_DROP) / (ANVIL_LAND - ANVIL_DROP);
				float sw = span * (0.5f + 0.5f * p);
				rect(-sw / 2.0f, bottom - 0.6f, sw, 1.2f, argb(0x000000, 0.5f * p));
			}
			if (t >= ANVIL_DROP) {
				float base;
				if (t < ANVIL_LAND) {
					float p = (t - ANVIL_DROP) / (ANVIL_LAND - ANVIL_DROP);
					base = Mth.lerp(p * p, top - 110.0f, top);
				} else {
					base = Mth.lerp(pressed, top, bottom - height * squashTo);
				}
				item(new ItemStack(Items.ANVIL), 0.0f, base - span * 0.42f, span, 0.0f);
			}
			if (since > 0.0f) {
				for (int k = 0; k < 16; k++) {
					float life = 0.9f;
					if (since < life) {
						float side = k % 2 == 0 ? -1.0f : 1.0f;
						float x = side * (span / 2.0f + 14.0f * easeOut(since / life) * (0.4f + 0.6f * hash(k, 130)));
						float y = bottom - 0.5f - hash(k, 131) * 3.0f * since;
						square(x, y, 1.0f + 2.2f * since, 0x8C8C8C, 0.55f * (1.0f - since / life));
					}
				}
				for (int k = 0; k < 10; k++) {
					debris(ANVIL_LAND, 0.8f, (hash(k, 132) - 0.5f) * span, bottom - height * squashTo,
							(hash(k, 133) - 0.5f) * 50.0f, -(8.0f + hash(k, 134) * 16.0f), 90.0f, 0.6f, color);
				}
			}
			cue(ANVIL_LAND, SoundEvents.ANVIL_LAND, 0.8f, 1.0f);
			cue(ANVIL_LAND, SoundEvents.STONE_BREAK, 0.7f, 1.0f);
		}

		/** Bites get taken out of the last letters, from the right, until the last one is gone. */
		private void eaten() {
			List<Integer> visible = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				if (!blank[i]) {
					visible.add(i);
				}
			}
			// (letter, slot): slot 0 and 1 are bites, slot 2 eats the rest of the letter.
			List<int[]> bites = new ArrayList<>();
			int[] slotsPerLetter = {3, 2, 1};
			for (int r = 0; r < slotsPerLetter.length && r < visible.size(); r++) {
				int letter = visible.get(visible.size() - 1 - r);
				for (int s = 0; s < slotsPerLetter[r]; s++) {
					bites.add(new int[] {letter, s});
				}
			}
			float radius = height * 0.42f;
			float[] biteY = {top + height * 0.3f, top + height * 0.75f};

			for (int i = 0; i < n; i++) {
				if (blank[i]) {
					continue;
				}
				float a = appear(i);
				int taken = 0;
				float lastBite = -1.0f;
				for (int k = 0; k < bites.size(); k++) {
					if (bites.get(k)[0] == i && t >= biteTime(k)) {
						taken++;
						lastBite = biteTime(k);
					}
				}
				if (taken == 0) {
					glyph(i, 0.0f, 0.0f, color, a);
					continue;
				}
				if (taken >= 3) {
					continue;
				}
				// A little jolt every time a bite is taken.
				float dx = -0.8f * (float) Math.exp(-(t - lastBite) * 12.0f);
				float[] biteX = {w[i] / 2.0f + 0.2f * radius, w[i] / 2.0f - 0.1f * radius};
				for (float y = top - 1.0f; y < bottom + 1.0f; y += 0.5f) {
					float limit = cx[i] + w[i] / 2.0f + 2.0f;
					for (int s = 0; s < taken; s++) {
						float dy = y + 0.25f - biteY[s];
						// Wavy radius: tooth marks.
						float r = radius + 0.35f * Mth.cos(dy * 2.6f);
						if (Math.abs(dy) < r) {
							limit = Math.min(limit, cx[i] + biteX[s] - Mth.sqrt(r * r - dy * dy));
						}
					}
					if (clip(cx[i] - w[i] / 2.0f - 2.0f + dx, y, limit + dx, y + 0.5f)) {
						glyph(i, dx, 0.0f, color, a);
						unclip();
					}
				}
			}
			for (int k = 0; k < bites.size(); k++) {
				int letter = bites.get(k)[0];
				int slot = bites.get(k)[1];
				float at = biteTime(k);
				float y = slot < 2 ? biteY[slot] : (top + bottom) / 2.0f;
				int crumbs = slot < 2 ? 12 : 20;
				for (int c = 0; c < crumbs; c++) {
					debris(at, 1.3f, cx[letter] + w[letter] / 2.0f - 1.0f, y, (hash(k * 20 + c, 140) - 0.3f) * 22.0f,
							-(4.0f + hash(k * 20 + c, 141) * 12.0f), 70.0f, 0.5f + 0.5f * hash(k * 20 + c, 142),
							mix(color, 0x5A3A18, hash(k * 20 + c, 143) * 0.6f));
				}
				cue(at, SoundEvents.GENERIC_EAT.value(), 0.8f + 0.4f * hash(k, 144), 1.0f);
			}
			if (!bites.isEmpty()) {
				cue(biteTime(bites.size() - 1) + 0.45f, SoundEvents.PLAYER_BURP, 1.0f, 1.0f);
			}
		}

		private static float biteTime(int k) {
			return 0.8f + k * 0.36f;
		}

		// ---------------------------------------------------------------- helpers

		/** Quick, clean fade in from left to right. */
		private float appear(int i) {
			return clamp01((t - i * 0.03f) / 0.25f);
		}

		private void glyph(int i, float dx, float dy, int rgb, float alpha) {
			glyph(i, dx, dy, 0.0f, 1.0f, 1.0f, 0.0f, rgb, alpha);
		}

		/** Draws letter i moved by (dx, dy), turned and scaled around the point (centre, pivotY). */
		private void glyph(int i, float dx, float dy, float rot, float sx, float sy, float pivotY, int rgb, float alpha) {
			alpha *= opacity;
			if (blank[i] || alpha <= 0.02f) {
				return;
			}
			pose.pushMatrix();
			pose.translate(cx[i] + dx, dy + pivotY);
			if (rot != 0.0f) {
				pose.rotate(rot);
			}
			pose.scale(sx, sy);
			pose.translate(-w[i] / 2.0f, -pivotY - font.lineHeight / 2.0f);
			g.text(font, letters[i], 0, 0, argb(rgb, alpha), true);
			pose.popMatrix();
		}

		/** Only draw inside this rectangle (title units) until {@link #unclip()}. False if it's empty. */
		private boolean clip(float x0, float y0, float x1, float y1) {
			int sx0 = Mth.floor(ox + x0 * scale);
			int sy0 = Mth.floor(oy + y0 * scale);
			int sx1 = Mth.ceil(ox + x1 * scale);
			int sy1 = Mth.ceil(oy + y1 * scale);
			if (sx1 <= sx0 || sy1 <= sy0) {
				return false;
			}
			// The scissor goes through the current transform, so set it in plain screen coordinates.
			pose.pushMatrix();
			pose.identity();
			g.enableScissor(sx0, sy0, sx1, sy1);
			pose.popMatrix();
			return true;
		}

		private void unclip() {
			g.disableScissor();
		}

		private void rect(float x, float y, float rw, float rh, int argb) {
			if (rw <= 0.0f || rh <= 0.0f || (argb >>> 24) <= 5) {
				return;
			}
			pose.pushMatrix();
			pose.translate(x, y);
			pose.scale(rw, rh);
			g.fill(0, 0, 1, 1, argb);
			pose.popMatrix();
		}

		private void square(float x, float y, float size, int rgb, float alpha) {
			alpha *= opacity;
			if (alpha > 0.02f) {
				rect(x - size / 2.0f, y - size / 2.0f, size, size, argb(rgb, alpha));
			}
		}

		private void line(float x0, float y0, float x1, float y1, float thickness, int argb) {
			float dx = x1 - x0;
			float dy = y1 - y0;
			float length = Mth.sqrt(dx * dx + dy * dy);
			if (length < 0.001f) {
				return;
			}
			pose.pushMatrix();
			pose.translate(x0, y0);
			pose.rotate((float) Math.atan2(dy, dx));
			pose.scale(length, thickness);
			pose.translate(0.0f, -0.5f);
			g.fill(0, 0, 1, 1, argb);
			pose.popMatrix();
		}

		private void item(ItemStack stack, float x, float y, float size, float rot) {
			if (opacity < 0.5f) {
				return;
			}
			pose.pushMatrix();
			pose.translate(x, y);
			if (rot != 0.0f) {
				pose.rotate(rot);
			}
			pose.scale(size / 16.0f, size / 16.0f);
			pose.translate(-8.0f, -8.0f);
			g.item(stack, 0, 0);
			pose.popMatrix();
		}

		/** One 16x16 frame of a vertical animation strip, stretched over the given box. */
		private void sprite(Identifier texture, float x, float y, float sw, float sh, int frame, int frames, int argb) {
			if (sw <= 0.0f || sh <= 0.0f) {
				return;
			}
			pose.pushMatrix();
			pose.translate(x, y);
			pose.scale(sw / 16.0f, sh / 16.0f);
			g.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0.0f, frame * 16.0f, 16, 16, 16, 16 * frames,
					argb(argb & 0xFFFFFF, (argb >>> 24) / 255.0f * opacity));
			pose.popMatrix();
		}

		/** A bit of something thrown from (x, y) at time {@code at}, falling with gravity. */
		private void debris(float at, float life, float x, float y, float vx, float vy, float gravity, float size, int rgb) {
			float age = t - at;
			if (age < 0.0f || age > life) {
				return;
			}
			square(x + vx * age, y + vy * age + 0.5f * gravity * age * age, size, rgb, 1.0f - age / life);
		}

		/** Thin smoke rising from (x, y), starting at {@code from}. */
		private void smoke(int seed, float x, float y, float from) {
			for (int k = 0; k < 3; k++) {
				float age = t - from + k * 0.47f;
				if (t < from || age <= 0.0f) {
					continue;
				}
				int cycle = (int) (age / 1.4f);
				float a = age % 1.4f;
				float sx = x + (hash(seed * 3 + k, cycle + 150) - 0.5f) * 3.0f * (1.0f + a);
				square(sx, y - a * 8.0f, 1.0f + a * 2.0f, 0x505050, 0.3f * (1.0f - a / 1.4f));
			}
		}

		/** Plays a sound the first frame the title reaches {@code at}. */
		private void cue(float at, SoundEvent sound, float pitch, float volume) {
			if (prev < at && t >= at && t - at < 0.3f && opacity > 0.5f) {
				DeathCinematic.playSound(sound, pitch, volume);
			}
		}

		private float impulse(float at, float strength) {
			return t >= at ? strength * (float) Math.exp(-(t - at) * 9.0f) : 0.0f;
		}

		/** Non-blank letters at these fractions of the word; -1 where there are none. */
		private int[] spread(float[] fractions) {
			List<Integer> visible = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				if (!blank[i]) {
					visible.add(i);
				}
			}
			int[] result = new int[fractions.length];
			for (int k = 0; k < fractions.length; k++) {
				result[k] = visible.isEmpty() ? -1 : visible.get(Math.min(visible.size() - 1, (int) (fractions[k] * visible.size())));
			}
			return result;
		}

		private int nearestToCentre() {
			int best = -1;
			for (int i = 0; i < n; i++) {
				if (!blank[i] && (best < 0 || Math.abs(cx[i]) < Math.abs(cx[best]))) {
					best = i;
				}
			}
			return best;
		}
	}

	public static float hash(int i, int salt) {
		int h = i * 73856093 ^ salt * 19349663;
		h ^= h >>> 13;
		h *= 0x5bd1e995;
		h ^= h >>> 15;
		return (h & 0xFFFFFF) / (float) 0x1000000;
	}

	private static float clamp01(float v) {
		return Mth.clamp(v, 0.0f, 1.0f);
	}

	private static float easeOut(float t) {
		return CameraDirector.easeOut(t);
	}

	private static float smooth(float t) {
		return CameraDirector.smooth(t);
	}

	private static float easeOutExpo(float t) {
		t = clamp01(t);
		return t >= 1.0f ? 1.0f : 1.0f - (float) Math.pow(2.0, -10.0 * t);
	}

	public static int mix(int a, int b, float t) {
		t = clamp01(t);
		int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
		int g = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
		int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
		return r << 16 | g << 8 | bl;
	}

	/** Text below alpha 4 is skipped by the renderer, so never go fully transparent. */
	public static int argb(int rgb, float alpha) {
		int a = Mth.clamp((int) (alpha * 255), 5, 255);
		return a << 24 | (rgb & 0xFFFFFF);
	}
}
