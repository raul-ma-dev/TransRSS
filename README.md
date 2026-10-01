# TransRSS

Aplicación Android para consultar noticias de CanalTrans y episodios de **En Caso De Que El Mundo Se Desintegre**. La versión móvil está desarrollada con **Kotlin, Jetpack Compose y Material 3**, y utiliza WebView para mostrar el contenido HTML de los artículos y reproducir audio.

## Funcionalidades

- Descarga automática de los feeds al iniciar el lector.
- Navegación inferior con **Episodios** y **Noticias**, en ese orden.
- Recarga manual al deslizar hacia abajo.
- Tema oscuro con acentos rojos basados en `#A61219`, tarjetas con gradiente e imagen de fondo.
- Renderizado inicial de **5 entradas**; al avanzar por el listado se añaden bloques de 5.
- Fechas en español con formato `miércoles, 30/09/2026`, calculadas en **GMT−3 fijo**, sin mostrar la hora.
- Apertura del enlace original en un navegador externo.
- Animación de carga Compose a pantalla completa y con fondo transparente: ignición, humo, llamas, despegue y partículas dibujadas con Canvas.
- Previews de Compose con datos locales, sin descargar los RSS.

## Fuentes y navegación

| Opción visible | Identificador interno | Encabezado | Feed |
| --- | --- | --- | --- |
| Episodios | `AUDIOBOOM` | En Caso De Que El Mundo Se Desintegre | <https://audioboom.com/channels/3716163.rss> |
| Noticias | `CANALTRANS` | Noticias CanalTrans | <https://canaltrans.com/rss/transrss.xml> |

Los identificadores internos conservan los nombres de las fuentes, aunque el menú muestre etiquetas distintas.

### Noticias

Muestra las entradas de CanalTrans que **no** tienen la categoría `Radio`. Cada tarjeta contiene el HTML completo y, cuando existe un enlace, el botón **Abrir original**. No necesita abrir una pantalla de detalle.

### Episodios

Muestra las entradas `Radio` de CanalTrans combinadas con el audio de Audioboom. Las tarjetas contienen título, fecha y resumen; al pulsarlas se abre el detalle con el contenido completo y el reproductor.

El resumen y el detalle de episodios omiten el contenido de los `<h1>` internos de la descripción, sin eliminar el título principal. La conversión a texto también elimina los marcadores de imagen que podrían aparecer como `[OBJ]` en las tarjetas o previews.

## Combinación de los RSS

1. Se identifican las entradas de CanalTrans cuya categoría es `Radio`, ignorando mayúsculas/minúsculas y espacios alrededor de la categoría.
2. CanalTrans aporta el título, la descripción HTML, la fecha, las categorías y el enlace original.
3. Audioboom aporta la URL del audio del episodio coincidente.
4. La coincidencia se busca en el **mismo día calendario según la zona horaria original de la entrada de CanalTrans**.
5. Si hay varios audios en ese día, se elige el de publicación más cercana. Los empates conservan el orden original del feed de audio.
6. Si no hay coincidencia, la entrada sigue visible, pero sin audio asociado.
7. Los episodios se ordenan por fecha de publicación, del más reciente al más antiguo; las fechas no interpretables quedan al final.

La conversión a GMT−3 afecta **solo a la presentación**. No cambia las fechas originales ni las reglas de coincidencia entre feeds. Si una fecha no puede interpretarse, se conserva su texto original.

## Reproducción de audio

El audio se reproduce con un elemento HTML `<audio controls>` dentro de WebView:

- No se reproduce automáticamente: requiere una acción del usuario.
- Usa `preload="none"` y recursos de audio HTTPS.
- Cuando el HTML no contiene un reproductor, se genera a partir de la URL de audio asociada.
- En el detalle de episodios, el reproductor generado se coloca después de la primera imagen; si no hay imagen, al final del contenido.

Este reproductor **no está conectado a un servicio nativo de reproducción**. No se implementan reproducción garantizada en segundo plano, descarga de episodios ni controles de audio integrados con Android Auto.

## Estructura del proyecto

```text
.
├── mobile/       # Lector RSS completo para teléfonos y tabletas
├── automotive/   # Aplicación base para Android Automotive
├── shared/       # Recursos y servicio multimedia compartidos
├── gradle/       # Catálogo de versiones, wrapper y configuración JVM
├── build.gradle.kts
└── settings.gradle.kts
```

### Estado de los módulos

- **`mobile`**: contiene el lector, la navegación, el procesamiento de feeds, las tarjetas, el detalle HTML y el loading animado.
- **`automotive`**: incluye una actividad Compose básica que muestra el nombre de la aplicación. Todavía no incorpora la interfaz ni el lector RSS de `mobile`.
- **`shared`**: contiene recursos compartidos y `MyMusicService`, una base de `MediaBrowserServiceCompat` con `MediaSessionCompat`. Su catálogo está vacío y sus callbacks de reproducción aún no tienen implementación.

## Tecnologías y requisitos

| Componente | Configuración actual |
| --- | --- |
| Nombre de la app móvil | TransRSS |
| Application ID móvil | `com.rama.rss` |
| Versión móvil | `1.0` (`versionCode = 1`) |
| Android mínimo móvil | Android 9 / API 28 |
| `compileSdk` / `targetSdk` móvil | 37 / 37 |
| Android Gradle Plugin | 9.4.1 |
| Kotlin | 2.2.10 |
| Gradle Wrapper | 9.6.0 |
| Compose BOM | 2026.02.01 |
| JVM del daemon Gradle | JDK 25, según `gradle/gradle-daemon-jvm.properties` |
| Compatibilidad del código Java | Java 11 |

Se necesita Android Studio compatible con la configuración del proyecto, Android SDK Platform 37, acceso a Internet para resolver dependencias y un dispositivo/emulador compatible para ejecutar la app.

**Java 11 es el nivel de compatibilidad del código, no la versión configurada para ejecutar Gradle.** El proyecto define JDK 25 para el daemon; la disponibilidad o descarga de esa JVM depende de la configuración local de Gradle.

## Preparación y ejecución

1. Abrir la carpeta raíz del proyecto en Android Studio.
2. Configurar el Android SDK y la JVM de Gradle.
3. Sincronizar el proyecto e instalar los componentes del SDK solicitados.
4. Seleccionar la configuración del módulo `mobile`.
5. Ejecutar en un dispositivo o emulador con API 28 o superior.

`local.properties` contiene la ruta local del SDK y debe configurarse para cada equipo. No se requiere una clave API para los feeds utilizados.

### Compilar en Windows

Desde CMD, en la raíz del proyecto:

```bat
gradlew.bat :mobile:assembleDebug
```

Desde PowerShell:

```powershell
.\gradlew.bat :mobile:assembleDebug
```

Desde Git Bash:

```bash
./gradlew :mobile:assembleDebug
```

El APK de depuración móvil se genera en:

```text
mobile/build/outputs/apk/debug/mobile-debug.apk
```

Para compilar la variante Automotive:

```bat
gradlew.bat :automotive:assembleDebug
```

El APK de depuración no es una publicación firmada para distribución en tiendas.

## Configuración del lector

Los archivos indicados a continuación están dentro de `mobile/src/main/java/com/rama/rss/`, salvo donde se indique otro directorio.

| Ajuste | Archivo | Qué modificar |
| --- | --- | --- |
| URLs, nombres del menú y encabezados | `reader/RssViewModel.kt` | Propiedades `url`, `title` y `header` de `RssSource` |
| Orden del menú | `reader/ReaderConfiguration.kt` | Orden de la lista devuelta por `enabledRssSources()` |
| Mostrar/ocultar Noticias | `reader/ReaderConfiguration.kt` | `CANALTRANS_TAB_ENABLED`, actualmente `true` |
| Entradas por bloque | `reader/FeedPagination.kt` | `FEED_PAGE_SIZE`, actualmente `5` |
| Zona y formato de fecha | `reader/RssDateFormatter.kt` | `ReaderDateOffset` y `ReaderDateFormat` |
| Colores y tema | `ui/theme/` | Paleta y configuración de Material 3 |
| Estilos HTML de artículos | `MainActivity.kt` | Función `articleDocument()` |
| Secuencia de despegue | `ui/rocket/` | Estado, ViewModel, pantalla y animación |
| Física y aspecto de las chispas | `ui/rocket/RocketParticleSystem.kt` y `RocketSparkParticles.kt` | Emisión, velocidades, gravedad, duración y dibujo |
| Nombre de la aplicación | `mobile/src/main/res/values/strings.xml` | Recurso `app_name` |

Ocultar Noticias en el menú **no elimina la dependencia de CanalTrans**: sus entradas de radio siguen siendo necesarias para la combinación de Episodios.

## Arquitectura y rendimiento

- `RssRepository` descarga y analiza RSS/Atom usando un parser XML.
- `RssViewModel` mantiene la selección y el estado de carga; las descargas necesarias se ejecutan de manera concurrente.
- La preparación y combinación de feeds se realiza en `Dispatchers.Default`, fuera del hilo de UI.
- Los audios se indexan por día y zona horaria; se utiliza búsqueda binaria para encontrar el más cercano.
- La lista usa `LazyColumn` y paginación local. Se mantienen datos preparados en memoria y se reutilizan conversiones de HTML durante la composición.
- `ArticleHtmlContent` integra WebView con Compose; las tarjetas de Noticias ajustan su altura al contenido HTML.
- Las partículas se actualizan con el reloj de fotogramas de Compose y se dibujan en Canvas, sin recomponer todo el cohete en cada fotograma. Su cantidad está limitada.

**La paginación limita el renderizado, no las descargas.** Cada actualización descarga los feeds completos, dentro del límite de tamaño permitido. No hay caché persistente de feeds ni lectura sin conexión implementada.

## Restricciones de contenido web

- Descarga de feeds mediante HTTPS, con tiempos de conexión y lectura de 15 segundos.
- Límite de lectura de 5 MB por feed.
- Rechazo de documentos XML con DTD.
- JavaScript, acceso a archivos y acceso a contenido local deshabilitados en WebView.
- Contenido mixto bloqueado y política CSP para restringir los recursos del documento HTML.
- Imágenes permitidas mediante HTTPS y `data:`; audio mediante HTTPS.
- Enlaces originales HTTP/HTTPS abiertos fuera de la app.

Estas restricciones no equivalen a una implementación general de sanitización de HTML; la app muestra las descripciones proporcionadas por las fuentes configuradas.

## Pruebas

### Pruebas unitarias

```bat
gradlew.bat :mobile:testDebugUnitTest
```

Cubren paginación, límites de lectura, combinación de feeds, formato de fechas, transiciones del cohete y física de partículas: trayectorias, gravedad, caducidad, emisión y límite de cantidad.

### Compilar las pruebas instrumentadas

```bat
gradlew.bat :mobile:assembleDebugAndroidTest
```

### Ejecutar pruebas en un dispositivo o emulador

```bat
gradlew.bat :mobile:connectedDebugAndroidTest
```

Para ejecutar solo las pruebas del HTML de artículos:

```bat
gradlew.bat :mobile:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.rama.rss.ArticleAppearanceTest
```

En Git Bash se pueden ejecutar las mismas tareas sustituyendo `gradlew.bat` por `./gradlew`.

Los informes unitarios se generan en `mobile/build/reports/tests/`; los informes instrumentados, en `mobile/build/reports/androidTests/`.

### Limitaciones conocidas de validación

- Algunas pruebas de UI con Espresso 3.5.1 han fallado en el emulador utilizado por una incompatibilidad con `android.hardware.input.InputManager.getInstance`.
- La prueba instrumentada de repetición/reinicio del loading ha presentado timeouts; su estabilización sigue pendiente.
- Los previews representan contenido estático: no reproducen audio ni ejecutan la secuencia animada completa del loading. Hay un preview específico de las partículas con una muestra estática.

Compilar el APK de pruebas no implica que todas las pruebas instrumentadas hayan pasado. Estas limitaciones deben tenerse en cuenta al validar cambios en el entorno local.


