# GT6UOU

GT6UOU is an experimental Minecraft 1.21.1 NeoForge mod that ports and reimagines GregTech 6-style systems for modern Minecraft.

The project is early in development. At the moment it focuses mostly on foundational world-generation and resource systems rather than finished gameplay progression.

## Current focus

- GT-style stone layer generation.
- Large ore veins tied to host rock types.
- Rich, normal, and poor ore variants.
- Raw material, crystal, and raw block resource forms.
- Surface prospecting indicators for ore veins.
- Clay, sand, turf, oilsands, raw oil pockets, and bedrock fluid deposit prototypes.
- Developer tools for testing world generation.
- A basic in-game geology handbook.

## Target platform

- Minecraft: 1.21.1
- Loader: NeoForge
- Mod id: `gt6uou`

## Development status

This is not a stable public gameplay release yet. APIs, registries, generated assets, world-generation behavior, and balance values may change heavily while the port is being built.

## Building

Use the Gradle wrapper:

```powershell
.\gradlew.bat build
```

For quick compile checks:

```powershell
.\gradlew.bat compileJava
```

## Reference repositories

Several external projects are used only as local references while developing this mod. They are intentionally excluded from this repository via `.gitignore`.

Ignored reference folders include:

- `gregtech6-master`
- `GregTech-Modern-1.20.1`
- `Core-Modern-dev`
- `Modpack-Modern-dev`
- `TerraFirmaCraft-1.21.x`
- `GregTech-Odyssey-main`

See [NOTICE.md](NOTICE.md) for attribution and license notes.

## License

Unless stated otherwise, this project's source code is licensed under LGPL-3.0. See [LICENSE.md](LICENSE.md).

This project is not affiliated with or endorsed by the GregTech, GregTech CEu, TerraFirmaCraft, TerraFirmaGreg, or GregTech Odyssey teams.
