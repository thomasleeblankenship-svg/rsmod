package net.runelite.client.plugins.microbot.dabrutus;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

/**
 * The Da-Scurrius paint for Brutus (user 2026-10-03: "make it look similar to that"): over the chat box, a main card
 * (weapon icon, phase, status pill, trip/boss/kill-time/loot box, elapsed/inventory/dodges footer) and an ACCOUNT stats
 * panel (level and XP gained this session).
 */
class BrutusPaint extends Overlay
{
	private static final Color BG = new Color(20, 25, 29, 238);
	private static final Color BORDER = new Color(58, 68, 76);
	private static final Color INNER = new Color(12, 16, 19, 245);
	private static final Color GREEN = new Color(126, 206, 96);
	private static final Color TEAL = new Color(110, 196, 214);
	private static final Color ORANGE = new Color(232, 164, 64);
	private static final Color GOLD = new Color(214, 178, 74);
	private static final Color TEXT = new Color(236, 238, 240);
	private static final Color MUTED = new Color(150, 160, 168);
	private static final Color RED = new Color(214, 84, 76);
	private static final Skill[] SKILLS = {Skill.ATTACK, Skill.HITPOINTS, Skill.STRENGTH, Skill.PRAYER, Skill.DEFENCE,
		Skill.MAGIC, Skill.RANGED};

	private final Client client;
	private final DaBrutusScript script;
	private final ItemManager itemManager;
	private final SkillIconManager skillIcons;

	@Inject
	BrutusPaint(Client client, DaBrutusScript script, ItemManager itemManager, SkillIconManager skillIcons)
	{
		this.client = client;
		this.script = script;
		this.itemManager = itemManager;
		this.skillIcons = skillIcons;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(OverlayPriority.HIGH);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (script.getStartedMs() == 0)
		{
			return null;
		}
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		Rectangle area = chatArea();
		// Wider stats panel so "+12.3k" XP values fit next to the level (2026-10-03 screenshot).
		int statsW = 226;
		int gap = 5;
		int mainW = Math.max(280, area.width - statsW - gap);
		drawMain(g, area.x, area.y, mainW, area.height);
		drawStats(g, area.x + mainW + gap, area.y, statsW, area.height);
		return null;
	}

	private Rectangle loggedArea;

	/**
	 * Exactly the chat box's message area (user, 2026-10-03: "fit it exactly where the chat box is"): the chat box
	 * frame from its top down to the top of the chat tab buttons. Falls back to the fixed-mode size at the bottom left.
	 */
	private Rectangle chatArea()
	{
		Widget parent = client.getWidget(WidgetInfo.CHATBOX_PARENT);
		Widget buttons = client.getWidget(WidgetInfo.CHATBOX_BUTTONS);
		Rectangle area = null;
		if (parent != null && !parent.isHidden() && parent.getBounds().width > 300)
		{
			Rectangle p = parent.getBounds();
			int bottom = p.y + p.height;
			if (buttons != null && !buttons.isHidden())
			{
				Rectangle b = buttons.getBounds();
				if (b.y > p.y + 60 && b.y < bottom)
				{
					bottom = b.y; // tabs at the bottom of the frame: stop above them
				}
			}
			area = new Rectangle(p.x, p.y, p.width, bottom - p.y);
		}
		if (area == null || area.height < 60)
		{
			area = new Rectangle(0, client.getCanvasHeight() - 165, 519, 142);
		}
		if (!area.equals(loggedArea))
		{
			loggedArea = area;
			net.runelite.client.plugins.microbot.Microbot.log(DaBrutusScript.tag() + "paint area " + area + " (chat parent "
				+ (parent == null ? "none" : parent.getBounds()) + ", tabs " + (buttons == null ? "none" : buttons.getBounds()) + ")");
		}
		return area;
	}

	// ------------------------------------------------------------------ main card

	private void drawMain(Graphics2D g, int x, int y, int w, int h)
	{
		// Laid out for the fixed-mode chat box (~118 px tall, 2026-10-03 screenshot: the first layout overlapped).
		card(g, x, y, w, h);
		g.setColor(new Color(36, 44, 36));
		g.fillRoundRect(x + 8, y + 7, 32, 32, 8, 8);
		BufferedImage weapon = weaponImage();
		if (weapon != null)
		{
			g.drawImage(weapon, x + 8 + (32 - weapon.getWidth()) / 2, y + 7 + (32 - weapon.getHeight()) / 2, null);
		}
		String status = script.getStatus() == null ? "" : script.getStatus();
		String phase = phase(status);
		text(g, "DA-BRUTUS · BOSSING", x + 46, y + 17, small(), GREEN);
		String ver = "v" + DaBrutusPlugin.version;
		text(g, ver, x + w - 8 - width(g, ver, small()), y + 17, small(), MUTED);
		text(g, phase, x + 46, y + 35, bold().deriveFont(16f), TEXT);
		int pw = pill(g, x + 46 + width(g, phase, bold().deriveFont(16f)) + 8, y + 22, phase.toUpperCase(), ORANGE, true);
		int sx = x + 46 + width(g, phase, bold().deriveFont(16f)) + 8 + pw + 6;
		text(g, ellipsize(g, status, small(), x + w - 8 - sx), sx, y + 35, small(), TEXT);

		// info box: three small lines
		int by = y + 44;
		int fy = y + h - 19;
		int bh = fy - by - 3;
		g.setColor(INNER);
		g.fillRoundRect(x + 8, by, w - 16, bh, 8, 8);
		g.setColor(GREEN);
		g.fillRect(x + 8, by + 3, 3, bh - 6);
		int line = Math.max(10, (bh - 4) / 3);
		int boss = bossPct();
		String where = boss >= 0 ? "BOSS " + boss + "%" : DaBrutusScript.inPenCached ? "BOSS RESPAWNING" : "OUT OF THE PEN";
		String trip = "TRIP " + script.getTrip() + "  ·  " + where + "  ·  KILLCOUNT " + BrutusEvents.kills;
		text(g, trip, x + 16, by + line, small(), GREEN);
		List<Long> times = script.killTimes();
		long elapsed = System.currentTimeMillis() - script.getStartedMs();
		String kills = times.isEmpty() ? "No kill yet this session"
			: "Last kill " + mmss(times.get(times.size() - 1)) + "  ·  avg " + mmss(avg(times)) + "  ·  "
				+ String.format("%.1f", BrutusEvents.kills * 3_600_000.0 / Math.max(60_000, elapsed)) + " kills/h";
		text(g, kills, x + 16, by + 2 * line, small(), TEXT);
		String loot = "Loot " + gp(script.getLootGp()) + " (" + gp(script.getLootGp() * 3_600_000L / Math.max(60_000, elapsed))
			+ "/h)";
		text(g, loot, x + 16, by + 3 * line, small(), GOLD);
		int bell = script.charges();
		String supplies = "Food " + script.foodHeld() + " · Cowbell " + (bell < 0 ? "charged" : String.valueOf(bell));
		text(g, supplies, x + w - 16 - width(g, supplies, small()), by + 3 * line, small(), MUTED);

		// footer
		int fx = x + 8;
		fx += pill(g, fx, fy, hhmmss(elapsed), MUTED, false) + 5;
		fx += pill(g, fx, fy, "Inv " + invCount() + "/28", MUTED, false) + 5;
		String dodged = "Dodged " + BrutusEvents.charges + "C · " + BrutusEvents.slams + "S";
		int dx = x + w - 8 - pillWidth(g, dodged);
		pill(g, dx, fy, dodged, GOLD, false);
		String persona = script.personaName();
		if (persona != null && fx + pillWidth(g, persona) <= dx - 5)
		{
			pill(g, fx, fy, persona, MUTED, false);
		}
	}

	/** Brutus's health bar in percent, or -1 when he is not in sight. Render thread = client thread. */
	private int bossPct()
	{
		var wv = client.getTopLevelWorldView();
		if (wv == null)
		{
			return -1;
		}
		for (NPC npc : wv.npcs())
		{
			if (npc != null && BrutusEvents.isBrutus(npc) && !npc.isDead())
			{
				int ratio = npc.getHealthRatio();
				int scale = npc.getHealthScale();
				return ratio < 0 || scale <= 0 ? 100 : ratio * 100 / scale;
			}
		}
		return -1;
	}

	// ------------------------------------------------------------------ stats panel

	private void drawStats(Graphics2D g, int x, int y, int w, int h)
	{
		card(g, x, y, w, h);
		text(g, "ACCOUNT", x + 8, y + 14, small(), TEAL);
		String combat = "Combat " + (client.getLocalPlayer() == null ? "?" : client.getLocalPlayer().getCombatLevel());
		text(g, combat, x + w - 8 - width(g, combat, small()), y + 14, small(), MUTED);
		int cols = 2;
		int gap = 3;
		int top = y + 19;
		int bottom = y + h - 10;
		int cellW = (w - 16 - gap) / cols;
		int cellH = (bottom - top - 3 * gap) / 4;
		for (int i = 0; i < SKILLS.length; i++)
		{
			cell(g, x + 8 + (i % cols) * (cellW + gap), top + (i / cols) * (cellH + gap), cellW, cellH, SKILLS[i]);
		}
		// 8th cell: the style being trained
		int cx = x + 8 + cellW + gap;
		int cy = top + 3 * (cellH + gap);
		g.setColor(INNER);
		g.fillRoundRect(cx, cy, cellW, cellH, 6, 6);
		String block = script.trainingText();
		text(g, "→ " + block, cx + 5, cy + cellH / 2 + 4, small(), GOLD);
		int hp = client.getBoostedSkillLevel(Skill.HITPOINTS);
		int hpMax = Math.max(1, client.getRealSkillLevel(Skill.HITPOINTS));
		int pr = client.getBoostedSkillLevel(Skill.PRAYER);
		int prMax = Math.max(1, client.getRealSkillLevel(Skill.PRAYER));
		int bw = (w - 16 - gap) / 2;
		bar(g, x + 8, y + h - 7, bw, hp, hpMax, RED);
		bar(g, x + 8 + bw + gap, y + h - 7, bw, pr, prMax, TEAL);
	}

	/** Icon, level (boosted/real when boosted) and the XP gained this session under it. */
	private void cell(Graphics2D g, int x, int y, int w, int h, Skill skill)
	{
		g.setColor(INNER);
		g.fillRoundRect(x, y, w, h, 6, 6);
		BufferedImage icon = skillIcons.getSkillImage(skill, true);
		int tx = x + 4;
		if (icon != null)
		{
			g.drawImage(icon, x + 3, y + (h - icon.getHeight()) / 2, null);
			tx = x + 5 + icon.getWidth();
		}
		int lvl = client.getRealSkillLevel(skill);
		int boosted = client.getBoostedSkillLevel(skill);
		String level = boosted != lvl ? boosted + "/" + lvl : String.valueOf(lvl);
		// Level on the first line, XP gained this session under it.
		text(g, level, tx, y + h / 2, bold(), TEXT);
		int xp = script.xpGained(skill);
		text(g, xp > 0 ? "+" + gp(xp).replace(" gp", "") + " xp" : "+0 xp", tx, y + h - 3, small(), xp > 0 ? GREEN : MUTED);
	}

	// ------------------------------------------------------------------ drawing helpers

	private static void card(Graphics2D g, int x, int y, int w, int h)
	{
		g.setColor(BG);
		g.fillRoundRect(x, y, w, h, 12, 12);
		g.setColor(BORDER);
		g.setStroke(new BasicStroke(1f));
		g.drawRoundRect(x, y, w - 1, h - 1, 12, 12);
	}

	/** A rounded pill; returns its width. */
	private static int pill(Graphics2D g, int x, int y, String label, Color color, boolean dot)
	{
		int w = pillWidth(g, label) + (dot ? 10 : 0);
		g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 40));
		g.fillRoundRect(x, y, w, 16, 16, 16);
		g.setColor(color);
		g.drawRoundRect(x, y, w - 1, 15, 16, 16);
		int tx = x + 7;
		if (dot)
		{
			g.fillOval(x + 6, y + 5, 6, 6);
			tx += 9;
		}
		text(g, label, tx, y + 12, small(), color == MUTED ? TEXT : color);
		return w;
	}

	private static int pillWidth(Graphics2D g, String label)
	{
		return width(g, label, small()) + 14;
	}

	private static void bar(Graphics2D g, int x, int y, int w, int cur, int max, Color color)
	{
		g.setColor(INNER);
		g.fillRoundRect(x, y, w, 5, 5, 5);
		g.setColor(color);
		g.fillRoundRect(x, y, Math.max(2, (int) (w * Math.min(1.0, cur / (double) max))), 5, 5, 5);
	}

	private static void text(Graphics2D g, String s, int x, int y, Font f, Color c)
	{
		g.setFont(f);
		g.setColor(new Color(0, 0, 0, 160));
		g.drawString(s, x + 1, y + 1);
		g.setColor(c);
		g.drawString(s, x, y);
	}

	private static int width(Graphics2D g, String s, Font f)
	{
		return g.getFontMetrics(f).stringWidth(s);
	}

	private static String ellipsize(Graphics2D g, String s, Font f, int max)
	{
		FontMetrics m = g.getFontMetrics(f);
		if (m.stringWidth(s) <= max)
		{
			return s;
		}
		while (s.length() > 1 && m.stringWidth(s + "…") > max)
		{
			s = s.substring(0, s.length() - 1);
		}
		return s + "…";
	}

	private static Font small()
	{
		return FontManager.getRunescapeSmallFont();
	}

	private static Font bold()
	{
		return FontManager.getRunescapeBoldFont();
	}

	// ------------------------------------------------------------------ data helpers

	/** A short title for the status line. */
	static String phase(String status)
	{
		String s = status.toLowerCase();
		if (s.contains("quest") || s.contains("lamp"))
		{
			return "Questing";
		}
		if (s.contains("grave") || s.contains("died"))
		{
			return "Grave";
		}
		if (s.contains("loot") || s.contains("bury") || s.contains("burying"))
		{
			return "Looting";
		}
		if (s.contains("bank") || s.contains("stocked") || s.contains("wield") || s.contains("wearing")
			|| s.contains("gillie"))
		{
			return "Banking";
		}
		if (s.contains("altar") || s.contains("praying"))
		{
			return "Praying";
		}
		if (s.contains("leaving"))
		{
			return "Leaving";
		}
		if (s.contains("respawn") || s.contains("ringing"))
		{
			return "Waiting";
		}
		if (s.contains("walking") || s.contains("teleport") || s.contains("releasing") || s.contains("charging"))
		{
			return "Travelling";
		}
		if (s.contains("brutus") || s.contains("dodg") || s.contains("eating") || s.contains("style")
			|| s.contains("prayer"))
		{
			return "Fighting";
		}
		return "Brutus";
	}

	private BufferedImage weaponImage()
	{
		ItemContainer eq = client.getItemContainer(InventoryID.EQUIPMENT);
		if (eq == null)
		{
			return null;
		}
		Item w = eq.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
		return w == null || w.getId() <= 0 ? null : itemManager.getImage(w.getId());
	}

	private int invCount()
	{
		ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
		if (inv == null)
		{
			return 0;
		}
		int n = 0;
		for (Item i : inv.getItems())
		{
			if (i != null && i.getId() > 0)
			{
				n++;
			}
		}
		return n;
	}

	private static long avg(List<Long> xs)
	{
		long sum = 0;
		for (long x : xs)
		{
			sum += x;
		}
		return xs.isEmpty() ? 0 : sum / xs.size();
	}

	static String mmss(long ms)
	{
		long s = ms / 1000;
		return String.format("%d:%02d", s / 60, s % 60);
	}

	static String hhmmss(long ms)
	{
		long s = ms / 1000;
		return String.format("%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
	}

	static String gp(long v)
	{
		if (v >= 1_000_000)
		{
			return String.format("%.1fm gp", v / 1_000_000.0);
		}
		if (v >= 1_000)
		{
			return String.format("%.1fk gp", v / 1_000.0);
		}
		return v + " gp";
	}
}
