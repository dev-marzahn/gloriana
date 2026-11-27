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
  `navigation/BackStack.kt`, so the back stack survives a configuration change and process death. `theme` holds the
  look, `components` the shared composables every screen is built from, and `navigation` the app shell.
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
resolves. `sharedUI` applies the Koin compiler plugin and uses its DSL (`viewModel<LibraryViewModel>()`), which wires
constructors at compile time; `sharedLogic` and the apps don't apply it and call constructors in `single { … }`
lambdas, which the `verify()` tests read. The plugin's own graph check is off: it cannot yet follow modules declared in
other Gradle modules, so the `verify()` tests are the graph check. A feature whose implementation is shared JVM code
(`jvmShared`) exposes its Koin module to common code through one `internal expect val` (`cardFilesModule`,
`vaultModule`, `debugModule`).

Debuggable-build hooks live in `logic.debug`: `DebugHooks` reads `debug/hooks.properties` (nothing in a release build)
and is the one binding of `Faults` and `DebugSwitches`. A write with fault points takes `Faults` and passes
`FaultPoint`s; a feature's switches are a `DebugSwitch` enum; both keys name their feature
(`fault.characters.DeleteRowRemoved=kill`, `fault.keys.NewKeysetStaged=fail`, `characters.import.hold=on`,
`assets.purge.now=on`, `keys.passphrase=on`). Before the feature prefixes, the keys were `fault.<Point>`, `import.hold`
and `purge.now`; a hooks file with those does nothing now.

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

Files a row points to (avatars, a Character Card's assets) are in `assets/<first two hex digits>/<SHA-256>` beside
`library.db`. `AssetStore` writes a file to `assets/staging` while hashing it, syncs it, and renames it into place only
after the import has read the whole file, so a stored file is never partial and is never changed; rows hold the hash.

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

## Look

- **Tokens only.** No screen names a colour, font, radius, spacing, shadow or duration: it reads them through
  `GlorianaTheme` (`colors`, `fonts`, `shapes`, `shadows`, `type`, `spacing`, `motion`, `avatarGradients`, `chat`).
  `LookTokens` holds a light and a dark `LookSet` with all of them, Material's container and fixed roles included, so a
  theme can replace the whole look at run time. Material You's dynamic colour is not used.
- **Wrappers only.** Screens use the `components` wrappers (`GlorianaButton`, `GlorianaIconButton`, `GlorianaSwitch`,
  `ListRow`, `SearchField`, `GlorianaSheet` and the rest), never the Material component directly: the wrappers give
  each one its token colours, its hover and pressed fills and its keyboard focus ring (`GlorianaFocusRing`), and keep
  its shadow and ring on the drawn shape, inside the touch target. Material's ripple is off; a plain clickable takes
  `GlorianaIndication(shape)` for its own shape. A disabled control draws with `Color.disabled()`.
- **Width classes.** `WidthClass` (compact under 600 dp, medium to 839 dp, expanded from 840 dp) comes from the
  window's width and carries the policies that depend on it: the `Navigation` (bar, labelled rail, or the workbench of
  rail, top bar, list pane and detail pane), the `SheetStyle`, and `pointerDense` (expanded's denser type and controls,
  with no 48 dp touch-target padding).
- **Top-level screens** start with `TopLevelScreen`. Below expanded it hands the title row to the list as its first
  item, so it scrolls with the list; on the workbench it draws the top bar, the list pane and the detail pane, which
  shows the pushed screen or, with nothing pushed, the Nothing picked block. Its `TopLevelContent` is either `Empty`
  (one `EmptyBlock`, no Create) or `Items` (the list, its Nothing picked block and an optional `createRoute`, which
  Create pushes). Screens navigate through `LocalShellNavigator`; each `Destination` keeps its own back stack there.
- **Back and Escape.** A pushed screen pops on system Back, on Escape (desktop) and with the predictive gesture. System
  Back on a destination's root goes to Chats; Escape on a root does nothing.
- **Fonts and icons** are bundled resources: Literata and Inter as static TTF files in `composeResources/font`, and
  Material Symbols Outlined (weight 400, grade 0) as vector drawables in `composeResources/drawable`, copied one by
  one as screens need them. Licence texts live in `composeResources/files/licenses` and `THIRD_PARTY_NOTICES.md`.

## Logs, keys and chats

No API key, custom header value or message text is ever logged, put in an error message, or sent anywhere but the
host the user set up for it. A log line can't be taken back once it is in a crash report or a shared bug report.
The `checkNoHttpLogging` task (part of `check`, from `gloriana.no-http-logging`) refuses Ktor's and OkHttp's HTTP loggers
on each app's shipped runtime classpath, `NoHttpLoggingTest` refuses their imports in any source, and `KeysAtRestTest`
searches every file the app writes, its standard output and its `java.util.logging` output for marker keys and header
values.

Keys and custom header values go through `KeyVault` (`logic.keys`) and nothing else:

- `seal` encrypts a value for a `SecretSlot` (a Connection's key, or one header of it by `HeaderName`) and `open`
  decrypts it, only when a request is built. The Connection's id, and the header's name in lower case, are bound into
  the ciphertext, so renaming a header means sealing its value again. Store the `SealedSecret` bytes; never keep the
  opened value in state. The vault clears every passphrase array it is given.
- Each Connection has its own data key in `keys/connections.json`, made on its first `seal`. Call `erase` right after a
  Connection's delete commits, and after a failed save when the Connection's row, read again, holds no key or header
  value (a new Connection, or one that had none); never erase a data key that stored values still need. A row this
  version can't read counts as needing its key. Saves and deletes run one at a time inside `DataKeyErasure.oneAtATime`,
  the only place the erase can be called, so no other save commits between that read and the erase.
- At start (`DataKeysSweptAtStart`) the vault removes every data key whose Connection `StoredConnections` (bound in
  `connectionsModule`) doesn't list, so a crash between a delete and its `erase` still erases the key. If the ids or
  the file can't be read or written, the sweep fails nothing and runs again at the vault's next call, until the first
  `seal` of the run, after which it waits for the next start. Neither `erase` nor the sweep opens the keyset; `erase`
  answers `KeyFileResult.Unavailable` when the file can't be written. `open` answers `Missing` for a value whose data
  key is gone or can't be read, `Locked` until a passphrase is set or entered, and `Unavailable` otherwise.
- One app instance per library. A second desktop instance on the same library can sweep the data key of a Connection
  the first is still saving, so that value reads as missing.
- The vault owns the keyset file and opens it only when a key is needed (`prepare` opens it at once). The file's name
  records what wraps it (`keys/keyset-device.json` or `keys/keyset-passphrase.json`), so even a damaged file keeps its
  protection. The platform's `DeviceKeyStore` holds named keys: an AES-GCM key in the Android Keystore, or a secret in
  the OS keychain through java-keyring on desktop; the device keyset's header names its key. A lookup answers `Found`,
  `Lost` (confirmed gone or invalidated: only then is a new key and keyset made), `Unconfirmed` (the Secret Service
  reports D-Bus errors as not found; the state is `DeviceKeyNotFound`, retried at each use), `Unusable` (an entry that
  isn't a key), `Unreachable` (retried at the next use) or `NoStore`. A key that is there but doesn't open the keyset,
  an unusable entry or a damaged device file give `DeviceDamaged`. In `DeviceDamaged` and `DeviceKeyNotFound` the user
  can start over with `replaceDeviceKeyset`; nothing replaces a key on its own. Every new keyset is written under a
  newly named key, and the old key is deleted only after the file is in place, so a replace cut short keeps the old
  key and file; data keys are erased first, as `reset` does. A desktop key is read back before a keyset is written
  under it.
- Which store wraps the keyset is recorded in the file name, so a device that starts on the passphrase stays on it. Where no
  store exists the state is `NoKeychain` and `setPassphrase` sets one (Argon2id, at most the shipped cost); after a
  restart it is `Locked` until `unlock`. `changePassphrase` wraps the same keyset under a new passphrase in one file
  replace, and `reset` replaces a forgotten or damaged (`PassphraseDamaged`) one and erases every data key. Each
  answers `NotAvailable` in a state it doesn't apply to, and after a write that fails the vault reads the file again.
  In the debuggable Android build, `keys.passphrase=on` skips the Keystore for a fresh library; turned on over a
  Keystore keyset it leaves the vault `Unavailable`, so clear the app's data first.
- Files the vault keeps are written with `writeAtomically` (`logic.storage`): a temporary file, synced, renamed over
  the old one, then the directory synced where the file system allows it; new directories come from
  `createDirectoriesDurably`. A `.part` file a kill leaves in `keys/` is removed the next time the keyset is opened.
- `desktopApp/proguard-rules.pro` keeps what Tink, JNA and the keychain backends reach by reflection. That isn't
  verifiable through shipped code until shipped code seals a key (the Connection screens). Until then, check a change
  to the rules or those libraries by adding a temporary `seal` call after Koin starts in `Main.kt`, running
  `./gradlew :desktopApp:proguardReleaseJars` and
  `java -Duser.home=<empty temp dir> -Dgloriana.debuggable=true -cp "desktopApp/build/compose/tmp/main-release/proguard/*"
  dev.marzahn.gloriana.desktop.MainKt` twice: the seal succeeds and the second launch opens
  `keys/keyset-device.json` unchanged. Remove the call and the `Gloriana Debug` keychain entries afterwards.

## Calling a model

`ModelApi` (`logic.connection.api`) is the one way to reach a model: it takes the prompt as role-tagged sections and a
`Connection`, opens the key and header values from `KeyVault` just for the request, and returns a flow of events (text,
reasoning, usage, then one finish or one failure). Every failure is a `ModelApiError`; nothing throws to the caller.

- Each API format is one `FormatAdapter`: its path, its own headers, its key header, the request body (the
  Connection's custom body JSON merged over it with `mergedWith`) and the readers for a stream and a whole reply. A
  `StreamReader` keeps its own `ReplyState` and decides the final events, also when a stream ends early (`ended`).
  Custom headers come after the adapter's and replace one of the same name. Whether a reply streams is read from the
  merged body's `stream`, which must be a JSON boolean.
- `Sampler`, `SamplerRange` and each `ApiFormat`'s `samplerRanges` (`logic.connection`) are the one sampler model:
  the save checks and `mergeSamplers` both use them. A format sends only its own samplers; the Connection's value wins,
  and a global value goes only where it is inside the format's range.
- `headerFaults` (`logic.connection`) is the one header rule: the save refuses what it finds, and `HostCalls` refuses
  it again before sending. The key itself must pass `isSendableHeaderValue`.
- `HostCalls` sends every request, streamed or whole, and returns every failure as a `ModelApiError` (`HostReply`,
  `HostEvent`); nothing it calls throws past it. It reads an event stream from the response body itself
  (`readEventData`), because Ktor's SSE plugin ends its flow quietly on a read error. The Connection's timeout is the
  connect and idle timeout, with no total timeout, and no redirect is followed. A request that isn't a chat reply (a
  model list) is built with `hostRequest` and sent through it too.
- Every host body that becomes an error (`KeyRejected`, `RateLimited`, `HostError`) goes through `Scrubber` where the
  error is made: `HostCalls` for a status, the `StreamReader` for an error in the stream, `ModelReplies` for an error
  in a 200 body. A successful reply is never scrubbed, since a short key would cut into its JSON. The scrubber is
  built from the secrets the request carries: every value sent under a credential header (each format's key header,
  `api-key`, `x-goog-api-key`), the Connection's key or a custom header that replaces it, and the token after an
  `Authorization` scheme, also JSON-escaped and percent-encoded. Other custom header values are not searched for.
- A stream stops being read at its format's last event (`StreamReader.over`), so nothing a host sends or fails after
  it reaches the caller.
- Plain `http` reaches private-network addresses only. The plain client never uses a proxy (`Proxy.NO_PROXY` and
  sockets made with it, since a bare `Socket` still asks the system for a SOCKS proxy), refuses an address literal
  that isn't private, and resolves names through `PrivateAddressesOnly`, which refuses a name unless every address
  is private; OkHttp connects only to the addresses it returns. Android's network security config permits
  cleartext and trusts system CAs only, so this check is the rule. Desktop sets `java.net.useSystemProxies` first
  thing in `main`, so `https` requests take the system proxy.
- Tests run the adapters against the stub host in-process (`startStubHost`), never a mock engine.

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
