<div align="center">

<img src="docs/images/logo.png" width="160" alt="Fast Compose Preview logo">

# Fast Compose Preview

**Instant Jetpack Compose previews in Android Studio and IntelliJ IDEA. No compiler, no build, no waiting.**

Renders the `@Preview` functions of the open Kotlin file as you type, powered by
[Fast Compose](https://github.com/fast-compose/fast-compose).

[![Fast Compose](https://img.shields.io/maven-central/v/io.github.fast-compose/fast-compose?label=Fast%20Compose)](https://central.sonatype.com/namespace/io.github.fast-compose)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
![IDEs](https://img.shields.io/badge/IDEs-Android%20Studio%20%7C%20IntelliJ%20IDEA%202026.1%2B-3DDC84?logo=androidstudio&logoColor=white)
![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?logo=kotlin&logoColor=white)

</div>

---

## Features

- **Fast Compose Preview** tool window on the right side bar, next to Running Devices.
- Follows the selected Kotlin editor and its unsaved text. Selecting another file shows that file's previews, or none;
  a previous file's preview is never kept.
- `@Preview` first: a file is checked for a `@Preview` annotation in the background before anything else, with the
  engine's `KotlinParser.hasPreviews`, so comments, strings and imports don't count. Without one, nothing is done: no parsing, no
  resource listing, no lookups in other files, no rendering, no logs. Adding one starts the preview.
- Each `@Preview` gets its own frame at a device size, scaled to fit the panel with the device's aspect ratio, so
  `Scaffold` screens look as they do on a device. Previews without `widthDp`/`heightDp` wrap their content up to that
  size. Scaling changes the density, so text stays sharp and clicks land correctly. Previews are centered and the
  list scrolls both ways.
- Bar below the previews:
  - **Device**: Phone (411 × 891 dp, default), Foldable (673 × 841 dp), Tablet (1280 × 800 dp) or Desktop
    (1920 × 1080 dp).
  - **Zoom**: − and +, and a menu with Fit (default), 50 %, 75 %, 100 % and 150 %; Ctrl + mouse wheel also zooms.
    Device and zoom are saved per project.
- Title bar buttons:
  - **Refresh Preview** rebuilds the preview, reading the file, the files it uses and its resources again.
  - **Open Preview in Window** moves the preview to its own window, which can be dragged to a second display; the
    window's own Dock button moves it back. The IDE remembers the mode and position.
  - **Report Issue** opens a new GitHub issue for the Fast Compose engine (rendering, unsupported APIs, errors) or the
    plugin, prefilled with the plugin, engine, IDE and OS versions. No code is sent.
- Crashes never leave an empty panel: an exception Compose throws while composing, measuring or drawing a preview is
  shown in the logs (`ComposeCode.reportCrash`) in a new panel that skips rendering previews, so it can't crash again,
  until the file is edited, Refresh is pressed or another file is opened. If that panel crashes too, the error shows
  as plain text.
- Logs pane for the previewed file's parse errors, runtime errors and output; clicking a line opens it. Lines about
  other files the preview uses are behind an **Other files** toggle, and clicking one opens that file.
- Composables, classes and values from other project files (see [Other files](#other-files)).
- Images and strings from `R` and `Res` (see [Resources](#resources)).
- Network images (`AsyncImage`) through Coil's OkHttp fetcher, cached on disk (up to 100 MB) in the IDE's system
  folder. Ktor is not bundled: it needs a newer kotlinx-coroutines than the IDE provides.
- Runs only while visible: a hidden tool window or a minimized window stops parsing, composing and drawing, and
  catches up with the editor when shown again.

## Other files

A preview can use top-level functions, classes, objects and properties declared in other files, e.g. `AppTheme` from
`ui/theme/Theme.kt`. Names are looked up as Kotlin does: explicit imports, then the file's package, then star imports.

- **Where:** the package's folder under each source root of the previewed file's module and the modules it depends on,
  its own module first; generated sources under `build/` are skipped. Works while the IDE is indexing; without a
  synced project, folders next to the previewed file are used.
- **Which file:** the first one declaring the name; a declaration with a body wins over one without. Private
  declarations are skipped.
- **`expect` / `actual`:** for an `expect` declaration (function, object, class or property), the `actual` is taken
  from the same module's other source sets: `jvmMain` or `desktopMain` first (the IDE runs on the JVM), then
  `androidMain`, then the rest, with iOS, JS, Wasm and native last. The `expect` is then blanked out of its file, line
  positions kept, so previews see only the `actual`. Without an `actual`, the `expect` is used as is.
- **Caching:** each file's package and top-level names are parsed once and reused until the file changes. Unsaved
  editor text is used.
- **Changes:** editing a file the preview looked into, or adding, removing or moving Kotlin files, rebuilds the
  preview 300 ms after the last change, so classes and values outside composables update too. Nothing rebuilds while
  the preview is hidden.
- **Project or library:** a package belongs to the project when a module's source root (outside `build/`) has a
  folder for it; names from other packages are library names, never looked up or reported. Answers are cached until
  source roots or folders change.
- Files must be in a folder matching their package. Library code is never read; it comes from bindings.

## Resources

Previews read resources from the Gradle source set layout of the previewed file:

| Kind | Folder |
|---|---|
| Compose Multiplatform `Res` | `src/<sourceSet>/composeResources`, e.g. `shared/src/commonMain/composeResources` |
| Android `R` | `src/<sourceSet>/res`, e.g. `app/src/main/res` |

- **Order:** the file's own source set, then the modules it depends on in classpath order (e.g. `androidMain`,
  `commonMain`, then library modules). The first folder with a match wins.
- **Lookup by name:** `R.string.title` and `Res.string.title` find the same name; if two modules declare it, the
  previewed module's wins.
- **Reading:** folders are listed from the IDE's file tree, and a file is read only when a preview needs it, then
  cached (up to 16 MB per folder). Unsaved edits of open files, such as `strings.xml`, show right away.
- **Changes:** a changed file refreshes only its folder. Nothing refreshes while the preview is hidden.
- Library resources from AARs and `build/` folders are not read.

## Compose

The plugin ships its own Compose Multiplatform, Material 3 and Skiko (with native libraries for Windows, macOS and
Linux, each on x64 and ARM64), matching the versions Fast Compose is built with. It does not use the IDE's Compose. `BundledSkiko` loads the
plugin's Skiko native library from its jar into `~/.skiko`. The IDE provides Kotlin and coroutines.

## Run

```bash
./gradlew runIde -PplatformPath="<IDE install folder>" -PrunIdeProject="<project folder>"
```

- `platformPath`: an installed Android Studio or IntelliJ IDEA (build 261 or newer), used for compiling and by
  `runIde`; nothing is downloaded. Pass it with `-P`, or set `platformPath=<IDE install folder>` in
  `local.properties` (not committed), which IDE syncs use since they can't pass `-P`.
- `platformVersion`: instead of `platformPath`, an IntelliJ IDEA version Gradle downloads, e.g. `2026.1` (CI).
- `runIdeProject` (optional): the project to open; without it the welcome screen shows.
- `localEngine` (optional): a local Fast Compose engine checkout, e.g. `../fast-compose`, built instead of the
  Maven Central release (`fastCompose` in `gradle/libs.versions.toml`), to try engine changes before they are
  released.

## Release

The version is `pluginVersion` in `gradle.properties`; its change notes are its `## <version>` section of
`CHANGELOG.md`, which the build turns into the Marketplace's change notes.

```bash
./gradlew buildPlugin -PplatformPath="<IDE install folder>"
./gradlew verifyPlugin -PplatformPath="<IDE install folder>" -PverifyIdes="<IDE folder>;<another IDE folder>"
./gradlew publishPlugin -PplatformPath="<IDE install folder>"
```

- `buildPlugin` writes `build/distributions/fast-compose-intellij-plugin-<version>.zip`, to install from disk.
- `verifyPlugin` checks compatibility with the IDEs in `verifyIdes`: installed IDE folders separated by `;`, and/or
  `recommended` for the versions JetBrains recommends (downloaded).
- `publishPlugin` signs the plugin and uploads it to the JetBrains Marketplace. It reads `CERTIFICATE_CHAIN`,
  `PRIVATE_KEY`, `PRIVATE_KEY_PASSWORD` and `PUBLISH_TOKEN` from the environment. A pre-release version such as
  `0.2.0-beta.1` goes to its channel (`beta`).
- `signPlugin` writes `build/distributions/fast-compose-intellij-plugin-<version>-signed.zip`, e.g. for the first
  upload by hand. Locally, pass the certificate and key as files instead of contents:
  `-PcertificateChainFile=<chain.crt> -PprivateKeyFile=<private.pem>`, with the password in `PRIVATE_KEY_PASSWORD`.

On GitHub, `.github/workflows/build.yml` builds and verifies pull requests against IntelliJ IDEA 2026.1 and the
recommended IDEs, and a `v<version>` tag publishes to the Marketplace and creates a GitHub release with the signed
zip. It needs the four values above as repository secrets.

## Requirements

- IntelliJ IDEA or Android Studio 2026.1 (build 261) or newer
- JDK 21 for building

## Third-party libraries

Bundled in the plugin, all under the Apache 2.0 license: [Fast Compose](https://github.com/fast-compose/fast-compose),
[Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform) and
[Skiko](https://github.com/JetBrains/skiko), [Material 3 for Compose](https://github.com/JetBrains/compose-multiplatform),
[Coil](https://github.com/coil-kt/coil), [OkHttp](https://github.com/square/okhttp) and
[Okio](https://github.com/square/okio).

## License

Apache 2.0
