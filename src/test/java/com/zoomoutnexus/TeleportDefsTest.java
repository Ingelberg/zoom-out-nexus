package com.zoomoutnexus;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TeleportDefsTest
{
	private static TeleportDef[] load()
	{
		return new Gson().fromJson(new InputStreamReader(
			ZoomOutNexusPlugin.class.getResourceAsStream("teleports.json"), StandardCharsets.UTF_8), TeleportDef[].class);
	}

	@Test
	public void keysAreUnique()
	{
		Set<Long> keys = new HashSet<>();
		for (TeleportDef def : load())
		{
			assertTrue("duplicate " + def.getName(), keys.add(def.getKey()));
		}
	}

	@Test
	public void boatTeleportsAreGlobalAndSplitByColumn()
	{
		TeleportDef boat = null, lastBoat = null;
		for (TeleportDef def : load())
		{
			if (def.getName().equals("Boat")) boat = def;
			if (def.getName().equals("Last Boat")) lastBoat = def;
		}
		assertTrue(boat.isGlobal() && lastBoat.isGlobal());
		assertFalse(boat.isAlt());
		assertTrue(lastBoat.isAlt());
		assertEquals(boat.getEnabledSprite(), lastBoat.getEnabledSprite());
	}

	@Test
	public void iconsFitOnTheOverviewWithoutOverlapAtDefaultSize()
	{
		TeleportDef[] defs = load();
		// Positions are spaced for 20px; the 26px default overlaps slightly by choice
		int size = 20;
		int half = size / 2;
		for (TeleportDef a : defs)
		{
			if (a.isGlobal()) continue;
			assertTrue(a.getName(), a.getX() - half >= 0 && a.getX() + half <= 478);
			assertTrue(a.getName(), a.getY() - half >= 0 && a.getY() + half <= 272);
			for (TeleportDef b : defs)
			{
				if (b == a || b.isGlobal()) continue;
				boolean overlap = Math.abs(a.getX() - b.getX()) <= size && Math.abs(a.getY() - b.getY()) <= size;
				assertFalse(a.getName() + " overlaps " + b.getName(), overlap);
			}
		}
	}
}
