# Anchor RTP Bot — Minecraft 1.21.11

Client-side Fabric mod.

## Current behavior
- Toggle with `O`
- Sends `/rtp`
- Scans a 100-block spherical radius for Respawn Anchors
- Walks toward the nearest anchor and mines it
- Rescans and continues until no anchors are found
- Uses `/rtp` when the area is cleared
- Uses `/rtp` when another player is detected within 32 blocks
- Eats when hunger is 10 or lower if food is in hotbar
- Keeps the selected pickaxe equipped for Mending/XP repair

## Build

Use Java 21.

```powershell
./gradlew.bat build
```

The finished JAR will be in `build/libs/`.

## Important
This is an automation client and may be against the rules of servers that prohibit bots/macros. Test only where automation is allowed.

The movement/mining logic is intentionally simple in this starter build; it does not yet implement full collision-aware pathfinding or a dedicated XP-farm routine.


## GitHub build

You do not need to install Gradle locally. This repository includes a GitHub Actions workflow.

1. Upload/commit all project files to your repository.
2. Open the **Actions** tab.
3. Click **Build mod** on the left.
4. Click **Run workflow**.
5. Wait for the green checkmark.
6. Open the completed workflow run.
7. Scroll to **Artifacts**.
8. Download **AnchorRTPBot** and extract the JAR.
9. Put the JAR in your Fabric 1.21.11 `mods` folder.

The workflow uses Java 21 and builds the JAR with Gradle. GitHub documents that Gradle builds produce JARs and that artifacts can be uploaded from `build/libs`.
