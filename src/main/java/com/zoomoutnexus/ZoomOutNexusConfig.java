package com.zoomoutnexus;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(ZoomOutNexusConfig.GROUP)
public interface ZoomOutNexusConfig extends Config
{
	String GROUP = "zoomoutnexus";

	@ConfigItem(
		keyName = "showTeleports",
		name = "Teleports on overview",
		description = "Show every teleport directly on the Nexus Map overview",
		position = 0
	)
	default boolean showTeleports()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showGlobal",
		name = "Boat / Respawn top-left",
		description = "Show the Respawn Point, Boat and Last Boat teleports in the top-left of the overview"
			+ " (also hides Nexus Menu Map's own copies when off)",
		position = 1
	)
	default boolean showGlobal()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showUnavailable",
		name = "Show unavailable",
		description = "Show greyed-out icons for teleports your nexus doesn't have",
		position = 2
	)
	default boolean showUnavailable()
	{
		return false;
	}

	@Range(min = 10, max = 50)
	@ConfigItem(
		keyName = "teleportIconSize",
		name = "Icon size",
		description = "Size of the teleport icons on the overview. Above 20 they start to overlap; 12, 24 and 48 scale the cleanest",
		position = 3
	)
	default int iconSize()
	{
		return 26;
	}

	@ConfigItem(
		keyName = "enlargeOnHover",
		name = "Enlarge on hover",
		description = "Show a bigger copy of the icon under the mouse",
		position = 4
	)
	default boolean enlargeOnHover()
	{
		return true;
	}

	@Range(min = 10, max = 50)
	@ConfigItem(
		keyName = "hoverSize",
		name = "Enlarge size",
		description = "How big a teleport icon gets while the mouse is over it (needs Enlarge on hover)",
		position = 5
	)
	default int hoverSize()
	{
		return 34;
	}

	@ConfigItem(
		keyName = "hideShields",
		name = "Hide region shields",
		description = "Hide the region shields on the overview, leaving only teleports",
		position = 6
	)
	default boolean hideShields()
	{
		return true;
	}
}
