# Estática — Game Design Document

> Los números marcados como **[placeholder]** son valores iniciales sin testear, generados como punto de partida. Van en `balance.json` y se ajustan con playtesting.

## 1. Concepto

Post-apocalipsis, años después de "el Silencio". El jugador es un operador de radio encerrado en un búnker. Nunca sale. Su único contacto con el exterior son las voces de los exploradores que guía, las señales que capta en el dial y transmisiones de origen desconocido.

**Pilares**
1. **Información incompleta:** el jugador decide con lo que escucha, no con lo que ve.
2. **Apego:** los exploradores tienen nombre, personalidad, stats propios, historia y muerte permanente. El jugador los forma, convive con ellos fuera de las misiones y **perder a un favorito tiene que doler de verdad** (ver §5.5).
3. **Siempre hay algo para hacer:** mientras las expediciones corren, el búnker y el dial tienen trabajo activo.
4. **Sesiones cortas:** todo loop activo se resuelve en 1–5 minutos.

## 2. Estética

- Terminal CRT: texto ámbar (`#FFB000`) sobre negro, monoespaciada, scanlines sutiles (overlay estático, no animado).
- Texto entrante con efecto máquina de escribir; las transmisiones con mala señal muestran cortes: `—no hay salid— ...`.
- Sonido: estática, clicks del dial, tecleo. Háptica corta al llegar mensajes urgentes.
- Cada explorador tiene un "estilo de voz" en el texto (uno escribe en minúsculas, otro usa jerga, otro es formal).
- Sin ilustraciones en el MVP. El único elemento gráfico es el **mapa de grilla** (ASCII/monoespaciado).

## 3. Loop principal

```
Barrer el dial → descubrir zonas / supervivientes / transmisiones
       ↓
Equipar exploradores en el taller → despachar a una zona
       ↓
Expedición en tiempo real (minutos/horas) con "situaciones" que piden decisión
       ↓  (mientras tanto: descifrar, gestionar energía, craftear, barrer)
Regreso con botín, heridas, estrés, lore
       ↓
Mejorar el búnker → más alcance de radio → zonas nuevas
```

## 4. Expediciones (núcleo idle)

### 4.1 Timeline
Al despachar, se genera con seed una lista de **segmentos** con timestamp:
- **Tránsito:** se resuelve solo; produce líneas de reporte ("crucé el puente, sin novedad").
- **Hallazgo:** botín automático según zona y rasgos.
- **Situación:** evento que **pausa al explorador** y pide decisión del operador (2–4 opciones).

### 4.2 Situaciones
- Llegan como notificación narrativa ("Mara: escucho algo en el sótano").
- Cada opción tiene una tirada contra stats + rasgos + intel. Las probabilidades **no se muestran exactas**; el explorador las comunica en su voz ("esto me da mala espina").
- **Timeout [placeholder: 15 min]:** si el operador no responde, el explorador decide solo según rasgos y confianza. Ausentarse no castiga duro, pero responder rinde más.
- Eventos definidos en JSON: condiciones (zona, rasgos, flags de historia), opciones, resultados, textos.

### 4.3 Catch-up
Al abrir la app, todos los segmentos vencidos se resuelven en orden y se vuelcan al log. Las situaciones vencidas sin respuesta se resuelven por timeout. No se simula nada en segundo plano.

### 4.4 Bandas y distancia
Frecuencia = distancia. Cuanto más lejos llega la radio, más largas las expediciones y más fuerte lo que hay afuera.

| Banda | Duración de expedición | Desbloqueo | Qué hay |
|---|---|---|---|
| Corta | < 5 min | inicio | botín básico, supervivientes comunes |
| Media | 15–60 min | Antena nv 2 | supervivientes con mejores techos de stats |
| Larga | 2–6 h | Antena nv 4 | supervivientes fuertes, rasgos raros, zonas con lore |
| Profunda | 8 h+ | endgame | los más fuertes, la fuente de La Voz |

Todos los valores son **[placeholder]**.

- **El arranque es todo Banda Corta:** la primera sesión tiene ritmo rápido y engancha antes de introducir esperas largas.
- **Más lejos = más fuertes:** supervivientes con stats base y techos más altos y rasgos raros. Reclutarlos requiere una expedición de rescate, y llegan con Confianza baja (no conocen al operador).
- **La señal se degrada con la distancia** (refuerza el pilar 1): reportes con cortes, situaciones con menos información y **retraso en las órdenes** (en Banda Larga, la respuesta tarda minutos en llegar al explorador).
- **Repetidoras:** se craftean en el taller y un explorador las instala en una zona. Mejoran la señal y extienden el alcance; pueden dañarse y requieren mantenimiento.

## 5. Exploradores

### 5.1 Stats individuales

| Stat | Uso |
|---|---|
| Aguante | combate, carga, resistir heridas |
| Sigilo | evitar encuentros, robar |
| Ingenio | reparar, abrir cerraduras, interpretar señales |
| Nervio | resistencia al estrés |
| Oficio | rendimiento en tareas del búnker (§5.3) |

- Cada explorador tiene **valores base y techo propios** (uno puede tener techo alto de Sigilo y bajo de Aguante). Dos exploradores nunca son intercambiables.
- **Crecimiento por uso:** los stats suben con lo que el explorador hace (expediciones sigilosas → Sigilo; turnos en el taller → Oficio). Cómo lo usa el jugador define en qué se convierte.
- **Rasgos** (1–2 visibles + 1 oculto que se revela con vínculo alto): p. ej. *Imprudente* (+botín, +riesgo), *Desconfiado* (desobedece si confianza baja), *Corredor* (expediciones más cortas).
- **Estrés:** sube con situaciones malas; alto estrés → errores, desobediencia, abandono.
- **Confianza (en las órdenes):** sube cuando las órdenes del operador salen bien, baja cuando salen mal. Con confianza alta aceptan órdenes riesgosas; con baja, pueden ignorarlas.
- **Heridas:** reducen stats hasta curarse en la enfermería.
- Reclutamiento: encontrando supervivientes con el dial o en expediciones (ver §4.4: más lejos = más fuertes).

### 5.2 Vínculo (relación personal)
Separado de la Confianza: la Confianza es profesional, el **Vínculo** es personal. Sube conversando, con regalos y cuidándolos; baja si se los ignora o se los expone a riesgos innecesarios.

Niveles de Vínculo [placeholder: 5] desbloquean:
- Conversaciones más personales y su historia previa al Silencio.
- **Misión personal** (arco propio: buscar a un familiar, volver a su casa, saldar una deuda).
- El rasgo oculto.
- Cambios en su voz por radio (más cercana, bromas internas, apodos para el operador).

### 5.3 Vida en el búnker (fuera de misiones)
Los exploradores que no están afuera viven en el búnker y hacen cosas:

**Tareas asignables** (cada una usa stats y entrena algo):
| Tarea | Efecto |
|---|---|
| Taller | craftea más rápido / mejor calidad según Oficio |
| Enfermería | cuida heridos, baja estrés del grupo |
| Cocina | raciones rinden más, moral general |
| Guardia | defiende ante incursiones al búnker |
| Asistente de radio | barrido pasivo del dial mientras el jugador no está |
| Entrenamiento | dos exploradores entrenan juntos: el de más stat enseña (mentoría) y suben su relación |
| Descanso | recupera estrés más rápido |

**Interacciones directas:**
- **Hablar:** diálogos cortos con opciones. A veces ellos inician ("¿tenés un minuto?"). Lo que se elige se recuerda.
- **Regalos:** objetos encontrados en expediciones (una armónica, una foto, chocolate, un libro). Cada explorador tiene gustos y disgustos individuales que se **descubren conversando**, no se muestran. El regalo correcto sube mucho el Vínculo; el incorrecto puede bajarlo. Cada uno tiene además **un objeto favorito** (el que más quiere). Un regalo que les gusta baja su estrés y les da +1 a todo en la próxima salida; desde Vínculo 3 (o siempre, si es el favorito) **lo devuelven a su manera** unas horas después: el cocinero cocina para todos, la mecánica arregla el equipo más gastado, el músico toca, la maestra da una clase. Con el Vínculo al máximo, lo que lo subiría pasa a bajar el estrés, y hablar nunca queda en nada.
- **Guiarlos:** en ciertos hitos el explorador pide consejo ("¿aprendo a usar el rifle o a curar?"). La respuesta del jugador define su especialización (ramas de habilidades). El jugador es su mentor, no solo su jefe.

**Relaciones entre exploradores:** amistades, rivalidades y parejas que surgen de compartir tareas y expediciones. Afectan el rendimiento cuando van juntos y la reacción cuando uno muere.

### 5.4 Recuerdos (requisito técnico)
Cada explorador guarda un log de **recuerdos**: regalos recibidos, decisiones del operador que lo marcaron, conversaciones clave, compañeros cercanos, frases propias. Este log alimenta diálogos, reacciones y, sobre todo, su muerte (§5.5). Modelar como tabla en Room desde la Fase 1.

### 5.5 Que la muerte duela
La muerte es permanente y **no se puede revertir ni comprar** (decisión de monetización explícita: vender revivir la abarataría y empujaría a pagar).

**Inversión previa:** stats que crecieron por cómo el jugador los usó, especialización que él eligió, misión personal a medias, regalos que lleva encima.

**Reglas de justicia:**
- **Ninguna muerte sin aviso.** Antes de una situación letal tiene que haber al menos una señal previa (herida, "esto me da mala espina", estrés alto) donde el operador podría haber retirado al explorador. La muerte tiene que sentirse como consecuencia de una decisión del jugador, no como azar.
- Con Vínculo alto, el explorador puede pedir volver ("quiero volver a casa, por favor").

**El momento:**
- La última transmisión se arma con sus **recuerdos**: menciona el regalo que lleva encima, al compañero que quiere, algo que el operador le dijo. Se escribe en su voz, con cortes de señal. Después, estática.
- No hay pantalla de "game over": el canal queda abierto y en silencio.

**Después:**
- Su litera queda vacía; su tarea del búnker queda sin cubrir.
- Los demás reaccionan según su relación: duelo (estrés), culpa, rasgos nuevos (*Vengativo*, *Temeroso*), conversaciones sobre él días después.
- Entrada en el **Diario** con su historia completa y la última transmisión, re-escuchable.
- Sus objetos personales se pueden recuperar en una expedición a la zona donde murió.

## 6. Sistemas activos (lo que se hace mientras se espera)

### 6.1 Barrido de frecuencias
- Dial deslizable dividido en las bandas de §4.4; solo se pueden barrer las desbloqueadas. Las señales están ocultas en frecuencias que rotan cada [placeholder: 4 h].
- Señales de bandas lejanas: más débiles y difíciles de sintonizar, mejores hallazgos.
- Cercanía indicada por el texto: más ruido/glitch lejos, texto más limpio cerca. Háptica al sintonizar.
- Hallazgos: zonas nuevas, supervivientes reclutables, transmisiones cifradas, eventos raros, fragmentos de la trama principal.
- Costo: energía del búnker por barrido → se integra con 6.4.

### 6.2 Descifrado
Transmisiones captadas que hay que decodificar:
- Morse (con tabla de referencia desbloqueable).
- Sustitución simple / César.
- Fragmentos con palabras cortadas que se completan con contexto del lore.
Recompensa: **intel** → marca escondites en zonas, revela peligros (mejora las tiradas de situaciones), avanza la trama. Dificultad escala con la progresión. Puzzles generados por plantilla + contenido para que no se agoten.

### 6.3 Guía en vivo
Durante algunas situaciones, el operador traza la ruta del explorador en el **mapa de grilla** usando lo que describe ("hay un edificio caído al norte"). Rutas mejores = menos tiempo y menos riesgo.

### 6.4 Gestión de energía
- El generador produce N watts; cada módulo consume: radio, filtros de aire, taller, enfermería, antena.
- El jugador reparte energía activamente: más radio = mejor señal con los exploradores; más enfermería = curación más rápida.
- De noche (hora real del dispositivo) el consumo sube. Eventos de apagón obligan a reasignar.

### 6.5 Taller
- Crafteo con chatarra/componentes: equipo para exploradores, piezas de módulos.
- Tiempos cortos [placeholder: 1–10 min] para que sea activo, no otra espera larga.

### 6.6 Diario del operador
Colección de lore: transmisiones descifradas, últimas palabras, notas de zonas. Da objetivo de completitud.

## 7. Búnker y progresión

| Módulo | Mejora |
|---|---|
| Radio | calidad de señal, menos retraso en órdenes |
| Antena | bandas desbloqueadas (alcance, §4.4) |
| Generador | watts totales |
| Taller | recetas, slots de crafteo |
| Enfermería | velocidad de curación, estrés |
| Literas | exploradores simultáneos |

Recursos: Chatarra, Componentes, Raciones, Baterías, Medicinas.

Zonas con nivel de peligro, distancia (duración), tabla de botín y eventos propios. Desbloqueo por alcance de radio.

## 8. Trama (meta-objetivo)

Una señal desconocida, "La Voz", transmite secuencias de números. Descifrarla a lo largo del juego revela qué fue el Silencio y quién más sigue escuchando. Capítulos desbloqueados por progresión, fragmentos escondidos en el dial y en zonas lejanas.

## 9. Retención sana

- Objetivos diarios: 3 tareas cortas (descifrar 1, completar 2 expediciones, barrer 5 veces).
- Meta semanal con recompensa mayor.
- **Sin sistema de energía/stamina que bloquee jugar.**
- Notificaciones: solo situaciones y regresos; ajustables por el jugador. Nada de "¡te extrañamos!".

## 10. Monetización

Principio: un jugador gratis dedicado accede a todo el contenido jugable.

1. **Cosmética de terminal:** paletas (fósforo verde, azul, blanco), tipografías, sonidos de tecleo, efectos de CRT.
2. **Pase de apoyo** (pago único o suscripción): cosmética exclusiva + slot extra de crafteo + estadísticas detalladas del operador. Comodidad, no poder.
3. **Anuncios recompensados opcionales**, tope diario [placeholder: 3], bonus pequeño. Nunca intersticiales.
4. **Expansiones de historia** pagas; el juego base es completo.
5. Sin lootboxes.

Implementación: interfaz `MonetizationProvider` desde el inicio, implementación real recién en la Fase 5 (el SDK de anuncios suma peso al APK; evaluar impacto antes).

## 11. Roadmap MVP

Todo el MVP es **offline, sin backend**.

- **Fase 0 — Esqueleto:** proyecto, Room, carga de contenido JSON, `GameRandom`, `GameClock`, pantalla de terminal con log y efecto de tipeo.
- **Fase 1 — Expediciones:** exploradores con stats individuales, zonas de Banda Corta, timeline, situaciones, catch-up, notificaciones, log de recuerdos.
- **Fase 2 — Vida en el búnker:** tareas, conversaciones, regalos, Vínculo, guía/especialización, muerte completa (§5.5).
- **Fase 3 — Radio activa y distancia:** barrido de frecuencias, descifrado (Morse + César), Banda Media, retraso de señal, repetidoras.
- **Fase 4 — Búnker:** energía, módulos, taller, enfermería, progresión por bandas.
- **Fase 5 — Contenido y pulido:** zonas hasta Banda Larga, ~40 eventos, misiones personales, relaciones entre exploradores, Diario, capítulo 1 de La Voz, sonido y háptica.
- **Fase 6 — Monetización y lanzamiento:** cosmética, pase de apoyo, anuncios opcionales, Play Store.
- **Post-MVP:** Banda Profunda, guía en vivo en grilla, triangulación, multijugador (§12).

## 12. Multijugador sin servidor (desde 0.8)

Sin backend. Cada búnker tiene un **indicativo** (`LU4QXR`, derivado del id del operador) y lo que se manda viaja como **código**: texto en grupos de cinco (`7KQ2M X9FAD …`), QR o Bluetooth. El mundo de cada jugador sigue siendo único: no hay región compartida.

- **Mensajes:** en claro o cifrados (César, Morse y los esquemas cuya llave tenga el que manda). Llegan como transmisión a descifrar.
- **Envíos:** recursos y equipo; salen del depósito al empaquetar, así no se duplican. Llegan a la escotilla.
- **Errantes:** una foto de alguien de la tripulación (nombre, stats, rasgos visibles, un recuerdo). Espera unos días en el lugar más lejano al alcance del otro; el equipo que vuelve de ahí lo cruza y recibe palabras, un regalo o un dato según su mejor stat. Nadie se recluta.
- **Últimas palabras:** de los caídos; suenan una vez en el dial del otro y quedan en el Diario.
- **Emisiones del desarrollador:** códigos firmados (ECDSA) para eventos sin servidor; se generan con `tools/link_broadcast.sh`.
- **Cerca (Bluetooth):** dos teléfonos en la misma sala. Códigos sin tipear, **trueque** en vivo (los dos aceptan las mismas ofertas) y **expedición conjunta** (el otro búnker cubre la ruta: escondite y peligros marcados, y su explorador espera ahí).

Los códigos se pueden falsificar, así que todo es modesto y se recorta a los límites de `balance.json → link`; hay un tope diario de códigos de jugadores (las emisiones y lo que llega por Bluetooth no cuentan).

