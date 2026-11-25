# API de Calendario

API REST con **Java 21, Spring Boot y PostgreSQL**, organizada mediante **arquitectura Onion**. Consume [API Festivos](https://github.com/Juandavm12/API-Festivos-ASII) para generar calendarios anuales con sus días clasificados.

## Funcionalidades

- Consumir los festivos de un año mediante HTTP.
- Generar y almacenar los 365 o 366 días del año como laborales, fines de semana o festivos; devolver `true` al confirmar el proceso completo.
- Consultar el calendario anual persistido, ordenado por fecha.
- Crear, listar, consultar, actualizar y eliminar tipos de día.

Los endpoints son públicos: no se implementan login, JWT ni gestión de usuarios. No se expone CRUD individual de los días del calendario.

## Autores

- Juan David Velasquez Murillo
- Paula Andrea Calderon Quintero
- Jose David Vasquez Diaz

## Ejecución local

Requisitos: **JDK 21**, PostgreSQL en ejecución y acceso a Internet para la primera descarga de Maven y dependencias. Maven Wrapper incluye los scripts para Linux/macOS (`./mvnw`) y Windows (`mvnw.cmd`); no es necesario instalar Maven globalmente.

1. Crear una base vacía desde las herramientas de PostgreSQL:

   ```bash
   createdb -h 127.0.0.1 -U postgres calendario
   ```

2. Desde la raíz del repositorio, iniciar Calendario con la contraseña del usuario de PostgreSQL:

   ```bash
   DB_PASSWORD='tu-clave-local' ./mvnw spring-boot:run
   ```

   Si el usuario local no requiere contraseña, se puede omitir `DB_PASSWORD`. Flyway crea las tablas y los tres tipos iniciales en la primera ejecución. Las migraciones no recrean categorías eliminadas mediante el CRUD.

3. Iniciar API Festivos siguiendo [su guía de ejecución](https://github.com/Juandavm12/API-Festivos-ASII#ejecución-local), con MongoDB disponible y su catálogo cargado. Se necesita para generar años, no para consultar calendarios guardados ni administrar tipos.

4. Generar y consultar un calendario:

   ```bash
   curl -X POST http://127.0.0.1:8081/api/calendario/generar/2023
   # true
   curl http://127.0.0.1:8081/api/calendario/listar/2023
   curl http://127.0.0.1:8081/api/tipos
   ```

`Ctrl+C` detiene la API y cierra sus conexiones; PostgreSQL y API Festivos se administran por separado. El arranque falla si PostgreSQL no está disponible o las migraciones no pueden completarse. No es necesario crear archivos `.env`.

### Configuración

| Variable | Valor predeterminado | Uso |
| --- | --- | --- |
| `HOST` | `127.0.0.1` | Interfaz de escucha local. |
| `PORT` | `8081` | Puerto de Calendario, distinto de Festivos. |
| `DB_URL` | `jdbc:postgresql://127.0.0.1:5432/calendario` | Conexión JDBC a la base. |
| `DB_USERNAME` | `postgres` | Usuario de PostgreSQL. |
| `DB_PASSWORD` | Vacío | Contraseña según la configuración local. |
| `FESTIVOS_URL` | `http://127.0.0.1:8080` | URL base de API Festivos, sin credenciales ni ruta del endpoint. |
| `FESTIVOS_TIMEOUT` | `5s` | Tiempo de espera positivo para la conexión y solicitud HTTP. |

Las credenciales se suministran mediante el entorno, no se guardan en el código. Para generar un JAR ejecutable:

```bash
./mvnw clean verify
java -jar target/api-calendario-0.0.1-SNAPSHOT.jar
```

El proceso `java -jar` utiliza las mismas variables de entorno y necesita PostgreSQL disponible.

## Documentación

| Documento | Contenido |
| --- | --- |
| [Arquitectura Onion](./DiagramaArquitectura-Taller2.md) | Núcleo, casos de uso, interfaces, adaptadores y flujo de generación. |
| [Modelo relacional](./DiagramaRelacional-Taller2.md) | Entidades `Tipo` y `Calendario`, campos, relaciones e integridad. |
| [Contratos REST](./ContratosAPI.md) | Endpoints, cuerpos JSON, estados HTTP, integración y casos de verificación. |

## Operaciones principales

| Método | Ruta | Función |
| --- | --- | --- |
| POST | `/api/calendario/generar/{anio}` | Generar o reemplazar el año de forma transaccional. |
| GET | `/api/calendario/listar/{anio}` | Consultar los días almacenados del año. |
| POST / GET | `/api/tipos` | Crear / listar tipos de día. |
| GET / PUT / DELETE | `/api/tipos/{id}` | Consultar / actualizar / eliminar un tipo. |

Ejemplo de CRUD de un tipo adicional:

```bash
curl -i -X POST http://127.0.0.1:8081/api/tipos \
  -H 'Content-Type: application/json' -d '{"id":4,"tipo":"Dia especial"}'
curl http://127.0.0.1:8081/api/tipos/4
curl -X PUT http://127.0.0.1:8081/api/tipos/4 \
  -H 'Content-Type: application/json' -d '{"tipo":"Dia especial actualizado"}'
curl -i -X DELETE http://127.0.0.1:8081/api/tipos/4
```

## Reglas de funcionamiento

- Un festivo prevalece sobre el fin de semana. Los identificadores `1`, `2` y `3` corresponden, respectivamente, a laboral, fin de semana y festivo; sus nombres son editables.
- `Calendario.Descripcion` contiene el día de la semana en español, no el nombre del festivo.
- Los tipos adicionales no adquieren reglas de clasificación automáticamente. No se puede eliminar un tipo con días asociados.
- El núcleo y los casos de uso no dependen de Spring, JPA ni del cliente HTTP; los adaptadores implementan interfaces internas.
- Calendario consume la lista anual de Festivos; no recalcula Pascua ni traslados al lunes.
- Regenerar un año no duplica fechas ni modifica otros años. Un fallo revierte los cambios y conserva el calendario previo.
- Una consulta de un año no generado devuelve `[]`; no llama a API Festivos.

## Pruebas

```bash
./mvnw test
```

La suite incluye reglas del núcleo, CRUD, contratos HTTP, cliente de Festivos, integridad referencial, rollback y concurrencia. Las pruebas de persistencia e integración inician **PostgreSQL real en un proceso temporal**, en puertos efímeros, y lo cierran al terminar. No usan la base de desarrollo ni requieren Docker; necesitan ejecutarse con un usuario distinto de root. La primera ejecución descarga también los binarios de PostgreSQL para la plataforma.

Para comprobar la integración contra una API Festivos real ya iniciada y con su catálogo cargado:

```bash
./mvnw -Dtest=FestivosRealTest -Dfestivos.real.url=http://127.0.0.1:8080 test
```

Esa prueba utiliza PostgreSQL temporal y compara las fechas festivas persistidas con la respuesta de API Festivos. Sin `festivos.real.url`, se omite; el resto de las pruebas usa un servidor HTTP local controlado para comprobar fallos y respuestas inválidas.

## Tecnologías

Java 21, Spring Boot 3.5.16 / Spring MVC, Spring Data JPA, PostgreSQL, Flyway, cliente HTTP del JDK, JUnit 5 y Mermaid. Maven Wrapper fija Maven 3.9.16 y verifica el SHA-256 de su distribución.
