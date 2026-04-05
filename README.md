# Chat Windows

A [Fabric](https://fabricmc.net/) client mod that filters matching chat into extra HUD panes you can move and resize.
Patterns use plain text by default; prefix with `regex:` when you want a full Java regular expression.

[View a demo on YouTube](https://youtu.be/lMcVxxYGzR0)

## Background

I wanted something like [LabyMod](https://www.labymod.net/)’s chat filtering—separate panes for matched messages—but as
its **own Fabric mod**, not tied to a big client stack. I couldn’t find an existing mod that did that the way I wanted,
so this project exists to fill that gap.

The codebase is **fully vibe coded**— the goal of a small mod I can actually use.

## Requirements

- Minecraft **1.21.11**
- [Fabric Loader](https://fabricmc.net/use/installer/) **0.18.4** or newer
- [Fabric API](https://modrinth.com/mod/fabric-api)
- **Java 21+**

## Download

Download the mod jar from **[GitHub Releases](https://github.com/Rainnny7/chat-windows/releases)** and place it in your
`mods` folder together with [Fabric API](https://modrinth.com/mod/fabric-api).

## Installation

Add the mod jar (and Fabric API) to your client’s `mods` folder. The mod is **client-only** and does not need to be
installed on a dedicated server.

## Usage

Matching lines are **removed from the main chat HUD** and shown only in the windows that match. Configure everything
with the `/chatwindow` command (client-side).

### Patterns

- **Plain text** — The pattern is matched as a literal substring on chat with formatting codes stripped. Characters like
  `.`, `*`, `+`, and brackets are not special.
- **`regex:` prefix** — Everything after `regex:` (any casing) is compiled as a Java `Pattern`. Example:
  `regex:.*joined the lobby.*`

Legacy configs without `patternFormat` in JSON are treated as full-regex strings and are migrated on load (each pattern
is stored with a `regex:` prefix when you save).

### Commands

| Command                                      | Description                                                       |
|----------------------------------------------|-------------------------------------------------------------------|
| `/chatwindow create <id> <pattern>`          | Create a window (replace if `id` already exists).                 |
| `/chatwindow add-pattern <id> <pattern>`     | Add another pattern to a window.                                  |
| `/chatwindow list-patterns <id>`             | List patterns for a window.                                       |
| `/chatwindow remove-pattern <id> <position>` | Remove pattern by 1-based index (cannot remove the last pattern). |
| `/chatwindow list`                           | List all windows.                                                 |
| `/chatwindow position <id>`                  | Toggle drag/resize mode for that window.                          |
| `/chatwindow toggle <id>`                    | Show or hide a window.                                            |
| `/chatwindow remove <id>`                    | Delete a window.                                                  |

### Config

Settings are saved under `.minecraft/config/chat-windows.json` (via [Fabric Loader](https://fabricmc.net/)’s config
directory).