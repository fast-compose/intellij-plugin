# Changelog

Each version's section is the plugin's change notes on the JetBrains Marketplace: one `- ` line per change.

## 0.2.1

- Each preview gets its own frame at a device size (Phone by default), scaled to fit the panel, so screens with `Scaffold`, tabs and bottom bars look as they do on a device. Previews are centered, with larger names.
- One window button: Open Preview in Window; the window's own Dock button moves it back.
- Device and Zoom bar below the previews: Phone, Foldable, Tablet or Desktop size; Fit, 50 %, 75 %, 100 % or 150 %, − and +, and Ctrl + mouse wheel. Saved per project.

## 0.2.0

- First release: renders `@Preview` functions of the open Kotlin file as you type, with Fast Compose 0.2.0.
- Tool window next to Running Devices; Open Preview in Window to move it to its own window, e.g. on a second display.
- Android and Kotlin Multiplatform projects: code from other files and modules, `expect` declarations from their JVM or Android `actual`, images and strings from `R` and `Res` across modules.
- Logs pane with parse errors, runtime errors and `println` output; other files' problems behind a toggle.
- Refresh and Report Issue buttons; crashes show in the logs instead of an empty panel.
- Runs only while visible, and only for files with a `@Preview`.
