# Contratos REST — API Calendario

Las respuestas usan JSON y las rutas no requieren autenticación. No hay endpoints de login ni emisión de tokens.

## Calendario

| Método | Ruta | Respuesta exitosa |
| --- | --- | --- |
| POST | `/api/calendario/generar/{anio}` | `200 OK`, cuerpo `true`, después de confirmar la transacción. |
| GET | `/api/calendario/listar/{anio}` | `200 OK`, arreglo de días ordenados por fecha. |

`anio` debe ser un entero entre `1` y `9999`, igual que en API Festivos. La generación no recibe cuerpo. Se usa POST porque modifica datos; repetirla no duplica fechas.

Ejemplo de un elemento de la consulta anual:

```json
{
  "id": 1,
  "fecha": "2023-01-01",
  "tipo": { "id": 3, "tipo": "Dia festivo" },
  "descripcion": "Domingo"
}
```

- Un año generado contiene exactamente 365 o 366 elementos y una sola fila por fecha.
- Un año aún no generado devuelve `[]`; la consulta no lo genera implícitamente.
- Una regeneración puede cambiar los `id` diarios, pero no afecta otros años.
- El resultado `true` indica persistencia completa. Los fallos usan estados HTTP de error, no `200 false` ni un éxito parcial.
- No se expone CRUD individual de los días del calendario.

## Tipos de día

| Método | Ruta | Cuerpo | Respuesta exitosa |
| --- | --- | --- | --- |
| POST | `/api/tipos` | `{ "id": 4, "tipo": "Dia especial" }` | `201 Created`, tipo creado y cabecera `Location: /api/tipos/4`. |
| GET | `/api/tipos` | — | `200 OK`, arreglo ordenado por `id`; `[]` si está vacío. |
| GET | `/api/tipos/{id}` | — | `200 OK`, `{ "id": 4, "tipo": "Dia especial" }`. |
| PUT | `/api/tipos/{id}` | `{ "tipo": "Dia especial actualizado" }` | `200 OK`, tipo actualizado con su mismo `id`. |
| DELETE | `/api/tipos/{id}` | — | `204 No Content`, sin cuerpo. |

### Identidad y validación

- El `id` de tipo es un entero positivo de 32 bits, proporcionado al crear e inmutable al actualizar. Esto permite restaurar una categoría eliminada sin agregar campos al modelo.
- `tipo` es obligatorio, de texto, se guarda sin espacios exteriores y admite entre 1 y 100 caracteres después de recortarlo. No se exige unicidad del nombre: la identidad es el `id`.
- POST acepta exactamente `id` y `tipo`; PUT acepta únicamente `tipo`. No se admiten campos adicionales.
- PUT actualiza un tipo existente; no crea uno nuevo. DELETE de un tipo inexistente devuelve `404`.
- Un `id` ya existente produce `409`. Un tipo referenciado por cualquier día del calendario no puede eliminarse: devuelve `409` sin modificar registros.

### Categorías de generación

| Id estable | Nombre inicial | Uso automático |
| --- | --- | --- |
| 1 | `Dia laboral` | Lunes a viernes no festivos. |
| 2 | `Fin de Semana` | Sábados y domingos no festivos. |
| 3 | `Dia festivo` | Fechas informadas por API Festivos. |

La carga inicial de la base crea estas tres categorías. No se recrean en cada arranque: si una se elimina sin referencias, puede restaurarse mediante POST con su mismo `id`.

El CRUD permite renombrarlas sin cambiar sus reglas. Los nombres actuales se reflejan también al consultar días ya generados, porque estos referencian el tipo, no una copia del nombre. Los identificadores adicionales no reciben reglas automáticas ni se asignan por semejanza de nombre. No existe una operación para reasignar días a tipos adicionales.

Si falta alguno de los tipos `1`, `2` o `3`, la generación devuelve `409`; el calendario anterior permanece intacto. Esto permite CRUD completo sin reinterpretar categorías ni recrearlas silenciosamente.

## Integración con API Festivos

La URL base es configurable y, por defecto, apunta a `http://127.0.0.1:8080`. Calendario escucha en `8081` por defecto, también configurable.

```http
GET /api/festivos/obtener/2023
```

Respuesta esperada:

```json
[
  { "festivo": "Año nuevo", "fecha": "2023-01-01" }
]
```

El ejemplo muestra un elemento, no el catálogo anual completo. Calendario utiliza `fecha` para clasificar; no guarda `festivo` en `Descripcion`. La petición no envía tokens. Se establece un tiempo de espera finito y configurable. No se transforma un error HTTP, un timeout ni un cuerpo inválido en una lista vacía.

Consultar calendarios guardados y administrar tipos no requiere que API Festivos esté disponible.

## Errores

Todas las respuestas de error propias usan esta estructura, sin trazas internas ni credenciales:

```json
{
  "codigo": "TIPO_EN_USO",
  "mensaje": "El tipo tiene dias del calendario asociados."
}
```

| Estado | Situación | Código |
| --- | --- | --- |
| `400 Bad Request` | Año, identificador, campos o JSON inválidos. | `SOLICITUD_INVALIDA` |
| `404 Not Found` | Tipo inexistente en consulta individual, actualización o eliminación. | `TIPO_NO_ENCONTRADO` |
| `409 Conflict` | Crear un identificador de tipo existente. | `TIPO_EXISTENTE` |
| `409 Conflict` | Eliminar un tipo con días asociados. | `TIPO_EN_USO` |
| `409 Conflict` | Generar sin las tres categorías necesarias. | `TIPOS_REQUERIDOS_AUSENTES` |
| `502 Bad Gateway` | API Festivos responde con error HTTP o datos inválidos. | `RESPUESTA_FESTIVOS_INVALIDA` |
| `503 Service Unavailable` | No es posible establecer conexión con API Festivos. | `FESTIVOS_NO_DISPONIBLE` |
| `504 Gateway Timeout` | API Festivos excede el tiempo de espera. | `TIEMPO_FESTIVOS_AGOTADO` |
| `500 Internal Server Error` | Fallo inesperado o error de persistencia no recuperable. | `ERROR_INTERNO` |

Una violación de integridad reconocida al crear o eliminar un tipo se traduce al conflicto correspondiente, aunque la produzca una operación concurrente. No se informa éxito antes del commit.

## Verificación funcional

- Año común y bisiesto; límites del rango y años inválidos.
- Festivo en sábado/domingo con prioridad sobre fin de semana; descripciones en español y fechas sin desfase horario.
- Regeneración sin duplicados, aislamiento entre años y generación concurrente del mismo año.
- Rollback ante fallos de escritura y conservación del año previo ante fallos de API1.
- Respuestas de API1 inválidas, fechas fuera del año, duplicados, lista vacía y timeout.
- CRUD completo: validación, inexistentes, identificadores repetidos y eliminación de tipos referenciados.
- Renombrar una categoría sin cambiar su clasificación; eliminar una categoría sin referencias y restaurarla por su identificador.
- Consulta de año no generado y consulta con API1 fuera de servicio.

Los casos de uso se prueban con dobles de los puertos; las transacciones, claves foráneas, unicidad y bloqueo por año se verifican contra PostgreSQL real temporal. La prueba `FestivosRealTest` comprueba ambas APIs con sus bases de datos reales. Los comandos se encuentran en [Pruebas](./README.md#pruebas).
