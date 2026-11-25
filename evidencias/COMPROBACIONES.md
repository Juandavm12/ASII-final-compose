# Comprobaciones de la entrega

Verificación de solo lectura: 24 de noviembre de 2026 (UTC).

| Comprobación | Resultado |
| --- | --- |
| Cuatro servicios Compose | running / healthy |
| Consulta HTTP Calendario 2027 | 200; 365 fechas únicas |
| Consulta HTTP Calendario 2028 | 200; 366 fechas únicas |
| Comparación con JSON previo tras reinicio | Idénticos en ambos años |
| Integridad del código fuente | 70 SHA-256 coincidentes con PROVENANCE.json |
| Pruebas del validador | 3 pruebas OK |
| Configuración Compose | Válida |
| Sintaxis shell y Python | Válida |

Los datos de salud, redes, volúmenes y consultas están en [verificacion-visual.json](verificacion-visual.json). Sus tres imágenes son visualizaciones de resultados de comandos y HTTP, no capturas de una terminal. Las dos capturas HTTP anteriores conservan sus metadatos originales en `capturas/`.

El registro [integracion-persistencia.log](integracion-persistencia.log) documenta la prueba previa de reinicio. La consulta actual compara su resultado conservado, pero no constituye un nuevo ensayo de reinicio. No se ejecutaron pruebas destructivas de fallo o recreación ni un contraste independiente completo del catálogo festivo.
