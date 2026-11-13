# Lee tu agenda

App Android que cada día, a la hora que elijas, abre tu agenda **a pantalla completa** y te obliga a **tocar cada entrada** para darla por leída. Pensada para el TDAH: cuando las entradas se vuelven tan familiares que ya no las lees.

## Guía para usuarios

📄 **[Guía de instalación y uso (PDF)](docs/Guia-instalacion-y-uso.pdf)**: explicada paso a paso para personas con poca soltura con el móvil. Lista para imprimir o compartir. También se abre desde la app: **Acerca de → 📖 Manual de uso (PDF)**.
Se genera con `python3 docs/guia/render.py` (portada `docs/guia/portada.html` + guía `docs/guia/guia.html`).

## Qué hace

- Lee los eventos de **hoy** de todos los calendarios del móvil (Google Calendar, Samsung, Outlook… lo que tengas sincronizado).
- A la hora configurada salta a pantalla completa, incluso con el móvil bloqueado.
- Cada entrada es una tarjeta: hay que tocarla para marcarla. El botón **Hecho** no se activa hasta leerlas todas.
- **Anti-piloto-automático:** si tocas dos tarjetas en menos de 1,2 s, la tarjeta tiembla y no se marca.
- Etiqueta **NUEVO** en los eventos que aparecen por primera vez, para que destaquen entre los de siempre.
- Si sales sin terminar, vuelve a abrirse cada X minutos (configurable) hasta que la completes o llegue la hora límite.
- El botón **atrás** no la cierra, pero **inicio, recientes y el botón 📞 Llamar** sí te dejan salir. Las llamadas entrantes y las alarmas del reloj aparecen encima con normalidad (no es un bloqueo de pantalla).
- Lo que ya has marcado se guarda: si sales a llamar y vuelves, no pierdes el progreso.

## Cómo conseguir el APK

**Opción A — Android Studio (recomendada):**
1. Instala [Android Studio](https://developer.android.com/studio).
2. *File → Open* y elige esta carpeta `LeeTuAgenda`.
3. Conecta el móvil con la depuración USB activada y pulsa ▶ *Run*.
   (O *Build → Build APK(s)* y copia `app/build/outputs/apk/debug/app-debug.apk` al móvil.)

**Opción B — GitHub, sin instalar nada:**
1. Sube la carpeta a un repositorio de GitHub.
2. Pestaña *Actions* → espera a que termine "Compilar APK" → descarga `LeeTuAgenda-apk`.
3. Pasa el APK al móvil e instálalo (acepta "instalar apps de origen desconocido").

## Primer uso

1. Abre **Lee tu agenda** y pulsa *Conceder* en cada permiso. Los dos recomendados importan:
   - **Mostrar sobre otras apps**: sin él, si estás usando el móvil a esa hora Android solo muestra una notificación (al tocarla se abre la agenda). Con el móvil bloqueado salta a pantalla completa igualmente.
   - **Sin restricción de batería**: en Xiaomi, Samsung, Huawei, Oppo… evita que el sistema mate el aviso.
2. Activa el aviso diario y elige la hora.
3. Prueba con *Simular el aviso dentro de 15 s* y bloquea el móvil.

## Ajustes en el código

- `AgendaActivity.MIN_MS_BETWEEN_TAPS`: tiempo mínimo entre toques (1200 ms).
- Requiere Android 8.0 o superior.

## Firma del APK

La clave de firma **no está en el repositorio**. GitHub Actions la lee de los *secrets* del repositorio
(`SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`).
Compilado en local o en F-Droid, `assembleRelease` genera un APK sin firmar (F-Droid lo firma con su propia clave).

## Licencia

Lee tu agenda es software libre: puedes redistribuirlo y/o modificarlo bajo los términos de la
[Licencia Pública General GNU versión 3](LICENSE) (GPL-3.0) publicada por la Free Software Foundation.
Se distribuye con la esperanza de que sea útil, pero SIN NINGUNA GARANTÍA.
