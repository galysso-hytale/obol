# Obol

Hytale economy mod: four coin denominations (copper, silver, gold, mythril)
held as a single amount, and a public API so other mods can display, give,
take and transfer coins.

Work in progress — see [PLAN.md](PLAN.md) for the design and the roadmap.

| Module | Rôle |
|---|---|
| `api` | Public API, JDK-only. Published standalone as `dev.galysso.obol:obol-api`. |
| `core` | The server plugin. Bundles `api` into a single jar. |

```sh
./gradlew build       # compiles both modules, produces core/build/libs/Obol-<version>.jar
./gradlew runServer   # dev server in core/run/
```
