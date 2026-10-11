package com.zoomoutnexus;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/**
 * Addon for Antipixel's Nexus Menu Map. It never touches that plugin's code: it waits for Nexus Menu Map
 * to build its widgets, then adds its own teleport icons to the same interface and shows them only while
 * the overview (index) page is on screen. With Nexus Menu Map disabled or in list mode, nothing shows.
 */
@Slf4j
@PluginDescriptor(
	name = "Zoom Out Nexus",
	description = "Addon for Nexus Menu Map: every teleport on the world overview, plus Boat/Respawn in the top-left",
	tags = {"poh", "portal", "teleport", "nexus", "house", "map"}
)
public class ZoomOutNexusPlugin extends Plugin
{
	/* Nexus interface (group 17) */
	private static final int GROUP_NEXUS_PORTAL = 17;
	private static final int ID_PORTAL_WINDOW = 0x110001;
	private static final int ID_LOC_LABELS_PRIMARY = 0x11000C;
	/** Clickable rows of the vanilla list; child N here is the row whose label is child N of the labels layer. */
	private static final int ID_LIST_ROWS_PRIMARY = 0x11000D;
	private static final int ID_SPRITES_PRIMARY = 0x11000F;
	private static final int ID_LOC_LABELS_ALTERNATE = 0x110010;
	private static final int ID_LIST_ROWS_ALTERNATE = 0x110011;
	private static final int ID_SPRITES_ALTERNATE = 0x110012;

	/** The vanilla list row's teleport/scry option. */
	private static final int LIST_ROW_OP = 1;
	private static final int VARBIT_NEXUS_MODE = 6671;

	/* Nexus Menu Map's layout and sprites */
	private static final int MAP_SECTION_X = 7;
	private static final int MAP_SECTION_Y = 35;
	private static final int FULL_ICON_SIZE = 24;
	/** Region overview sprites (Map_*_Top.png); only on the overview page, so they tell us it's showing. */
	private static final int INDEX_SPRITE_MIN = -18010;
	private static final int INDEX_SPRITE_MAX = -18000;
	/** Region shield sprites, standard (-181xx) and hover (-1815x/-1816x). */
	private static final int SHIELD_SPRITE_MIN = -18160;
	private static final int SHIELD_SPRITE_MAX = -18100;

	/* Nexus Menu Map's config, read so our icons are named/bordered the same way */
	private static final String NEXUS_GROUP = "nexusmenu";

	private static final Pattern TELE_NAME_PATTERN = Pattern.compile("<col=ffffff>(.+)</col> : +(.+)");
	private static final String SHORTCUT_COLOUR_TAG = "<col=ffffff>";
	private static final String NAME_FORMAT = "<col=ff9040>%s</col>";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ZoomOutNexusConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private Gson gson;

	private List<TeleportDef> definitions;

	/** Our icons for the currently open nexus interface; empty when it's closed. */
	private final List<Icon> icons = new ArrayList<>();
	private final Set<Widget> ownWidgets = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
	/**
	 * Enlarged copy of the hovered icon. Created after every icon so it draws on top of its neighbours;
	 * it has no listeners, so the mouse still belongs to the real icon underneath.
	 */
	private Widget hoverWidget;
	private Icon hoveredIcon;
	private boolean pendingBuild;
	private boolean shieldsHiddenByUs;

	private static final class Icon
	{
		final TeleportDef def;
		final Widget widget;
		/** Vanilla label widget, null when the player's nexus doesn't have this teleport. */
		Widget label;
		String shortcut;

		Icon(TeleportDef def, Widget widget)
		{
			this.def = def;
			this.widget = widget;
		}

		boolean available()
		{
			return label != null;
		}
	}

	@Provides
	ZoomOutNexusConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ZoomOutNexusConfig.class);
	}

	@Override
	protected void startUp()
	{
		try (InputStreamReader reader = new InputStreamReader(
			ZoomOutNexusPlugin.class.getResourceAsStream("teleports.json"), StandardCharsets.UTF_8))
		{
			definitions = List.of(gson.fromJson(reader, TeleportDef[].class));
		}
		catch (Exception e)
		{
			log.error("Failed to load teleport definitions", e);
			definitions = List.of();
		}

		// Plugin turned on while the nexus is already open
		clientThread.invokeLater(() ->
		{
			if (client.getWidget(ID_PORTAL_WINDOW) != null)
			{
				pendingBuild = true;
			}
		});
	}

	@Override
	protected void shutDown()
	{
		clientThread.invokeLater(() ->
		{
			icons.forEach(icon -> icon.widget.setHidden(true));
			if (hoverWidget != null)
			{
				hoverWidget.setHidden(true);
			}
			restoreShields();
			clear();
		});
	}

	private void clear()
	{
		icons.clear();
		ownWidgets.clear();
		hoverWidget = null;
		hoveredIcon = null;
		pendingBuild = false;
		shieldsHiddenByUs = false;
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded e)
	{
		if (e.getGroupId() == GROUP_NEXUS_PORTAL)
		{
			// The interface (re)loaded, so any widgets we made are gone. Build on the next client tick,
			// by which point Nexus Menu Map has finished creating its own widgets in its WidgetLoaded handler.
			clear();
			pendingBuild = true;
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged e)
	{
		if (e.getVarbitId() == VARBIT_NEXUS_MODE)
		{
			icons.forEach(this::applyActions);
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (ZoomOutNexusConfig.GROUP.equals(e.getGroup()) || NEXUS_GROUP.equals(e.getGroup()))
		{
			clientThread.invokeLater(() ->
			{
				if (!config.hideShields())
				{
					restoreShields();
				}
				icons.forEach(this::applyAppearance);
				setHovered(hoveredIcon, false);
			});
		}
	}

	@Subscribe
	public void onClientTick(ClientTick e)
	{
		Widget window = client.getWidget(ID_PORTAL_WINDOW);
		if (window == null)
		{
			if (!icons.isEmpty() || pendingBuild)
			{
				clear();
			}
			return;
		}

		if (pendingBuild)
		{
			pendingBuild = false;
			build(window);
			if (config.showTeleports())
			{
				disableRegionSelect(window);
			}
		}

		if (!icons.isEmpty())
		{
			updateVisibility(window);
		}
	}

	private void build(Widget window)
	{
		Map<Long, Icon> byKey = new HashMap<>();
		for (TeleportDef def : definitions)
		{
			Widget w = window.createChild(-1, WidgetType.GRAPHIC);
			ownWidgets.add(w);
			Icon icon = new Icon(def, w);
			icons.add(icon);
			byKey.put(def.getKey(), icon);

			w.setHidden(true);
			w.setHasListener(true);
			w.setOnMouseOverListener((JavaScriptCallback) ev -> setHovered(icon, true));
			w.setOnMouseLeaveListener((JavaScriptCallback) ev -> setHovered(icon, false));
		}

		hoverWidget = window.createChild(-1, WidgetType.GRAPHIC);
		ownWidgets.add(hoverWidget);
		hoverWidget.setHidden(true);
		hoverWidget.setSpriteTiling(false);
		hoverWidget.setBorderType(0);

		readVanillaList(byKey, false);
		readVanillaList(byKey, true);

		icons.forEach(this::applyAppearance);
	}

	/**
	 * Matches the teleports in the vanilla nexus list (primary or alternate column) to our icons,
	 * the same way Nexus Menu Map does: by the list entry's sprite.
	 */
	private void readVanillaList(Map<Long, Icon> byKey, boolean alt)
	{
		Widget labels = client.getWidget(alt ? ID_LOC_LABELS_ALTERNATE : ID_LOC_LABELS_PRIMARY);
		Widget sprites = client.getWidget(alt ? ID_SPRITES_ALTERNATE : ID_SPRITES_PRIMARY);
		if (labels == null || sprites == null)
		{
			return;
		}

		Widget[] labelChildren = labels.getDynamicChildren();
		Widget[] spriteChildren = sprites.getDynamicChildren();
		if (labelChildren == null || spriteChildren == null || labelChildren.length != spriteChildren.length)
		{
			return;
		}

		for (int i = 0; i < labelChildren.length; i++)
		{
			Icon icon = byKey.get(TeleportDef.key(spriteChildren[i].getSpriteId(), alt));
			if (icon == null)
			{
				// A teleport Nexus Menu Map doesn't know about yet
				continue;
			}

			String text = labelChildren[i].getText();
			String shortcut = null;
			if (text != null && text.contains(SHORTCUT_COLOUR_TAG))
			{
				Matcher m = TELE_NAME_PATTERN.matcher(text);
				if (!m.matches())
				{
					continue;
				}
				shortcut = m.group(1);
			}

			icon.label = labelChildren[i];
			icon.shortcut = shortcut;
		}
	}

	private void applyAppearance(Icon icon)
	{
		Widget w = icon.widget;
		TeleportDef def = icon.def;

		w.setSpriteId(icon.available() ? def.getSpriteForDisplay() : def.getDisabledSprite());
		// No outline: drawn around a shrunk sprite it reads as a thick white/black ring
		w.setBorderType(0);
		w.setSpriteTiling(false);
		w.setName(icon.available() ? String.format(NAME_FORMAT, teleportName(icon)) : "");
		applyActions(icon);
		applyBounds(icon);
	}

	private void applyBounds(Icon icon)
	{
		Widget w = icon.widget;
		TeleportDef def = icon.def;

		if (def.isGlobal())
		{
			w.setOriginalX(MAP_SECTION_X + def.getX());
			w.setOriginalY(MAP_SECTION_Y + def.getY());
			w.setOriginalWidth(FULL_ICON_SIZE);
			w.setOriginalHeight(FULL_ICON_SIZE);
		}
		else
		{
			int size = config.iconSize();
			w.setOriginalX(MAP_SECTION_X + def.getX() - size / 2);
			w.setOriginalY(MAP_SECTION_Y + def.getY() - size / 2);
			w.setOriginalWidth(size);
			w.setOriginalHeight(size);
		}
		w.revalidate();
	}

	private void applyActions(Icon icon)
	{
		Widget w = icon.widget;
		w.clearActions();
		if (icon.available())
		{
			w.setAction(0, client.getVarbitValue(VARBIT_NEXUS_MODE) == 1 ? "Scry" : "Teleport");
		}
	}

	private void setHovered(Icon icon, boolean hovered)
	{
		if (icon == null || hoverWidget == null)
		{
			return;
		}
		if (!hovered)
		{
			if (hoveredIcon == icon)
			{
				hoveredIcon = null;
				hoverWidget.setHidden(true);
			}
			return;
		}

		hoveredIcon = icon;
		int size = config.hoverSize();
		if (!config.enlargeOnHover() || !icon.available() || icon.def.isGlobal() || size <= config.iconSize())
		{
			hoverWidget.setHidden(true);
			return;
		}

		hoverWidget.setSpriteId(icon.def.getSpriteForDisplay());
		hoverWidget.setOriginalX(MAP_SECTION_X + icon.def.getX() - size / 2);
		hoverWidget.setOriginalY(MAP_SECTION_Y + icon.def.getY() - size / 2);
		hoverWidget.setOriginalWidth(size);
		hoverWidget.setOriginalHeight(size);
		hoverWidget.revalidate();
		hoverWidget.setHidden(false);
	}

	/**
	 * Points the menu entry of one of our icons at the matching row of the (hidden) vanilla teleport list,
	 * so choosing it is an ordinary click on that row and the game performs the teleport itself.
	 */
	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded e)
	{
		MenuEntry entry = e.getMenuEntry();
		if (entry.getType() != MenuAction.CC_OP || entry.getParam1() != ID_PORTAL_WINDOW || icons.isEmpty())
		{
			return;
		}

		for (Icon icon : icons)
		{
			if (icon.widget.getIndex() == entry.getParam0())
			{
				if (icon.available())
				{
					entry.setParam0(icon.label.getIndex())
						.setParam1(icon.def.isAlt() ? ID_LIST_ROWS_ALTERNATE : ID_LIST_ROWS_PRIMARY)
						.setIdentifier(LIST_ROW_OP);
				}
				return;
			}
		}
	}

	private void updateVisibility(Widget window)
	{
		Widget[] children = window.getDynamicChildren();
		boolean overview = false;
		for (Widget c : children)
		{
			if (!ownWidgets.contains(c) && !c.isSelfHidden()
				&& c.getSpriteId() >= INDEX_SPRITE_MIN && c.getSpriteId() <= INDEX_SPRITE_MAX)
			{
				overview = true;
				break;
			}
		}

		for (Icon icon : icons)
		{
			boolean show = overview && (icon.available() || config.showUnavailable());
			if (icon.def.isGlobal())
			{
				show &= config.showGlobal() && !nexusDrawsAt(children, icon.widget);
			}
			else
			{
				show &= config.showTeleports();
			}

			if (icon.widget.isSelfHidden() == show)
			{
				icon.widget.setHidden(!show);
				if (!show && hoveredIcon == icon)
				{
					setHovered(icon, false);
				}
			}
		}

		if (overview && config.hideShields())
		{
			for (Widget c : children)
			{
				if (isShield(c) && !c.isSelfHidden())
				{
					c.setHidden(true);
					shieldsHiddenByUs = true;
				}
			}
		}
	}

	/** Newer Nexus Menu Map versions already draw the global teleports top-left; don't double them up. */
	private boolean nexusDrawsAt(Widget[] children, Widget ours)
	{
		for (Widget c : children)
		{
			if (!ownWidgets.contains(c) && !c.isSelfHidden() && c.getType() == WidgetType.GRAPHIC
				&& c.getOriginalX() == ours.getOriginalX() && c.getOriginalY() == ours.getOriginalY()
				&& c.getOriginalWidth() == FULL_ICON_SIZE)
			{
				return true;
			}
		}
		return false;
	}

	private void restoreShields()
	{
		if (!shieldsHiddenByUs)
		{
			return;
		}
		shieldsHiddenByUs = false;

		Widget window = client.getWidget(ID_PORTAL_WINDOW);
		if (window == null)
		{
			return;
		}

		// Shields only exist on the overview page, so if it's up they should be visible again
		boolean overview = false;
		for (Widget c : window.getDynamicChildren())
		{
			if (!ownWidgets.contains(c) && !c.isSelfHidden()
				&& c.getSpriteId() >= INDEX_SPRITE_MIN && c.getSpriteId() <= INDEX_SPRITE_MAX)
			{
				overview = true;
			}
		}
		for (Widget c : window.getDynamicChildren())
		{
			if (isShield(c))
			{
				c.setHidden(!overview);
			}
		}
	}

	/**
	 * Every teleport is on the overview already, so the shields stop opening the zoomed region pages:
	 * no "Select" option and no hover lift. Nexus Menu Map recreates them each time the interface loads.
	 */
	private void disableRegionSelect(Widget window)
	{
		for (Widget c : window.getDynamicChildren())
		{
			if (!ownWidgets.contains(c) && isShield(c))
			{
				c.clearActions();
				c.setHasListener(false);
				c.setOnMouseOverListener((Object[]) null);
				c.setOnMouseLeaveListener((Object[]) null);
				c.setOnOpListener((Object[]) null);
				c.setName("");
			}
		}
	}

	private static boolean isShield(Widget w)
	{
		return w.getSpriteId() >= SHIELD_SPRITE_MIN && w.getSpriteId() <= SHIELD_SPRITE_MAX;
	}

	private String teleportName(Icon icon)
	{
		TeleportDef def = icon.def;
		String name = def.getName();

		if (def.hasAlias())
		{
			String mode = configManager.getConfiguration(NEXUS_GROUP, "teleportName");
			if (mode == null || "BOTH".equals(mode))
			{
				name = String.format("%s (%s)", def.getName(), def.getAlias());
			}
			else if ("ALIAS".equals(mode))
			{
				name = def.getAlias();
			}
		}

		String shortcuts = configManager.getConfiguration(NEXUS_GROUP, "displayShortcuts");
		if (icon.shortcut != null && !"false".equals(shortcuts))
		{
			name = String.format("[%s] %s", icon.shortcut, name);
		}
		return name;
	}
}
