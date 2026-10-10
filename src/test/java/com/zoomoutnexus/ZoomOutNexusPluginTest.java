package com.zoomoutnexus;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.plugins.Plugin;

/**
 * Launches RuneLite with this addon (and Nexus Menu Map, if libs/nexus-map.jar is present) for local testing.
 * Run with {@code gradlew run}. Nexus Menu Map is looked up by name so this compiles without the jar.
 */
public class ZoomOutNexusPluginTest
{
	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception
	{
		Class<? extends Plugin> nexusMap;
		try
		{
			nexusMap = (Class<? extends Plugin>) Class.forName("net.antipixel.nexus.NexusMapPlugin");
		}
		catch (ClassNotFoundException e)
		{
			nexusMap = null;
		}

		if (nexusMap != null)
		{
			ExternalPluginManager.loadBuiltin(nexusMap, ZoomOutNexusPlugin.class);
		}
		else
		{
			ExternalPluginManager.loadBuiltin(ZoomOutNexusPlugin.class);
		}
		RuneLite.main(args);
	}
}
