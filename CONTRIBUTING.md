# Guía de Contribución - Spectrum 24GHz

## Métodos de Compilación

### Compilación con Android Studio
1. **Clonar repositorio**:
   ```bash
   git clone https://github.com/anfegomezver/spectrum24ghz.git
   ```
2. **Abrir proyecto**: Seleccionar **"Open"** y buscar la carpeta del repositorio.
3. **Sincronizar Gradle**: Esperar la descarga de dependencias.
4. **Ejecutar o Generar**:
    - **Ejecución**: Conectar dispositivo Android o emulador y presionar **"Run"**.
    - **APK**: `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`.

### Compilación via Gradle CLI
Requiere [Android SDK](https://developer.android.com/studio) instalado y la variable de entorno `ANDROID_HOME` configurada.

1. **Descargar dependencias**:
   ```bash
   ./gradlew
   ```
2. **Compilar versión de depuración**:
   ```bash
   ./gradlew assembleDebug
   ```
3. **Ubicación del APK**:
   `app/build/outputs/apk/debug/app-debug.apk`

---

## Requisitos del Sistema
- **Android SDK**: API Level 23 (Android 6.0) hasta API Level 34 (Android 14).
- **Java**: Versión 8 (1.8).
