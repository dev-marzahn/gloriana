# Contributing

## Prerequisites

- A JDK. Gradle downloads JDK 25 for its daemon if needed (`gradle/gradle-daemon-jvm.properties`).
- The Android SDK, with its path in `local.properties` (`sdk.dir=…`). The Android targets compile in every build.

## Running

- Desktop: `./gradlew :desktopApp:run`. The run is the desktop's debuggable build: it sets the system property
  `gloriana.debuggable=true`; a packaged app never sets it. macOS packages (dmg) need a version of 1.0.0 or later,
  so they can't be built before the 1.0 release.
- Android: `./gradlew :androidApp:installDebug` with a device or emulator attached, then open Gloriana. The debug
  build installs as `dev.marzahn.gloriana.debug`, beside a release install of `dev.marzahn.gloriana`.

Android builds one APK per ABI (`arm64-v8a`, `armeabi-v7a`, `x86_64`). Each APK's version code is the app's version
code times 10 plus its ABI's number (`armeabi-v7a` 1, `arm64-v8a` 2, `x86_64` 3), so every ABI of one release can be
published side by side and a device gets its best match. The app version is `gloriana.version` in
`gradle.properties`, read only through `appVersion` in `build-logic`; `AppVersion` and `Abi` there turn it into the
version codes, refuse a version whose codes would pass Google Play's limit, and are tested there.

The Android app keeps its data out of Android's cloud backup and device-to-device transfer (`allowBackup`, the
backup and data extraction rules): chats and keys stay on the device unless the user exports them.

## Modules

Each module's code lives under its own package root, so a test can tell the modules apart by package.

- `sharedLogic` (`dev.marzahn.gloriana.logic`): the domain and everything below it (storage, networking), with no
  Compose. One package per feature, each holding its domain types, use cases and storage. A feature's Room entities
  and DAOs stay inside it: other features see its repositories and domain types only, so a feature can change its
  tables without touching another feature's code.
- `sharedUI` (`dev.marzahn.gloriana.ui`): the Compose screens, one package per screen. Each screen has a `ViewModel`
  exposing one `StateFlow` of screen state, and a Navigation 3 route. Every route is registered in
  `navigation/BackStack.kt`, so the back stack survives a configuration change and process death.
- `androidApp` (`dev.marzahn.gloriana.android`) and `desktopApp` (`dev.marzahn.gloriana.desktop`): each platform's
  entry point and its implementations of the platform seams (plain interfaces in common code), bound in that
  platform's Koin module.
- `stubHost` (`dev.marzahn.gloriana.stubhost`): a test host for AI provider requests, used by tests and QA. Never part
  of an app.
- `detektRules` (`dev.marzahn.gloriana.detekt`): the project's own detekt rules, run on every other module.

## Build

Every module's build is a few lines on top of the convention plugins in `build-logic`: `gloriana.kmp-library`,
`gloriana.android-app`, `gloriana.kotlin-jvm`, `gloriana.compose`, `gloriana.koin`, `gloriana.serialization`,
`gloriana.room` (Room, its compiler and the bundled SQLite driver), `gloriana.detekt` and `gloriana.detekt-core` (detekt
without the project's own rules, for `detektRules` itself). They hold the shared settings once: the SDK levels, the JVM
level, warnings as errors, JUnit Platform for every JVM test, lint and detekt.

Every target compiles to Java 11 and, off Android, against Java 11's class library (`-Xjdk-release`, `release`), so
desktop and shared code can't use a JDK method newer than Java 11. That alone doesn't keep shared code within what
Android 8 offers: some Java 11 methods (`List.of`, `String.isBlank`) need a newer Android. Lint's `NewApi` check on
the Android target catches those.

Tests in `sharedLogic` and `sharedUI` go in `jvmTest`: the modules have no Android host tests, so a `commonTest` would
run on the JVM only and make the Android plugin warn.

## Dependency injection

Koin, one module per layer: `logicModule` (`sharedLogic`), `uiModule` (`sharedUI`) and each app's platform module.
Each app has one app module (`androidAppModule`, `desktopAppModule`) that includes all three; the app starts Koin with
it, and a `verify()` test per platform (`AndroidGraphTest`, `DesktopGraphTest`) checks that every definition in it
resolves. Definitions use the Koin compiler plugin's DSL (`viewModel<LibraryViewModel>()`), which wires constructors
at compile time. The plugin's own graph check is off: it cannot yet follow modules declared in other Gradle modules,
so the `verify()` tests are the graph check.

## Storage

The library is one Room database, `GlorianaDatabase` in `sharedLogic`'s `storage` package, on the bundled SQLite
driver in WAL mode. Each platform's Koin module binds the database its builder makes (`androidGlorianaDatabase`,
`desktopGlorianaDatabase`). Both pass Room's generated `GlorianaDatabase_Impl` as the factory, so nothing finds it by
reflection and the desktop release's ProGuard keeps it. `desktopApp/proguard-rules.pro` keeps only what the bundled
driver's native code reaches by name: its native methods, and the `SQLiteException(String)` constructor it throws.
`DatabaseOpenedAtStart` opens the database at start on the app-wide `ApplicationScope` (`logic.app`). A failure
there goes to its `onFailure`, which in the app is `stopApp`: on Android the default crash handler, on desktop the
stack trace and exit code 1. The file is `library.db`:

- Android: the app's files directory, `/data/data/dev.marzahn.gloriana/files/library.db`
  (`dev.marzahn.gloriana.debug` for the debug build).
- Desktop: the per-OS application-data directory, in `Gloriana`, or `Gloriana Debug` for the debuggable build, so a
  test run never touches a real library: `~/Library/Application Support/` on macOS, `%APPDATA%` on Windows,
  `$XDG_DATA_HOME` (or `~/.local/share`) on Linux.

Rules for every table:

- **IDs and timestamps come only from `IdFactory` and `UpdatedAtClock`.** Every row's `id` is a UUIDv7 from the
  factory, stored as a 16-byte BLOB. Every write sets `updatedAt` to `UpdatedAtClock.updatedAt(previous)`, the larger
  of now and the previous value plus one, so it only grows when the clock steps back. A write that also stores a
  time (`deletedAt`) reads `now()` once and passes it to `updatedAt(previous, now)`. Tests pass their own `Clock` or
  `IdFactory`. Nothing orders rows by `id`.
- **Every user action that writes is one transaction** (`useWriterConnection { it.immediateTransaction { … } }`), so
  a crash leaves the state before or after it.
- **Deletes are final and leave tombstones.** In the delete's transaction, call `Tombstones.record` once per removed
  row, join rows included: the `EntityType` and the row's key (its `id`, or a join row's two ids in column order).
  Each table keeps one constant for its name, used by both `@Entity(tableName = …)` and its `EntityType`. Deleting
  a key again updates its one tombstone. `record` runs in its own transaction, which joins the delete's as a
  savepoint.
- **A feature's entities and DAOs stay in its package** (`dev.marzahn.gloriana.logic.<feature>`); other code sees its
  repositories and domain types. Only the `@Database` class in `logic.storage` lists them, and each feature binds its
  own DAOs in its own Koin module. `FeatureBoundaryTest` (Konsist) enforces it.
- **Every schema change is a migration with its test.** Bump `DATABASE_VERSION`, keep the exported schema in
  `sharedLogic/schemas`, add the migration to `migrations`, and test it with `MigrationHarness`: `createAt` the
  previous version and write rows, then `migrateToLatest` and read them back through the DAOs. Never
  `fallbackToDestructiveMigration`: without a migration Room refuses to open the database, and the file stays as it
  was (`GlorianaDatabaseTest` checks this for a file from a newer version).

## Strings

Every string the UI shows is a Compose resource in `sharedUI/src/commonMain/composeResources`, with plurals as plural
resources and arguments as placeholders, never joined from pieces, so the app can be translated without code changes.
The one Android resource string, the launcher label in `androidApp/src/main/res/values/strings.xml`, has to be an
Android resource; `LauncherLabelTest` keeps it equal to the Compose `app_name`.

## Logs, keys and chats

No API key, custom header value or message text is ever logged, put in an error message, or sent anywhere but the
host the user set up for it. A log line can't be taken back once it is in a crash report or a shared bug report.

## Versions

Exact versions live only in `gradle/libs.versions.toml`; build files refer to the catalog. Each library is on its
newest stable release, with these exceptions:

- Kotlin is on 2.4.20, the newest release the Koin compiler plugin supports: on 2.4.21 the plugin warns, and warnings
  are errors.
- detekt is on its 2.0 pre-release, the first that reads current Kotlin, with the Compose rules release built for it.
- Compose Multiplatform's Material 3 ships only as pre-releases for each Compose Multiplatform minor; it is on the
  newest one for the Compose Multiplatform version in use.
- `androidx-sqlite` stays on the line Room's release is built against.

Gradle's own heap is 4 GB (`gradle.properties`): the Android and multiplatform compilations run out of memory at
Gradle's 512 MB default.

## Checks

```sh
./gradlew check
```

It compiles every module for every target and runs every unit test (`build-logic`'s included), Android lint on every
Android module, and detekt on every module. Warnings are errors in the Kotlin compiler, lint and detekt.

- detekt runs its default rules plus the Compose rules, from `config/detekt/detekt.yml`. That file lists only where the
  project differs: `@Composable` functions are named in PascalCase, and Material 2 is refused, since the app is
  Material 3 only.
- detekt also runs the project's own rules from `detektRules`, switched on in that module's bundled
  `config/config.yml`. `HardcodedUiText` fails on a string literal passed as UI text: `Text`, `BasicText`, the
  `contentDescription` of `Icon`, `Image` or any named argument or assignment, a desktop `Notification`, and an Android
  notification's title, text, ticker, big text, summary or action title. Test sources, `sharedLogic` and `stubHost` are
  out of its scope.
- Lint ignores the checks that compare dependencies with the newest releases (`lint.xml`): a release elsewhere must
  not fail a build that passed. The app also skips `ChromeOsAbiSupport`, which can't read the ABI list from
  `Abi` (the list holds `x86_64`), and `MissingApplicationIcon` until the app has its icon.

Locally, run the narrowest tasks that cover your change first, for example `./gradlew :sharedUI:jvmTest` or
`./gradlew :desktopApp:detekt`, and `./gradlew check` before you push.

## CI

CI runs on GitHub Actions in two workflows, in `.github/workflows`, set up by the shared `.github/actions/setup`.

- `build`, on every pull request and every push to `main`, runs the check, then builds the Android app and the desktop
  app for Linux. Making it required for a merge is a ruleset on `main`, added once the check has run on GitHub.
- `device`, nightly, on every tag and by hand, boots Android emulators (API 26 and 36, x86_64) and runs
  `./gradlew connectedCheck androidConnectedCheck`: the app's instrumented tests and the libraries' device tests. It
  fails if the app's tests did not run, and keeps the results as an artifact.

Every third-party action is pinned to a full commit SHA; Dependabot proposes the updates.
