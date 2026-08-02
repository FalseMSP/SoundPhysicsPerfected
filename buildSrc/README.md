# buildSrc: modbuild

Shared build logic for the Stonecutter-multiplied, Architectury Loom mod build.

```
buildSrc/
  build.gradle.kts                          <- portable: kotlin-dsl + Shadow, nothing project-specific
  src/main/kotlin/
    modbuild/                               <- portable: copy this whole directory into another project
      ModBuildExtension.kt                     as-is (plus the two lines below) to get embed() there too
      ModBuildProject.kt
    SppDependencyVersions.kt                <- NOT portable: this mod's own dependency versions
    modbuild.fabric-conventions.gradle.kts  <- NOT portable: this mod's own per-loader dependency lists
    modbuild.neoforge-conventions.gradle.kts   (the *pattern* is reusable, the *content* isn't)
    modbuild.forge-conventions.gradle.kts
```

**To reuse `embed(...)` in another Stonecutter+Loom mod:** copy `buildSrc/build.gradle.kts` and
`buildSrc/src/main/kotlin/modbuild/` verbatim into the other project's buildSrc, then in its root
script add `val modbuild = extensions.create("modbuild", ModBuildExtension::class, project,
LoaderData(loom.platform.get().name.lowercase()))`. That's the entire portable surface — everything
else in this directory is Sound Physics Perfected's own build configuration, kept in buildSrc only
because the convention plugins (also buildSrc-compiled) need to reference it.

## What's here

- `modbuild/ModBuildProject.kt` — plain data classes (`ModData`, `McData`, `LoaderData`) reading
  generic project properties. Construct once per project (`ModData(project)` etc.) instead of
  redeclaring the same `project.property(...)` lookups everywhere.
- `modbuild/ModBuildExtension.kt` — the `modbuild { }` extension, exposing `embed(...)`.
- `SppDependencyVersions.kt` — this mod's own third-party dependency versions. Deliberately outside
  the `modbuild/` directory and package (see above).
- `modbuild.fabric-conventions.gradle.kts` / `modbuild.neoforge-conventions.gradle.kts` /
  `modbuild.forge-conventions.gradle.kts` — one precompiled plugin per loader, holding that
  loader's own dependency set. Applied conditionally from the root script (see "Adding a
  dependency" below). Also outside `modbuild/` for the same reason.

## Adding a dependency — which mechanism to use

| Situation | Where it goes |
|---|---|
| Needed on every loader | Root `build.gradle.kts`'s `dependencies {}` block |
| Needed on one loader only | That loader's `modbuild.<loader>-conventions.gradle.kts` |
| Needed on one loader **and** one MC version | Same convention plugin, gated: `if (mc.version == "1.21.1") { ... }` |
| A real mod dependency already loaded by the platform at runtime (fabric-api, voicechat-api, ...) | Plain `"modImplementation"(...)` — no `embed()` needed |
| A plain jar that must be **both shipped in the mod jar and visible on the dev-time classpath** | `modbuild.embed(name, notation)` — see below |
| A subproject | `modbuild.embed(project(":name"))` |
| A dual-configuration dependency (e.g. something needed as both `annotationProcessor` and `implementation`) | Manual `include(implementation(annotationProcessor(...)))` — doesn't fit `embed()`'s single-notation model |

## `embed(...)` — what it's for and how it works

Loom's `include()` alone is enough for a subproject or plain jar to end up in the *shipped* jar.
It is **not** enough to make that same code visible while running `runClient` on NeoForge/Forge:
their dev-time FML module classloader only sees what's registered in `MOD_CLASSES`, which
`include()` never touches. `embed(...)` handles both concerns in one call, per situation:

```kotlin
// subproject
modbuild.embed(project(":your-subproject"))

// plain jar(s), no relocation
modbuild.embed("quiltParsers", "org.quiltmc.parsers:json:0.2.1", "org.quiltmc.parsers:gson:0.2.1")

// plain jar, relocated to avoid colliding with some other mod's own unrelocated copy
modbuild.embed("fastutil", "it.unimi.dsi:fastutil:8.5.16") {
    relocate("it.unimi.dsi.fastutil", "your.package.shadow.fastutil")
}
```

All three resolve non-transitively by default — a transitive dependency silently duplicating an
already-present module (e.g. a second, unrelocated copy of Gson) is exactly the kind of crash this
exists to prevent. If a dependency genuinely needs a transitive extra, list it as another
`notation` in the same call rather than letting resolution pull it in implicitly.

On Fabric, the dev-visibility half of `embed(...)` is a no-op — Fabric's dev classpath isn't
module-partitioned the way NeoForge/Forge's FML classloader is, so nothing extra is needed there.

### Relocation

The third form applies [Shadow](https://gradleup.com/shadow/) to relocate the dependency's
packages before it ships, so it can't collide with some other installed mod's own unrelocated copy
of the same library at runtime. Relocated classes are used for *both* the shipped jar and dev-time
visibility — source that imports the relocated package path has to, once relocation is configured,
so using the same relocated jar in both places keeps them consistent instead of only relocating at
ship time.

## Two things that will look surprising if you're extending this

**Loom API access is reflection-based, not typed.** buildSrc cannot hold any static reference to
Loom's own classes — a coordinate dependency, or even extracting just Loom's classes into a local
jar, collides with the root project's real application of the same plugin (Gradle either refuses
to apply Loom a second time, or the real Loom plugin's own class loading breaks trying to resolve
its own transitive deps through the now-ambiguous classpath). `embed(subproject)` calls
`loom.mods.maybeCreate(...).sourceSet(...)` via plain reflection instead. If you need more of
Loom's API from buildSrc, expect to hit the same wall — reflection, or have the root script (which
has real, live access) pass the value in, are the two ways out.

**The convention plugins use `"configName"(notation)`, not `configName(notation)`.** kotlin-dsl's
accessor generation for precompiled script plugins only kicks in for a plugin applied within that
same script's own `plugins {}` block — these convention plugins are applied to a project that
*already* has Loom (from the root script), so bare identifiers like `modImplementation(...)` don't
resolve. The generic string-invoke form is Gradle's own core DSL sugar and needs no such wiring.

## Known gaps, on purpose

`embed()` doesn't cover dual-configuration dependencies (see the table above) or platform-specific
mod dependencies resolved via `modApi(...)`/version ranges (e.g. a Sable-companion-style
dependency) — both stay as direct, manual Loom calls in the relevant convention plugin.
