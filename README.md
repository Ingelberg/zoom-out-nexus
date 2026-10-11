# Zoom Out Nexus

> **This plugin is vibe coded.** Claude (an AI coding assistant) wrote it with my direction and in-game testing. It's been used and tweaked in-game, but not every line has been hand-reviewed.

An addon for [Nexus Menu Map](https://github.com/Antipixel/nexus-map) by Antipixel
([issue #29](https://github.com/Antipixel/nexus-map/issues/29)).

- Every teleport is drawn directly on the world overview, so you don't have to click into a region first.
- Respawn Point, Boat and Last Boat sit in the top-left of the overview. Newer Nexus Menu Map versions
  already draw these, and when they do this addon skips its own copies.

Nexus Menu Map must be installed, enabled and set to map mode. This addon doesn't change that plugin's
code. It adds its own icons to the same interface and shows them only while the overview page is up.

## Config

| Option | Default | |
|---|---|---|
| Teleports on overview | on | The main feature |
| Boat / Respawn top-left | on | Turning it off also hides Nexus Menu Map's own copies |
| Show unavailable | off | Grey icons for portals your nexus doesn't have |
| Icon size | 26 | 10–50. Positions are spaced for 20, so neighbours touch a little at 26 |
| Enlarge on hover | on | Draws a bigger copy of the hovered icon on top of its neighbours |
| Enlarge size | 34 | 10–50. Size of the hovered copy |
| Hide region shields | on | Clears the overview |

While the addon shows teleports, the region shields no longer open the zoomed region pages, since
every teleport is already on the overview. Names and shortcut keys follow Nexus Menu Map's own settings.
Icons are drawn without a border.

## Positions

Icon positions are in `src/main/resources/com/zoomoutnexus/teleports.json`. `x`/`y` give the icon
**centre** on Nexus Menu Map's 478×272 overview sprite. Global entries give the top-left corner instead.
Sprite IDs come from Nexus Menu Map's `RegionDef.json`. If upstream adds a teleport, copy the entry
across and choose a spot for it. `TeleportDefsTest` fails if two icons overlap at 20px.

## Running locally

`libs/nexus-map.jar` is Nexus Menu Map built from source. It's only on the test classpath, so
`gradlew run` can load both plugins:

```
git clone https://github.com/Antipixel/nexus-map && cd nexus-map && gradlew jar
copy build\libs\nexus-map-*.jar <this repo>\libs\nexus-map.jar
gradlew run
```
