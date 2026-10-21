# Validation record

Validated locally on 2026-10-21 using Paper **1.20.1 build 196**, API **1.20.1-R0.1-SNAPSHOT**, and Eclipse Temurin **Java 25.0.4+7** on Windows. The test server was isolated and bound to **127.0.0.1:25585**. No production server was restarted or modified.

`gradlew.bat clean build acceptanceJar --no-daemon` passed. The single deployment artifact is staged under `dist/`; the acceptance plugin is separate.

## Runtime results

- First boot: **30 assertions passed**.
- Second boot: **16 assertions passed**.
- Save-on-stop completed on both boots without a timeout or server error.
- `/polar` help/list/createblank/save/unload/load executed successfully. The command-created world retained its saved gold block.
- API save/unload/reload retained normal and negative-coordinate blocks, a 300-entry global block palette, block light, world time, chest contents, item PDC, block-entity PDC, chunk PDC, and sign text.
- Armor stand, item display, and vehicle/passenger data survived reload and restart. The passenger was restored once and remained attached to its vehicle.
- A newer Minecraft data version was rejected.
- The plugin loaded with WorldEdit absent.

## Scope

The runtime checks cover the API and console-command flow on Paper 1.20.1. They do not establish live player GUI behavior, all entity types, rotated schematic placement, Slime v13 import, Folia, or installed WorldEdit/FAWE compatibility. Those paths were compiled where applicable; Folia is explicitly unsupported on this branch.

## Evidence

The following lines are extracted from the local server logs, excluding unrelated runtime and library-download output.

```text
[11:43:24 INFO]: [PolarPaperAcceptance] PASS create a Polar world
[11:43:24 INFO]: [PolarPaperAcceptance] PASS legacy gamerules applied
[11:43:24 INFO]: [PolarPaperAcceptance] PASS fixture passenger attached
[11:43:24 INFO]: [PolarPaperAcceptance] PASS fixture container populated
[11:43:24 INFO]: [PolarPaperAcceptance] PASS save a Polar file
[11:43:24 INFO]: [PolarPaperAcceptance] PASS unload the Polar world
[11:43:25 INFO]: [PolarPaperAcceptance] PASS loaded world exists
[11:43:25 INFO]: [PolarPaperAcceptance] PASS vanilla world saving disabled
[11:43:25 INFO]: [PolarPaperAcceptance] PASS block palette and coordinates
[11:43:25 INFO]: [PolarPaperAcceptance] PASS negative chunk coordinates
[11:43:25 INFO]: [PolarPaperAcceptance] PASS global block palette (300 states)
[11:43:25 INFO]: [PolarPaperAcceptance] PASS block lighting
[11:43:25 INFO]: [PolarPaperAcceptance] PASS world time restored
[11:43:25 INFO]: [PolarPaperAcceptance] PASS container inventory
[11:43:25 INFO]: [PolarPaperAcceptance] PASS item PDC
[11:43:25 INFO]: [PolarPaperAcceptance] PASS block entity PDC
[11:43:25 INFO]: [PolarPaperAcceptance] PASS sign NBT
[11:43:25 INFO]: [PolarPaperAcceptance] PASS chunk PDC
[11:43:25 INFO]: [PolarPaperAcceptance] PASS entity NBT and PDC
[11:43:25 INFO]: [PolarPaperAcceptance] PASS passenger saved exactly once
[11:43:25 INFO]: [PolarPaperAcceptance] PASS passenger mount restored
[11:43:25 INFO]: [PolarPaperAcceptance] PASS display entity
[11:43:25 INFO]: [PolarPaperAcceptance] PASS reject newer Minecraft data versions
[11:43:25 INFO]: [PolarPaperAcceptance] PASS register the /polar command
[11:43:25 INFO]: [PolarPaperAcceptance] PASS execute /polar list
[11:43:25 INFO]: [PolarPaperAcceptance] PASS execute /polar createblank
[11:43:25 INFO]: [PolarPaperAcceptance] PASS execute /polar save
[11:43:25 INFO]: [PolarPaperAcceptance] PASS execute /polar unload
[11:43:25 INFO]: [PolarPaperAcceptance] PASS execute /polar load
[11:43:25 INFO]: [PolarPaperAcceptance] PASS command save and load preserves blocks
[11:43:25 INFO]: [PolarPaperAcceptance] ACCEPTANCE FIRST BOOT PASS: 30 assertions
[11:43:25 INFO]: [polarpaper] Saved 'acceptance' in 31ms
[11:43:37 INFO]: [polarpaper] Loading polar world: acceptance
[11:43:38 INFO]: [polarpaper] Loading polar world: command_fixture
[11:43:39 INFO]: [PolarPaperAcceptance] PASS loaded world exists
[11:43:39 INFO]: [PolarPaperAcceptance] PASS vanilla world saving disabled
[11:43:39 INFO]: [PolarPaperAcceptance] PASS block palette and coordinates
[11:43:39 INFO]: [PolarPaperAcceptance] PASS negative chunk coordinates
[11:43:39 INFO]: [PolarPaperAcceptance] PASS global block palette (300 states)
[11:43:39 INFO]: [PolarPaperAcceptance] PASS block lighting
[11:43:39 INFO]: [PolarPaperAcceptance] PASS world time restored
[11:43:39 INFO]: [PolarPaperAcceptance] PASS container inventory
[11:43:39 INFO]: [PolarPaperAcceptance] PASS item PDC
[11:43:39 INFO]: [PolarPaperAcceptance] PASS block entity PDC
[11:43:39 INFO]: [PolarPaperAcceptance] PASS sign NBT
[11:43:39 INFO]: [PolarPaperAcceptance] PASS chunk PDC
[11:43:39 INFO]: [PolarPaperAcceptance] PASS entity NBT and PDC
[11:43:39 INFO]: [PolarPaperAcceptance] PASS passenger saved exactly once
[11:43:39 INFO]: [PolarPaperAcceptance] PASS passenger mount restored
[11:43:39 INFO]: [PolarPaperAcceptance] PASS display entity
[11:43:39 INFO]: [PolarPaperAcceptance] ACCEPTANCE RESTART PASS: 16 assertions
[11:43:39 INFO]: [polarpaper] Saved 'acceptance' in 64ms
```

Deployment JAR SHA-256: `f88a231471ef2b8c54edb9d800bb1f658e50217955ab473007e773a0e3fc633c`.
