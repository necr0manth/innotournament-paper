# InnoTournament Paper

InnoTournament Paper is a Kotlin/Paper port of the original
[InnoTournament](https://github.com/necr0manth/InnoTournament) Java/Architectury project. The original
project implements a team-based survival tournament with limited lives, a shrinking world border, team
scoring, and progression tied to Minecraft advancements.

This repository moves that tournament framework to a server-side Paper plugin. It replaces the original
multi-loader and ECS-based architecture with Paper APIs and direct Kotlin domain models for players, teams,
and tournament state.

> **Status:** active migration. The project builds and the core tournament lifecycle is implemented, but
> this port is not yet a complete drop-in replacement for the original project or a production release.

## Features

- Four-phase tournament lifecycle: `CREATED`, `PREPARED`, `STARTED`, and `ENDED`.
- Player-created teams with ownership, invitations, open joining, renaming, leaving, and kicking.
- Limited player lives, spectator handling, team-size score modifiers, final standings, and winner detection.
- Team-wide advancement tracking and synchronization.
- Separate pre-start and main event sets loaded from JSON.
- Configurable events for lifecycle transitions, time, player ticks, kills, and advancements.
- Event actions for world-border changes, PvP control, scoring, command execution, logging, and start-box
  placement.
- Server-side start-box structures, configured-world spawn and border centering, and multi-dimension border
  resizing.
- Native Paper Brigadier commands and Adventure components.

## Architecture

- `PluginPlayer` and `PluginTeam` hold plugin-lifetime player and team state.
- `TournamentPlayer` and `TournamentTeam` hold participation state for the active tournament.
- `Tournament` owns the lifecycle, participants, scoring, lives, advancement synchronization, and cleanup.
- `TournamentData` loads tournament settings and the relative paths of the two event files.
- `TournamentEvent` definitions create isolated runtime subscriptions for each tournament.
- `EventSubscriptionView` exposes event subscriptions without exposing event invocation to consumers.

Only one tournament can be active at a time.

## Requirements

- JDK 25
- Paper 26.1.2

The Gradle build downloads the Paper development bundle and Kotlin runtime dependencies automatically.

## Building

On Windows:

```powershell
.\gradlew.bat clean build
```

On Linux or macOS:

```bash
./gradlew clean build
```

The deployable shadow JAR is written to:

```text
build/libs/innotournament-paper-1.0-all.jar
```

## Development Server

The repository includes a `runServer` task configured for Paper 26.1.2 with a 2 GiB heap:

```powershell
.\gradlew.bat runServer
```

## Installation and Configuration

1. Build the project.
2. Copy `innotournament-paper-1.0-all.jar` into the Paper server's `plugins` directory.
3. Start the server once, then create the tournament directory if it does not exist. No configuration is
   generated automatically.
4. Place tournament JSON files under:

   ```text
   plugins/innotournament-paper/tournaments/
   ```

5. Select a configuration and prepare or start the tournament with the administrative commands below.

A tournament file defines the tournament and pre-tournament spawns, optional start-box structure and
position, pre-start game mode, score modifiers, last-team score, and paths to the pre-start and main event
files. Event paths are resolved relative to the tournament JSON file.

No ready-made tournament configuration is bundled yet.

## Commands

### Tournament administration

These commands require the vanilla `minecraft.command.function` permission.

- `/tournament select <filePath>` - select a JSON file from the tournament directory.
- `/tournament prepare` - create and prepare the selected tournament.
- `/tournament start` - prepare the tournament if needed, then start it.
- `/tournament stop` - remove the active tournament and its runtime state.

### Teams

The `/teams` command, with `/p` as an alias, provides:

- team creation, listing, joining, and leaving;
- player invitations and invitation rejection;
- owner-only renaming, open-join settings, and kicking;
- clickable Adventure-based confirmation and management actions.

`/teams removeAll` requires the vanilla `minecraft.command.team` permission.

Players with `innotournament.admin` bypass pre-start game-mode enforcement.

### Utility

- `/worldborderinfo` - show the direction and distance to the nearest side of the current world's border.

## Current Limitations

- Team, player, tournament, and invite state is stored in memory and is not persisted across restarts.
- Invite expiration is not active because the invite scheduler is not registered yet.
- The sidebar, pre-start boundary enforcement, repeated-team initialization, and some natural-end listener
  cleanup paths are still incomplete.
- Offline participants can keep a team alive, while an empty started tournament ends on its first tick.
- Original custom content, generators, client networking, and JourneyMap integration have not been ported.
- The repository has no automated test suite or bundled end-to-end tournament configuration yet.

See the [original Architectury implementation](https://github.com/necr0manth/InnoTournament) for the
pre-port codebase.
