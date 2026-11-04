# NahNoMinimap

**English** | [Русский](README.ru.md)

A Paper plugin for restricting supported map and minimap mods.

## Features

- Restrict supported minimaps and world maps.
- Notify administrators when supported mod channels are detected.
- Configure restrictions globally or for individual worlds.
- Manage bypass and alert permissions.
- Customize restriction messages using language files.
- Integrate restriction messages with supported resource-pack plugins.

## Supported mods

| Mod | Support |
| --- | --- |
| Xaero's Minimap | Restrictions and alerts |
| Xaero's World Map | Restrictions and alerts |
| Diorama | Restrictions |
| JourneyMap | Restrictions and alerts |
| VoxelMap | Restrictions and alerts |
| Conflux Map | Radar restrictions and alerts; full restriction requires version 0.1.15 or newer |
| WMap | Alerts |
| Surveyor Framework | Alerts |

Restrictions depend on the mod and its version. Modified clients may ignore
server restrictions. A detected channel is a sign that a mod may be present,
but does not necessarily prove that a player is using it.

## Requirements

- Paper or a compatible fork
- Minecraft 26.3
- PacketEvents

Currently, only Minecraft 26.3 is supported. Older versions are not planned,
but support may be added on request.

## Download

This repository contains the source code. Download ready-to-use builds from
[Modrinth](https://modrinth.com/plugin/nnm).

## Documentation

Configuration, permissions and setup instructions are available in the
[Wiki](https://github.com/28mielav/NahNoMinimap/wiki).

## Issues and suggestions

Found a bug or want support for another map mod?
[Open an issue](https://github.com/28mielav/NahNoMinimap/issues).
