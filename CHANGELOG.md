# Changelog

Todos los cambios relevantes de Estática se registran acá.
Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/); versionado según [docs/VERSIONING.md](docs/VERSIONING.md).

Cada entrada indica, si cambian, las tres versiones independientes:
**App** (`version.properties`) · **DB** (esquema Room) · **Contenido** (`balance.json → contentVersion`).

## [Unreleased]

_Nada todavía._

## [0.3.0] — Fase 2: Vida en el búnker — en verificación

App 0.3.0 (versionCode 300) · **DB 2 → 3** · **Contenido 2 → 3**

Sin fecha de release: falta probarla en un dispositivo, incluida la actualización desde 0.2.0.

### Agregado
- **Vínculo** (GDD §5.2): relación personal con el operador, aparte de la confianza. Va de 0 a 100 en 5 niveles.
  - Sube conversando, con regalos y dejando volver a quien lo pide.
  - Baja si se ignora al explorador más de 48 h (−3 por día) o si se lo manda a una opción riesgosa que le daba mala espina.
  - Desde el nivel 3: se revela el rasgo oculto, la voz cambia ("hermano", Irene pasa de usted a vos) y, si está en peligro, puede pedir volver.
- **Conversaciones** (`dialogues.json`, 22 diálogos) que se juegan en el log, con las respuestas como botones:
  - Por explorador: su historia, sus gustos, una confesión que revela su rasgo oculto y un pedido de consejo.
  - Compartidas: charla de rutina, "¿tenés un minuto?" y conversaciones de duelo.
  - Lo que se elige queda como recuerdo. El operador puede iniciar una charla por explorador cada 4 h; las que inicia el explorador vencen a las 12 h.
- **Regalos:** 9 objetos personales (`items.json`) que aparecen en las expediciones según la zona.
  - Cada explorador tiene gustos y disgustos ocultos que se descubren conversando o regalando. Lo que gusta: +15 de vínculo; lo que disgusta: −6.
  - El explorador lleva encima los regalos que recibió.
- **Tareas del búnker** (`tasks.json`), calculadas por tiempo transcurrido:
  - Descanso: el estrés propio baja ×2,5.
  - Enfermería: los demás curan ×1,5 y se calma el grupo.
  - Cocina: baja el estrés del grupo.
  - Taller: 3 de chatarra → 1 componente cada 2 h.
  - Guardia y Asistente de radio: por ahora solo entrenan su stat; la radio además calma un poco al grupo.
  - Entrenamiento: el que sabe más enseña, ×1,5.
  - Todas entrenan un stat.
- **Guía y especialización:** cuando un explorador llega a cierto stat, pide consejo y la respuesta le da una especialización permanente. Las opciones son Sombra, Rastreo, Puntería, Mecánica y Primeros auxilios.
- **Afinidad entre exploradores** (mínima, oculta): sube al entrenar o compartir tarea. Decide quién sufre más una pérdida y a quién nombra un explorador al morir.
- **Muerte completa** (GDD §5.5):
  - Con vínculo alto y en peligro, el explorador pide volver y aparece la opción [Volvé al búnker].
  - La última transmisión menciona el regalo que lleva, al compañero más cercano y algo que el operador le dijo.
  - Después, los demás reciben estrés de duelo según su cercanía y a veces una cicatriz nueva (*Rencor* o *Miedo*). Uno o dos días más tarde hablan del muerto.
  - Su tarea queda sin cubrir y su ficha queda atenuada en la lista.
  - Sus objetos quedan en la zona y se recuperan volviendo a ella.
- **Diario:** una entrada por cada caído, con su historia, sus recuerdos y la última transmisión completa.
- **Interfaz:**
  - La ficha del explorador muestra vínculo (■■□□□), tarea, gustos descubiertos y regalos que lleva.
  - Botones [HABLAR] [REGALAR] [TAREA], que pasan a la línea de abajo en pantallas angostas.
  - Botón [DIARIO] en el panel de exploradores.
- Tests: 78 en total (23 nuevos): diálogos, tareas, regalos, duelo, pedido de volver, objetos encontrados, últimas palabras y regresión del estrés.

### Corregido
- **El estrés no bajaba con la app abierta.** Cada catch-up redondeaba la recuperación hacia abajo, y con catch-ups cada pocos segundos (expediciones activas) se perdía toda. Ahora la fracción se acumula (`stress_carry`), igual que la XP de las tareas.

### Cambiado
- `ZoneAdapter` fue reemplazado por un `PickerAdapter` genérico que se usa para zonas, regalos, tareas y el Diario.
- El panel de decisiones muestra tanto órdenes de situaciones como respuestas de conversaciones; las situaciones van primero.
- `Migrations` ahora es una lista de pasos (`STEPS`) y `MigrationsTest` verifica cada paso contra los esquemas exportados.
- Contenido v3: archivos nuevos `items.json`, `tasks.json` y `dialogues.json`. Se agregaron frases nuevas a las voces, rasgos de especialización y cicatriz, y gustos a los exploradores. `balance.json` suma las secciones `bond`, `grief` y `affinity`.

### DB 2 → 3
- `scouts`: columnas nuevas `bond`, `task_id`, `last_talk_at_millis`, `last_initiated_at_millis`, `last_decay_at_millis`, `task_xp_carry`, `task_output_carry`, `stress_carry`, `died_at_millis` y `died_zone_id`.
- `expeditions`: columna nueva `retreat_offered`.
- Tablas nuevas: `scout_flags`, `conversations`, `items`, `affinity` y `expedition_items`.
- En las partidas existentes, los exploradores arrancan con vínculo 0 y sin tarea.

### Notas
- Sin probar en dispositivo.
- APK release sin firmar: ~1,0 MB.

## [0.2.0] — Fase 1: Expediciones — en verificación

App 0.2.0 (versionCode 200) · **DB 1 → 2** · **Contenido 1 → 2**

Sin fecha de release: falta probarla en un dispositivo, incluida la actualización desde 0.1.0 (migración de DB).

### Agregado
- **Exploradores** (GDD §5.1): 3 iniciales (Mara, Tano, Irene) definidos en `scouts.json`. Cada uno tiene 5 stats con base y techo propios, rasgos visibles, un rasgo oculto que ya tiene efecto aunque no se vea, estrés, confianza y heridas.
- **Crecimiento por uso:** cada tirada da XP al stat que se usó y los stats suben hasta su techo.
- **Recuperación en el búnker** calculada por tiempo transcurrido, sin ticks: el estrés baja y las heridas curan un nivel cada 4 h.
- **Rasgos** (`traits.json`): Imprudencia, Desconfianza, Pies ligeros, Método, Miedo, Lealtad, Sangre fría. Modifican stats, tiradas, botín, duración, estrés, obediencia y cómo decide el explorador cuando está solo.
- **Estilos de voz** (`voices.json`): minúsculas, jerga y formal. Cada uno tiene su transformación de texto y sus bancos de frases.
- **4 zonas de Banda Corta** (`zones.json`) y **9 situaciones** (`events.json`), con 2–3 opciones cada una.
- **Expediciones** (GDD §4):
  - El timeline se genera completo al despachar, con su propia seed. Las duraciones son relativas: esperar una decisión corre todo lo que viene después.
  - Las probabilidades nunca se muestran: el explorador las dice en su voz, en tres niveles ("esto me da mala espina").
  - Si el operador no responde en 15 min, el explorador decide según sus rasgos.
  - Un explorador con poca confianza y un rasgo de desobediencia puede ignorar la orden.
  - Una herida crítica lo manda de vuelta al búnker.
  - El botín que trae se suma a las reservas del búnker al volver.
- **Catch-up** (GDD §4.3): al abrir la app se resuelve todo lo vencido, en orden, y se vuelca al log sin efecto de tipeo. Con la app abierta, un único timer despierta el juego en el próximo evento, sin polling.
- **Muerte simple** (anticipo de la Fase 2):
  - Es permanente.
  - La última transmisión sale con cortes de señal y menciona un recuerdo o la última orden. Después, "— señal perdida —".
  - Se guarda como recuerdo `LAST_WORDS` para el futuro Diario.
- **Recuerdos** (GDD §5.4): situaciones que marcaron al explorador, órdenes riesgosas o que salieron mal, y últimas palabras. Se conservan aunque el explorador muera.
- **Interfaz:**
  - Botón [ EXPLORADORES ] que abre el panel: ficha con stats en barras (■■■□□), estado, estrés, confianza, rasgos y bio.
  - Desde la ficha se despacha y se elige la zona.
  - Panel de decisión con las opciones como botones y la hora límite.
  - El comando `estado` ahora lista exploradores y reservas.
- **Notificaciones** (WorkManager 2.9.1):
  - Al pasar la app a segundo plano se agenda, por expedición, el próximo momento notable (situación o regreso). Ese momento se calcula del timeline guardado, sin simular nada.
  - El worker solo publica un texto ya armado en la voz del explorador. Al volver a la app se cancelan.
  - El permiso se pide en el primer despacho.
- `GameSession`: carga la partida una vez y aplica cada paso (catch-up, despacho, orden) en una sola transacción de Room.
- `ContentValidator`: chequea las referencias entre archivos de contenido (rasgos, voces, eventos, recursos, stats) y lista todos los problemas juntos.
- Tests: 55 en total. Incluyen:
  - Catch-up de una vez = catch-up paso a paso, en todas las zonas y con muchas seeds.
  - Ninguna muerte sin aviso.
  - Los exploradores que deciden solos nunca mueren.
  - Las notificaciones predicen exactamente cuándo pasa cada cosa.
  - La migración 1→2 coincide con los esquemas exportados por Room.

### Reglas de muerte (decisión de diseño)
- Para que un explorador muera hacen falta **aviso + orden del operador**.
- Aviso: herida grave o más, estrés ≥ 70, o que el explorador haya dicho que la opción le daba mala espina.
- Si el explorador decidió solo (timeout o desobediencia), puede herirse pero no morir (GDD §4.2: ausentarse no castiga duro).
- Un fallo letal sin aviso deja la herida que indique el resultado, y esa herida es el aviso para la próxima.

### Cambiado
- `LogLine` ahora tiene `scoutId` (opcional), para filtrar el log por explorador más adelante.
- Contenido v2: `terminal.json` suma `statNames`, `scoutStatus`, `injuries` y los textos de expedición. `balance.json` suma las secciones `scouts` y `checks`.

### DB 1 → 2
- Tablas nuevas: `scouts`, `scout_stats`, `scout_traits`, `memories`, `expeditions`, `expedition_segments`, `expedition_loot`, `resources`.
- Columnas nuevas: `log_lines.scout_id` y `game_state.content_version` (por defecto 1).
- Las partidas existentes reciben los 3 exploradores iniciales al cargar.

### Balance
Todo es **[placeholder]** y está en `balance.json`. Resultado de 1000 simulaciones por explorador y zona, sin intervención del operador:

| | Almacén | Estación | Farmacia | Escuela |
|---|---|---|---|---|
| Mara (heridos / críticos) | 13% / 0% | 0% / 0% | 28% / 2% | 35% / 4% |
| Tano | 41% / 0% | 34% / 0% | 61% / 8% | 68% / 15% |
| Irene | 0% / 0% | 8% / 0% | 20% / 0% | 22% / 0% |

Muertes sin intervención: 0%. Botín: 3–5 unidades por expedición. Tano sale lastimado seguido porque es imprudente, a propósito.

### Notas
- Sin probar en dispositivo: no había emulador ni teléfono disponible.
- La migración se verifica contra los esquemas exportados; un test instrumentado con `MigrationTestHelper` queda pendiente hasta tener dispositivo.
- APK release sin firmar: ~0,9 MB.

## [0.1.0] — Fase 0: Esqueleto — en verificación

App 0.1.0 (versionCode 100) · DB 1 · Contenido 1

Sin fecha de release todavía: falta probarla en un dispositivo o emulador. Cuando se apruebe, se le pone fecha y se crea el tag `v0.1.0`.

### Agregado
- Proyecto Android nativo: Java 17, Views + ViewBinding, minSdk 26, compile/targetSdk 35, Gradle 8.11.1, AGP 8.7.2.
- Catálogo de versiones de dependencias en `gradle/libs.versions.toml`: Room 2.6.1, Hilt 2.52, Lifecycle 2.8.7, Gson 2.11.0, RecyclerView, ConstraintLayout.
- Versión de la app centralizada en `version.properties`; el `versionCode` se calcula solo.
- `core/GameClock` (interfaz inyectable) y `SystemGameClock`.
- `core/GameRandom`: PRNG SplitMix64 determinista; todo su estado es un `long` que se persiste.
- `core/TextTemplate` (placeholders `{clave}`) y `core/Ids` (UUID).
- `domain/`: `LogLine`/`LogChannel`, modelos de contenido (`Balance`, `TerminalText`), `ElapsedFormatter`, `CommandInterpreter`, `GameState`.
- Room v1: tablas `log_lines` (seq local + UUID global) y `game_state` (fila única: operador, seed y estado del RNG, timestamps). El esquema se exporta a `app/schemas/`; las migraciones van en `data/db/Migrations`, sin fallback destructivo.
- Carga de contenido JSON (`assets/content/balance.json`, `terminal.json`): se parsea una vez, se valida y queda cacheada.
- `balance.json` con todos los **[placeholder]** del GDD: timeout de situaciones, bandas, niveles de vínculo, rotación de señales, tiempos de taller, tope de anuncios.
- Pantalla de terminal: log en `RecyclerView` (`ListAdapter` + `DiffUtil` + IDs estables), ventana de 200 líneas en memoria y paginado hacia atrás desde Room.
- Efecto máquina de escribir: un solo `Handler` y solo para la línea activa; tocar el log completa la línea. Al pasar a segundo plano, la línea se completa.
- Scanlines CRT estáticas (shader con bitmap repetido, sin animación), paleta ámbar `#FFB000`, ícono adaptativo.
- Secuencia de arranque en la primera partida; al volver, mensaje de reconexión con el tiempo de ausencia, elegido con el RNG persistido (base del catch-up).
- Comandos de terminal definidos en JSON: `ayuda` y `estado`.
- Ejecutor de juego de un solo hilo: toda la lógica y el acceso a Room corren ahí, en orden.
- Tests unitarios (23): `GameRandom`, `TextTemplate`, `ElapsedFormatter`, `CommandInterpreter`, validación de los JSON incluidos en la app.

### Cambiado
- `GDD.md` se movió a `docs/GDD.md`.

### Notas
- APK release sin firmar: ~0,7 MB (objetivo < 15 MB).
- Lint muestra versiones más nuevas de AGP, Room y Lifecycle. Se usan las que ya estaban en la caché local; actualizar es una decisión aparte.

[Unreleased]: #unreleased
[0.3.0]: #030--fase-2-vida-en-el-búnker--en-verificación
[0.2.0]: #020--fase-1-expediciones--en-verificación
[0.1.0]: #010--fase-0-esqueleto--en-verificación
