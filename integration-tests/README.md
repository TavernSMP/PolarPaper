# Paper 1.20.1 acceptance checks

The fixture is a separate test plugin. It is compiled by `acceptanceJar`, written to `build/acceptance/`, and excluded from the deployable PolarPaper JAR. It runs only with `-Dpolarpaper.acceptance=true` and stops its test server after each phase.

Use an isolated directory and Paper **1.20.1 build 196** with Java **25**. Do not put the fixture on a production server. `Run-Acceptance.ps1` creates a fresh `.integration/acceptance-<timestamp>` directory, binds the server to `127.0.0.1`, builds cleanly, and boots it twice. It preserves previous runs and requires an EULA file already accepted by the operator.

```powershell
$env:GRADLE_USER_HOME = 'D:\cache\.gradle'
.\integration-tests\Run-Acceptance.ps1 `
  -PaperJar 'D:\test-tools\paper-1.20.1-196.jar' `
  -AcceptedEula 'D:\test-tools\eula.txt' `
  -Java 'D:\Java JDK\jdk-25.0.4.7-hotspot\bin\java.exe'
```

A first run may download server dependencies into the isolated directory. With another machine or OS, build using `bash gradlew clean build acceptanceJar --no-daemon`, install `dist/*.jar` and `build/acceptance/polarpaper-acceptance.jar` in a fresh server's `plugins/`, and run Java with the same acceptance system property. Start the same directory a second time after the first phase shuts down. Supply an operator-accepted EULA and use the localhost binding and small view distance from the PowerShell runner.

The first phase creates a world through the public API, fills it with blocks, 300 distinct wall states, a lit block, a chest and item metadata, a sign, chunk PDC, a persistent display, and a vehicle/passenger pair. It saves, unloads, and reloads that world. It also executes `/polar help`, `/polar list`, `/polar createblank`, `/polar save`, `/polar unload`, and `/polar load <name>.polar`; the command-created world must retain a gold block.

The second phase waits for the saved world to load on startup and checks the same persisted data. Both phases must stop cleanly, including save-on-stop. The expected result is **30 first-boot assertions and 16 restart assertions**. See [REPORT.md](REPORT.md) for the local validation record.

The fixture does not exercise a connected player's inventory UI, every entity type, rotated schematic placement, Slime v13 input, Folia, or an installed WorldEdit/FAWE integration. These are separate acceptance scenarios; compilation is not runtime evidence for them.
