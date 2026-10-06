# Versionado y registro de cambios

Estática maneja **tres versiones independientes**. Cada una cambia por motivos distintos y protege cosas distintas.

| Versión | Dónde vive | Qué protege | Cuándo sube |
|---|---|---|---|
| **App** | `version.properties` | Builds y Play Store | En cada release |
| **DB** | `EstaticaDatabase.version` + `app/schemas/N.json` | Las partidas guardadas de los jugadores | Con cada cambio de esquema de Room |
| **Contenido** | `assets/content/balance.json → contentVersion` | La compatibilidad de los saves con el contenido | Cuando cambian el formato o los IDs del contenido JSON |

## 1. Versión de la app (SemVer)

Formato `MAJOR.MINOR.PATCH`, con una sola fuente de verdad: `version.properties`. No se edita `versionName` ni `versionCode` en `build.gradle`.

**Antes de 1.0, la versión sigue al roadmap (GDD §11):**

| Versión | Hito |
|---|---|
| 0.1.x | Fase 0: Esqueleto |
| 0.2.x | Fase 1: Expediciones |
| 0.3.x | Fase 2: Vida en el búnker |
| 0.4.x | Fase 3: Radio activa y distancia |
| 0.5.x | Fase 4: Búnker |
| 0.6.x | Fase 5: Contenido y pulido |
| **1.0.0** | Fase 6: lanzamiento en Play Store |
| 1.x | Post-MVP (Banda Profunda, guía en grilla…) |
| 2.0.0 | Multijugador (requiere backend; cambio incompatible) |

- **MINOR** sube cuando se cierra una fase.
- **PATCH** sube con fixes, ajustes de balance o contenido nuevo dentro de una fase.
- Después de 1.0, **MAJOR** sube solo si se rompe la compatibilidad con los saves o se exige una migración manual.

**versionCode** se calcula en `app/build.gradle`:

```
versionCode = MAJOR * 10000 + MINOR * 100 + PATCH      // 0.1.0 → 100, 1.2.3 → 10203
```

Por eso MINOR y PATCH tienen que quedar en 99 o menos. El versionCode siempre crece, como exige Play Store.

Los builds debug llevan el sufijo `-debug` (p. ej. `0.1.0-debug`).

## 2. Versión de la base de datos (Room)

Las partidas no se pueden perder: la muerte de un explorador tiene que doler por el juego, no por un bug de migración.

Reglas:
1. Cualquier cambio en una `@Entity` (tabla, columna, índice) → subir `version` en `EstaticaDatabase`.
2. Escribir `MIGRATION_N_M` en `data/db/Migrations.java` y sumarla a `Migrations.ALL`.
3. **Prohibido** `fallbackToDestructiveMigration()`.
4. Commitear el JSON nuevo que Room genera en `app/schemas/com.estatica.data.db.EstaticaDatabase/N.json`. Nunca editar ni borrar los anteriores: son el historial para testear migraciones.
5. Agregar un test de migración cuando exista el primero (`MigrationTestHelper`, test instrumentado; `room-testing` ya está en el catálogo).
6. Anotar el cambio en el CHANGELOG con la línea `DB N → M`.

| DB | App | Cambio |
|---|---|---|
| 1 | 0.1.0 | Esquema inicial: `log_lines`, `game_state` |
| 2 | 0.2.0 | Exploradores, recuerdos, expediciones, recursos; `log_lines.scout_id`, `game_state.content_version` |
| 3 | 0.3.0 | Vínculo, tareas, flags, conversaciones, objetos personales, afinidad; `expeditions.retreat_offered` |
| 4 | 0.4.0 | Radio: zonas conocidas, señales captadas, transmisiones, datos, repetidoras, rescates, lore, módulos; misiones en `expeditions` |
| 5 | 0.5.0 | Energía (`power`, `bunker_state`), taller (`craft_jobs`), `scouts.gear_id` |

`MigrationsTest` (JVM) compara el SQL de cada migración con los esquemas exportados. Al agregar una migración, extender ese test.

## 3. Versión del contenido

`contentVersion` en `balance.json`:

- **No sube** al cambiar números o textos: un ajuste de balance no rompe nada.
- **Sube** cuando cambia la estructura de un JSON, o cuando se renombra o borra un ID que un save puede referenciar (evento, ítem, rasgo, zona). En ese caso hay que agregar un mapeo de IDs viejos a nuevos o un fallback para los saves existentes.
- Desde la DB v2, `game_state.content_version` guarda la versión de contenido con la que se escribió el save por última vez. `GameSession.start()` la actualiza al cargar. Ahí es donde va a ir la adaptación de saves viejos: leer la versión guardada, mapear IDs y recién después actualizarla.
- Los saves referencian IDs de: exploradores (`template_id`), rasgos, zonas, eventos, recursos y voces. Renombrar o borrar cualquiera de ellos obliga a subir `contentVersion` y agregar un mapeo.

## 4. Changelog

Archivo: [`CHANGELOG.md`](../CHANGELOG.md), formato [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/).

- **Todo cambio** visible o estructural se anota en `## [Unreleased]`, en el mismo commit que lo introduce.
- Categorías: `Agregado`, `Cambiado`, `Obsoleto`, `Eliminado`, `Corregido`, `Seguridad`, más `Balance` para ajustes de números del GDD.
- Si cambian la DB o el contenido, se indica en la entrada (`DB 1 → 2`, `Contenido 1 → 2`).
- Se escribe para alguien que no leyó el código: qué cambió y por qué importa, no qué archivo se tocó.

## 5. Git

- Rama principal: `main`, siempre compilable y con los tests en verde.
- Ramas de trabajo: `fase<N>/<tema>` (p. ej. `fase1/timeline`) o `fix/<tema>`.
- Mensajes de commit en inglés, en imperativo, con prefijo de tipo: `feat:`, `fix:`, `balance:`, `content:`, `refactor:`, `test:`, `docs:`, `build:`.
- Cada release lleva un tag anotado `vMAJOR.MINOR.PATCH`.

## 6. Checklist de release

1. `./gradlew test lint assembleRelease` en verde.
2. Probar en un dispositivo real: instalación limpia **y** actualización sobre la versión anterior (para verificar la migración del save).
3. Subir la versión en `version.properties`.
4. En `CHANGELOG.md`, renombrar `[Unreleased]` a `[X.Y.Z] — AAAA-MM-DD` y abrir un `[Unreleased]` vacío.
5. Verificar que estén commiteados los esquemas nuevos de `app/schemas/`.
6. Commit `release: vX.Y.Z` y `git tag -a vX.Y.Z -m "..."`.
7. Revisar el tamaño del APK (objetivo < 15 MB) y anotarlo en la entrada si cambió bastante.
