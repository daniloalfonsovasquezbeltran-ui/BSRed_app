# BSRed Android

Visor de https://bsred.onrender.com con rastreo del chofer durante un viaje activo.

La aplicación solicita ubicación precisa y notificaciones al abrirse. La ubicación empieza a compartirse al pulsar «Iniciar viaje» en la consola del chofer y se detiene al finalizar. El servicio de ubicación usa una notificación persistente y puede continuar con la pantalla apagada. Sólo el origen HTTPS de BSRed puede comunicarse con el servicio nativo.

Abrir el proyecto en Android Studio con JDK 17, SDK 34 y Build Tools 34.0.0. Para compilar y validar: `bash gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest`. El APK debug está en `app/build/outputs/apk/debug/app-debug.apk`.

Se necesita el servidor BSRed con la migración `004_choferes_viajes.sql`. Los choferes se crean desde la cuenta de su empresa, nunca por registro público. Para detalles consultar `docs/choferes-viajes.md` del repositorio web.

Los checks JVM no verifican sensores ni restricciones de batería de un dispositivo real. Probar el inicio, permisos denegados, pantalla apagada, pérdida de red, suspensión y finalización en un Android real antes de operación. Para actualizar una instalación conservar la firma original; no subir claves ni archivos privados al repositorio.
