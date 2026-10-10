package com.zoomoutnexus;

import lombok.Getter;

/**
 * One teleport as loaded from teleports.json. The sprite IDs mirror Nexus Menu Map's RegionDef.json.
 * For regular teleports (x, y) is the icon centre on the 478x272 overview map; for global ones it is
 * the top-left corner, matching where Nexus Menu Map draws them at full 24px size.
 */
@Getter
public class TeleportDef
{
	private String name;
	private String alias;
	private boolean isAlt;
	private int enabledSprite;
	private Integer displaySprite;
	private int disabledSprite;
	private boolean global;
	private int x;
	private int y;

	public boolean hasAlias()
	{
		return alias != null;
	}

	/** The sprite to show when available (some teleports use a cleaner custom sprite). */
	public int getSpriteForDisplay()
	{
		return displaySprite != null ? displaySprite : enabledSprite;
	}

	/** Identifies the teleport in the vanilla list: its sprite plus which list (primary/alternate) it's in. */
	public long getKey()
	{
		return key(enabledSprite, isAlt);
	}

	public static long key(int spriteId, boolean alt)
	{
		return ((long) spriteId << 1) | (alt ? 1 : 0);
	}
}
