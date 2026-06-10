# Spectrum 24GHz - Guía de la Aplicación

Bienvenido al repositorio de **Spectrum 24GHz**, una aplicación Android diseñada para el análisis y visualización del espectro electromagnético (o señales WiFi) en la banda de 2.4 GHz.

---

## 📋 Requisitos de la Aplicación

Para poder ejecutar e instalar esta aplicación, tu dispositivo o entorno de desarrollo debe cumplir con los siguientes requisitos mínimos:

| Requisito | Detalle |
| :--- | :--- |
| **Sistema Operativo** | Android 6.0 (Marshmallow) o superior |
| **SDK Mínimo (minSdk)** | API Level 23 |
| **SDK Objetivo (targetSdk)** | API Level 34 (Android 14) |
| **Versión de Java** | Java 8 (1.8) |
| **Permisos Requeridos** | (Puede requerir permisos de Ubicación para escaneo de WiFi, dependiendo del uso) |

---

## 👥 Integrantes del Proyecto

Las siguientes personas participaron en el proyecto (ordenados por código de forma ascendente):

 - **0192007:** Luisa Fernanda Acosta Vergel.
 - **0192018:** Angelo Andree Acosta Manzano.
 - **0192019:** Stefanny Ximena Páez Bayona.
 - **0192041:** Jairo Alonso Ramos Méndez.
 - **0192092:** Paula Johanna García Rodríguez.
 - **0192106:** Darwin Jhosett Bermúdez Romero.
 - **0192108:** Angel Andrés Boada Salazar.
 - **0192111:** Valentina Prado Sarabia.
 - **0192125:** Maicol Eduardo Robles Salazar.
 - **0192128:** Michell Natalia Cárdenas Jiménez.
 - **0192155:** Anderson Lizarazo Tellez.
 - **0192163:** Javier Quintero.
 - **0192250:** Andrés Felipe Gómez Verjel.
 - **0192295:** Andrey Castilla Contreras.
 - **0192324:** Arley Santiago Quintero.

---

## 📚 Documentación Técnica

Para más detalles sobre la estructura del proyecto y la guía de desarrollo, consulta los siguientes archivos:
- **[ARCHITECTURE.md](./ARCHITECTURE.md)**: Descripción técnica de la arquitectura y componentes.
- **[CONTRIBUTING.md](./CONTRIBUTING.md)**: Guía paso a paso para compilar el proyecto.

---

## 🚀 Guía de Instalación y Obtención del APK

Existen dos formas principales de obtener e instalar la aplicación en tu dispositivo:

### Opción 1: Descargar el APK desde GitHub (Recomendado)

Si solo quieres instalar y usar la aplicación sin necesidad de ver el código fuente, la mejor manera es descargar el archivo APK directamente desde los "Releases" (Lanzamientos) de GitHub.

**Pasos:**
1. Ve al siguiente enlace: **[Descargar Spectrum 24GHz v1.0.0](https://github.com/anfegomezver/spectrum24ghz/releases/tag/v1.0.0)**
2. En la sección **Assets** (al final de la página), haz clic en el archivo con extensión `.apk` (por ejemplo, `app-debug.apk` o `Spectrum24GHz_v1.0.0.apk`) para descargarlo en tu dispositivo Android.
3. Una vez descargado, abre el archivo. Es posible que tu dispositivo te pida permiso para **"Instalar aplicaciones de orígenes desconocidos"**. Debes habilitar esta opción para proceder.
4. Sigue las instrucciones en pantalla para finalizar la instalación y ¡listo!

### Opción 2: Compilar el proyecto desde el código fuente (Para Desarrolladores)

Si deseas modificar el código o compilar la aplicación tú mismo:

1. **Clonar el repositorio:**
   Abre tu terminal o consola de comandos y ejecuta:
   ```bash
   git clone https://github.com/TU_USUARIO/spectrum24ghz.git
   ```
2. **Abrir en Android Studio:**
   Abre Android Studio, selecciona "Open" y busca la carpeta `spectrum24ghz` que acabas de clonar.
3. **Sincronizar Gradle:**
   Espera a que Android Studio descargue las dependencias y sincronice el proyecto con los archivos de Gradle.
4. **Ejecutar o Generar APK:**
   - **Para ejecutar directamente:** Conecta tu dispositivo Android mediante USB (con la depuración USB activada) o inicia un emulador y presiona el botón "Run" (el ícono de 'play' verde).
   - **Para generar el APK:** Ve al menú superior `Build` -> `Build Bundle(s) / APK(s)` -> `Build APK(s)`. Una vez terminado, aparecerá un mensaje en la esquina inferior derecha con un enlace para "locate" (ubicar) el archivo APK generado, el cual podrás transferir a tu teléfono e instalar.

---

## 📖 Uso Básico de la Aplicación

Al iniciar la aplicación, encontrarás la vista principal (TimeGraphView) que dibujará en tiempo real el comportamiento del espectro o las señales captadas en la frecuencia de 2.4GHz. 
Asegúrate de conceder los permisos solicitados por la aplicación al iniciarla por primera vez, ya que en Android son necesarios para que el escaneo funcione correctamente.
