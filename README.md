This is a Kotlin Multiplatform project targeting Android, iOS.

* [/composeApp](./composeApp/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./composeApp/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./composeApp/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./composeApp/src/jvmMain/kotlin)
    folder is the appropriate location.

* [/iosApp](./iosApp/iosApp) contains iOS applications. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

### Build and Run Android Application

To build and run the development version of the Android app, use the run configuration from the run widget
in your IDE’s toolbar or build it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :composeApp:assembleDebug
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:assembleDebug
  ```

### Local setup after cloning

Copy [local.properties.example](local.properties.example) to `local.properties` if
you do not already have one. If IntelliJ has created it, add the missing entries
while keeping your existing SDK path and credentials. Set `sdk.dir`, `MAPS_API_KEY`
and `AUTH0_CLIENT_ID` for your environment; `DEV_TOKEN` may remain empty for normal
sign-in. `local.properties` is ignored by Git and must never be committed.

### Backend URL

Set the backend address in your own `local.properties`, without editing Kotlin:

```properties
BACKEND_BASE_URL=http://10.0.2.2:8080
```

| Target | Example URL |
| --- | --- |
| Android Emulator; backend on the host PC | `http://10.0.2.2:8080` |
| Physical phone on the same LAN | `http://192.168.1.100:8080` (replace with your PC's IP) |
| iOS Simulator; backend on the same Mac | `http://localhost:8080` |
| Shared remote backend | `https://api.example.com` (replace with the actual server) |

The address must start with `http://` or `https://`, without credentials, query
parameters or a fragment. A trailing slash is removed automatically.

Precedence is: nonblank Gradle property `BACKEND_BASE_URL`, nonblank
`BACKEND_BASE_URL` in `local.properties`, then `http://10.0.2.2:8080`.
For a one-off build, you can override it without changing the local file:

```powershell
.\gradlew.bat :composeApp:assembleDebug "-PBACKEND_BASE_URL=https://api.example.com"
```

Gradle generates a common Kotlin configuration consumed by `AppConfig`, so the
setting applies to both Android and iOS and to both DEV and PROD. It is embedded
in the application at build time. After changing it, rebuild and install/run the
app from IntelliJ; reopening an already installed APK does not update its URL.
Do not edit generated files under `build/`.

Start the backend at the selected address before running the app. For a physical
phone, allow the backend port through the host firewall and use the same network.

The initial `SplashScreen` requests `/`. An unauthenticated HTTP 401 response means
the backend is reachable; the app can continue to sign-in. A connection timeout
or refused connection triggers the generic “Algo salió mal” dialog.

### Tiempo estimado de atención en vivo

Mientras la entrada de la consulta permanece `EN_COLA`, la app abre una conexión SSE autenticada con `GET /api/atencion/tiempos/suscribirse/{consultaId}`. Recibe los eventos `tiempo-estimado` y `heartbeat`; al salir de la cola, el backend deja de enviar actualizaciones del tiempo estimado.

### Entrada directa al chat y regreso al inicio

Al confirmar la selección de hospital con éxito, la app abre directamente el chat.
La navegación conserva inicio debajo del chat y descarta las pantallas de selección
de especialidad, ubicación y hospital. Tanto la flecha del chat como Atrás de Android
permiten volver a inicio; desde «Chat interactivo» se retoma la misma conversación.
Este acceso usa un color propio para distinguirlo de «Solicitar atención de guardia».
Un fallo al seleccionar hospital no abre el chat ni descarta la conversación guardada.
La pantalla muestra el motivo informado por el backend.

### Restricción de inscripción remota

Inicio y la lista de hospitales consultan `GET /api/paciente/sancion` al abrirse
o volver a primer plano. Mientras la sanción esté vigente, actualizan el estado
cada 30 segundos en esas pantallas. También se consulta antes de confirmar un
hospital y se maneja el `403 PATIENT_SANCTIONED` del backend si el estado cambió
durante la selección. Los plazos se muestran en la fecha de Buenos Aires.

La sanción deshabilita la selección para inscribirse a distancia. El listado de
hospitales y sus tiempos, el perfil, los estudios y el chat de una consulta
activa siguen disponibles. El aviso explica el ingreso por recepción e incluye
la opción de llamar al 107 para urgencias. La app guarda los identificadores
de los avisos de aplicación y levantamiento que el paciente ya vio en el
dispositivo. Cuando el backend informa el levantamiento o vencimiento, la
selección se habilita sin volver a iniciar sesión.

Verificación manual del 5/11/2026 con la skill `android-mobile-validation`: Pixel 9 Pro XL
(Android 17), APK debug compilado contra `https://api.pretriage.com.ar`; compilación y
52 pruebas unitarias pasaron (0 errores, fallos u omitidas). Se observó la selección
exitosa del Hospital Alemán, especialidad Clínica médica, abriendo Chat directamente, el regreso a inicio
con la flecha y con Atrás de Android, el reingreso desde «Chat interactivo» conservando
el marcador sintético y el acento verde del acceso. Capturas: `C:/Users/valen/AppData/Local/Temp/pretriage-navigation-qa-b5a061efb7364b9dab5b86746bd40729`
(`01-home-before`, `04-chat-direct`, `07-home-chat-loaded`, `08-chat-reopened`,
`09-home-after-android-back`, `10-chat-after-system-back`).

Regresión manual: seleccionar especialidad, ubicación y hospital, confirmar y comprobar
que aparece el chat. Escribir una respuesta, volver a inicio con la flecha y reabrir
«Chat interactivo»: deben conservarse los mensajes. Repetir con Atrás de Android y
comprobar que no reaparece el selector ni se reabre el chat automáticamente. Tras
cancelar la consulta, inicio debe volver a ofrecer «Solicitar atención de guardia».

### Chat after cancelling a consultation

Mobile clears its saved chat ID after a successful cancellation
(`POST /api/paciente/consulta/cancelar`, with state `CANCELADA`) and after a
successful new hospital selection (`POST /api/atencion/hospital`, HTTP 204).
The next chat screen creates a new interview, including when the same hospital
and specialty are selected again. Failed requests preserve the saved chat ID;
leaving and reopening the chat within the same consultation still restores it.
The previous chat and its messages remain in the backend as history.

Manual regression: select a hospital, complete or partially answer the chat,
cancel the consultation, and select the same hospital/specialty again. Chat opens automatically.
It must show only the initial greeting and allow writing. Leave and reopen this
new chat to confirm that its messages are restored. Check that a rejected
cancellation or hospital selection does not discard the existing chat.

### Build and Run iOS Application

To build and run the development version of the iOS app, use the run configuration from the run widget
in your IDE’s toolbar or open the [/iosApp](./iosApp) directory in Xcode and run it from there.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…


### Mensajes del chat de voz

La app muestra el registro de respuestas y la información de atención, sin anunciar preclasificaciones ni niveles de prioridad al paciente. El backend comparte esa regla con Gemini para texto, audio y cierre. Al activar voz con el saludo inicial ya visible, el backend pide una transición breve («Te escucho») y no envía otra transcripción del saludo. Las preguntas posteriores y las respuestas del paciente siguen llegando normalmente. Los avisos de atención urgente se mantienen.


La entrevista terminada permanece cerrada mientras el backend procesa. `finalizado` indica que la entrevista cerró; `estadoClasificacion` indica por separado si la clasificación está `NO_SOLICITADA`, `PENDIENTE`, `FALLIDA`, `PARCIAL` o `COMPLETADA`. Una respuesta antigua que omita `estadoClasificacion` permanece como cierre desconocido, porque `finalizado` solo indica que la entrevista cerró y una estimación de cola puede existir antes de clasificar. `PENDIENTE` muestra un aviso breve mientras el backend realiza sus dos intentos automáticos; el cliente consulta el estado por GET en una ventana inicial de 200 segundos para cubrir las dos fases de unos 92 y 95 segundos. Después continúa con consultas más espaciadas mientras la app está en primer plano; `proximoReintentoEn` guía la espera de API, limitada a 30 segundos. Al volver al primer plano hace un GET inmediato. Nunca repite el POST del mensaje. El estado `NO_DISPONIBLE` de servidores antiguos no queda pegado en la caché ni bloquea el chat después de una lectura GET exitosa.

Los campos aditivos `cierreParcial`, `estadoRecuperacion` (`SIN_RECUPERACION`, `REINTENTANDO`, `ESPERANDO_API`), `proximoReintentoEn` y `versionEstado` acompañan los DTO del chat. El cliente aplica primero la versión remota más nueva y usa el ID de solicitud para impedir que una respuesta vieja libere un mensaje nuevo. `cierreParcial` por sí solo no termina la espera: si `estadoClasificacion` sigue en `PENDIENTE`, la app conserva el seguimiento hasta recibir un estado terminal. `PARCIAL` confirma que las respuestas quedaron guardadas, pero no se presenta como triage completado ni muestra una estimación de atención. En voz, `entrevista_finalizada` significa que se aceptó el cierre; `estado_chat` entrega snapshots pendientes o en recuperación y `procesamiento_finalizado` lleva el snapshot terminal parcial. `triage_finalizado` queda reservado para un resultado válido. El origen `CIERRE_SIN_DATOS` sigue siendo un cierre administrativo sin clasificación: el cliente mantiene `finalizado=true` y `estadoClasificacion=NO_SOLICITADA`, sin mostrar una tarjeta de atención. El cierre de una clasificación completada no anuncia prioridad ni promete una derivación ya realizada.


### Estado de espera y cierre de la entrevista por voz

El evento WebSocket procesando_respuesta lleva activo=true mientras el backend
interpreta una respuesta y activo=false cuando vuelve a esperar una respuesta
útil. La app mantiene visible «Procesando tu respuesta…» durante ese intervalo;
los fragmentos de transcripción, audio y turno_completo no lo limpian por sí
solos. El micrófono y la interrupción siguen disponibles. Al recibirse una
pregunta útil, la pantalla indica que está escuchando.

Cuando el backend acepta el cierre, la app muestra el procesamiento mientras la
sesión de voz sigue activa. Si el socket entrega `estado_chat`, la app aplica ese
snapshot con la misma reconciliación por versión e inicia el seguimiento GET del
turno pendiente al terminar la voz. Un resultado terminal parcial detiene el
audio y confirma que las respuestas quedaron guardadas sin presentarlas como
clasificación completa. Los errores de una entrevista abierta ofrecen retomar
voz o continuar por texto; una entrevista cerrada no ofrece volver a responder
ni reprocesar manualmente la clasificación.


### Interrupciones de voz y protección contra eco (Android)

La app intenta permitir que el paciente hable mientras responde el asistente. El
modo efectivo se obtiene del audio nativo y se actualiza al iniciar o cambiar la
salida: con una ruta de auriculares compatible o cancelación de eco habilitada,
la pantalla indica «Podés interrumpir hablando». Se conserva la detección de voz de
Gemini y, al recibir `interrumpido`, se corta inmediatamente la reproducción y se
vacían tanto la cola local como el buffer nativo. Las escrituras previas quedan
invalidadas para que no reaparezca audio del turno interrumpido.

La cancelación de eco nativa depende del teléfono y de su ruta de audio; que se
pueda habilitar no garantiza un resultado acústico perfecto. La selección de modo
es automática y no expone un control manual: el modo por turnos se activa cuando
faltan las condiciones mínimas para escuchar y reproducir a la vez. La pantalla
sigue indicando el modo efectivo.

En modo por turnos, se reemplaza por PCM silencioso la captura mientras hay audio
pendiente en la cola o en `AudioTrack`. La escucha vuelve cuando el contador real
`playbackHeadPosition` alcanza los frames encolados y pasan 300 ms de margen. Se
comprueba antes y después de leer cada fragmento para descartar lecturas que cruzan
ese límite. No se usa `turno_completo` para liberar el micrófono, porque puede
quedar audio por reproducir. No se envía `fin_audio` por cada fragmento silenciado.
La interfaz muestra el modo efectivo y conserva la opción de terminar la voz.
Si otra aplicación o llamada toma el foco de audio durante la captura, la sesión
se cierra y se informa el motivo; no queda mostrando «Te escucho» con el micrófono
apagado. El cierre indica «Terminando la conversación de voz...». No se cancela
el procesamiento de una entrevista ya terminada por un cambio de foco posterior.

Verificación automática de la política: `./gradlew.bat :composeApp:testDebugUnitTest
--tests com.proyecto_final.triage.audio.ControlEcoVozTest` (usar el procedimiento de
log temporal y `--stop` de AGENTS.md). No requiere backend ni base de datos.

Regresión física pendiente en un teléfono real:

- Con parlante, dejar terminar tres respuestas sin hablar: no deben aparecer
  transcripciones del bot como paciente ni interrupciones espontáneas.
- En modo que permite interrumpir, hablar durante una respuesta: debe detenerse
  el audio pendiente, transcribirse la voz del paciente y contestarse la nueva frase.
- En una ruta sin soporte de interrupciones, comprobar la selección automática
  del modo por turnos y su indicación en pantalla.
- Repetir con volumen alto y con auriculares; conectar/desconectar auriculares
  durante la sesión y verificar que el mensaje de la pantalla sigue el modo nativo.
- Detener y reabrir voz, salir de la pantalla y repetir: sin audio pendiente,
  micrófono retenido ni cambios persistentes de la ruta/modo de audio del sistema.


### Errores al enviar por texto

El envío de texto incluye un UUID de solicitud. Un HTTP 202 confirma que el backend
guardó el turno y continuará procesándolo; el cliente obtiene el historial canónico
del DTO y consulta su estado sin agregar una burbuja de respuesta propia. Ante un
resultado ambiguo, la app consulta por GET y nunca repite automáticamente el POST.
Un 401/403 requiere revisar la sesión, 404 indica un chat no encontrado y 429 pide
esperar. El cliente consulta rápido durante 200 segundos para cubrir las dos fases de
recuperación del backend, y después sigue con GETs espaciados sin mantener el indicador
de escritura. Si la API sigue sin responder, vuelve a consultar al regresar la app al
primer plano; nunca repite el POST. Un `NO_DISPONIBLE` legado sin estado de
recuperación no se conserva como bloqueo terminal después de un GET exitoso.

### Disposición de la UI y barras del sistema

El contenedor de `App` dibuja el fondo con `Surface` y reserva `safeDrawing`
antes de la navegación. Las pantallas quedan dentro del área disponible de las
barras de estado, navegación y teclado. Los modificadores de insets descendientes
consumen solo el espacio restante; no agregar márgenes fijos para compensar la
barra de Android. Se conserva `imePadding()` del chat. Esta composición sigue el
[manejo de insets de Compose](https://developer.android.com/develop/ui/compose/system/insets-ui).

Las tarjetas del perfil distribuyen el ancho entre etiqueta y valor con separación.
Con poco ancho o fuentes grandes pasan a etiqueta arriba y valor debajo, para que
un correo largo no comprima la etiqueta en una columna de letras. No se recortan
los datos para forzar una altura fija.

Los filtros de hospitales (distancia, tiempo de atención y mejor valorados)
comparten ancho y altura calculada a partir de su texto. La grilla adapta la
cantidad de columnas al ancho disponible y la escala de fuente; en pantallas
angostas se apilan conservando la altura común. El espacio del indicador de selección se
mantiene al cambiar de filtro para evitar saltos de tamaño.

Regresión manual: revisar perfil con correo largo y elección de hospital a 320 dp
y con fuente normal, 1.5 y 2 veces mayor; probar orientación horizontal. Con
navegación Android por tres botones y por gestos, recorrer inicio de sesión,
perfil, hospitales, consulta y chat, verificando que los últimos botones sean
visibles y pulsables. Abrir/cerrar el teclado del chat y edición del perfil para
comprobar que no se duplique el espacio inferior ni quede tapado el campo activo.


### Integración con desarrollo (25 de octubre de 2026)

La rama de chat de voz incorpora el cambio de contraseña autenticado, el orden
«Mejor valorados» y los ajustes de ubicación/carga de hospitales de `desarrollo`.
La URL del backend sigue configurándose mediante `BACKEND_BASE_URL`; una dirección
local de otro equipo no reemplaza esa configuración. El cambio de contraseña usa
`POST /api/auth/cambio-contrasenia/actual` y conserva los avisos de bloqueo y
validación del backend. El selector mantiene los tres órdenes con altura común.

Verificado el 25 de octubre de 2026: `:composeApp:assembleDebug` y
`:composeApp:testDebugUnitTest --tests com.proyecto_final.triage.audio.ControlEcoVozTest`
finalizaron correctamente; pasaron las nueve pruebas de política de audio. Se usó
un override local de `BACKEND_BASE_URL`, log temporal y detención del daemon.
La compilación comprueba Android; la regresión acústica física indicada arriba y
la compilación iOS requieren sus respectivos dispositivos/entornos.

### Reconciliación de mensajes del chat

`POST /api/chat/{id}/mensajes` envía un UUID `solicitudId`. El backend responde HTTP
202 con el DTO completo después de guardar el mensaje del paciente y aceptar el turno
para procesamiento. `GET /api/chat/{id}` devuelve el mismo estado: `estadoTurnoTexto`
(`SIN_SOLICITAR`, `PENDIENTE`, `COMPLETADA` o el legado `NO_DISPONIBLE`), el UUID
asociado, cantidad de intentos, `cierreParcial`, `estadoRecuperacion`
(`SIN_RECUPERACION`, `REINTENTANDO`, `ESPERANDO_API`), `proximoReintentoEn` UTC y
`versionEstado`. `COMPLETADA` resuelve el turno de mensaje y no implica una respuesta
BOT ni una clasificación completa. La solicitud UUID y el texto se conservan en la
copia de navegación en memoria hasta un estado terminal.

Después de HTTP 202, y también al volver a abrir un chat, la app restaura el progreso
desde GET. Consulta rápido durante 200 s para cubrir los dos intentos y las fases de
unos 92 y 95 s del backend, y luego sigue con GETs más espaciados en primer plano.
`proximoReintentoEn` guía la espera de API, limitada a 30 s. Al pausar la app se
suspenden las consultas; al volver, se hace un GET inmediato y se reanuda el seguimiento.
Un POST con respuesta perdida se reconcilia por GET usando el UUID; el cliente nunca
repite ese POST. Después de la ventana rápida no queda un indicador de escritura girando.
Mientras el turno pendiente esté guardado o la API no pueda confirmarlo, texto y voz
permanecen bloqueados para impedir mensajes duplicados.

`NO_DISPONIBLE` de un servidor antiguo ya no se trata como bloqueo terminal pegado:
si un GET exitoso no trae estado de recuperación activo, la app descarta la referencia
de solicitud vieja y permite continuar. Si la API real no responde, conserva el turno
UUID y reintenta consultas al volver a primer plano, sin reenviar el mensaje. La respuesta
HTTP 200 del backend anterior sigue aceptándose como una respuesta inmediata con su
burbuja BOT tradicional.

En el cierre de voz, `entrevista_finalizada` señala que se aceptó el cierre; la app
consulta automáticamente el snapshot del chat hasta el resultado terminal. El evento
`estado_chat` entrega un snapshot pendiente o en recuperación; la app lo aplica con la
misma reconciliación por versión e inicia el polling del turno si la voz terminó por
indisponibilidad de API. `procesamiento_finalizado` entrega el snapshot terminal parcial:
se detiene el audio, se muestran las respuestas guardadas sin presentar el triage como
completo ni dejar un spinner. `triage_finalizado` sigue reservado a un resultado válido,
aunque no incluya una estimación de atención. `PARCIAL` es terminal y no muestra una
tarjeta de atención. No hay controles manuales para reintentar o forzar el cierre.
Estas reglas también se aplican al reingresar.

Al salir se conserva en memoria la última conversación visible, incluidos los fragmentos
de transcripción abiertos y el estado de cierre. Al volver al mismo chat se restaura
antes del GET; una lectura antigua o pendiente con menos mensajes no recorta el historial
ni reabre una entrevista cerrada. Un resultado terminal usa el historial del servidor.
Esta copia no escribe transcripciones en disco y se limpia al cerrar sesión o limpiar
el chat. Si termina el proceso de la app, se depende del historial ya guardado por el
backend. Un fallo de red al recuperar no descarta el chat ni crea otro automáticamente.

El error recuperable de voz muestra «Se interrumpió la voz.» con «Retomar voz»;
la entrada escrita permanece disponible. Un resultado completado limpia el error y
tiene precedencia sobre fallos posteriores del socket.

Pruebas de reconciliación (Android unit tests):
`.\gradlew.bat :composeApp:testDebugUnitTest --tests com.proyecto_final.triage.ChatSendReconciliationTest`
La recuperación durable de turnos de texto se cubre con
`com.proyecto_final.triage.ChatTextTurnRecoveryTest`.
Verificado el 25 de octubre de 2026: `assembleDebug` y `testDebugUnitTest`
completados, con 24 pruebas aprobadas (10 de reconciliación). Se usó un log
 temporal y se detuvo Gradle al terminar. El APK de prueba se generó con el
 override local `-PBACKEND_BASE_URL=http://192.168.100.241:8080`; no se cambió
 `local.properties` ni se guardaron credenciales en el repositorio.

Verificado el 28/10/2026 después de corregir el cierre y reingreso de voz:
`assembleDebug` y `testDebugUnitTest` pasaron, con 38 pruebas aprobadas y sin
fallos, errores ni omisiones. Las nueve regresiones de recuperación cubren
historial antiguo/incompleto, copia temporal de navegación, estados terminales,
fallos transitorios de clasificación, errores de lectura, límite de consultas y
cancelación al salir. El APK usa `http://192.168.100.241:8080`; se guardó el log
en un archivo temporal y se detuvo Gradle. No se probó una nueva grabación en
el teléfono ni se compiló iOS. La pregunta repetida tras la primera negativa
requiere además el ajuste de reconocimiento del backend actualizado.

Verificación adicional del 28/10/2026 para el indicador atascado: se reprodujeron
antes de la corrección dos casos que ocultaban `FALLIDA` mientras el socket seguía
en `PROCESANDO`. El build final y las 42 pruebas unitarias pasaron sin fallos,
errores ni omisiones; Gradle quedó detenido. Cuatro regresiones nuevas cubren
fallo confirmado con socket abierto, fallo persistido al agotar la espera, salida
del estado de voz y resultado desconocido sin reabrir la entrevista. El APK se
generó a las 11:29 -03:00 con la misma URL LAN. No se probó un teléfono ni iOS.

Verificación final del cierre controlado por el servidor (28/10/2026):
`assembleDebug` y `testDebugUnitTest` completados, con 43 pruebas aprobadas, cero
fallos/errores/omisiones y Gradle detenido. El APK usa la URL LAN existente.
La clasificación pendiente se sigue automáticamente por GET también cuando un
POST de texto se reconcilia con un cierre guardado. No se muestra un botón de
reprocesar; los reintentos técnicos son responsabilidad del backend. La espera
usa hasta 80 lecturas y un límite total de 120 s, seguido de una consulta de estado
si el resultado sigue sin confirmarse. El backend pasó su suite aislada de 748
pruebas (730 aprobadas, 18 opcionales omitidas). Falta la prueba física de voz.

Verificación de turnos de texto durables (28/10/2026, 14:59 -03:00):
`assembleDebug` y las 52 pruebas Android pasaron sin fallos, errores ni omisiones.
Se probaron HTTP 202, recuperación al volver al chat, resultado desconocido sin
reenviar, bloqueo por indisponibilidad, UUID anterior recibido tarde, historial
terminal y clasificación final que sigue pendiente durante un reintento. El APK
usa `http://192.168.100.241:8080`; el log quedó en un archivo temporal y Gradle se
detuvo al finalizar. Hace falta reiniciar el backend actualizado e instalar este
APK. No se probó un teléfono ni se compiló iOS.

Si vence la espera durante la clasificación final de un turno de texto, la app
lo presenta como estado desconocido y detiene el aviso de procesamiento. La
consulta de estado sigue disponible sin reenviar el mensaje.
