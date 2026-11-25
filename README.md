# Festivos y Calendario con Docker Compose

Dos APIs integradas para generar y consultar calendarios colombianos, con MongoDB y PostgreSQL, redes privadas y persistencia mediante volúmenes.

## Inicio rápido

Requisitos: Docker Engine con Compose v2, puertos locales 8080 y 8081 disponibles y recursos para compilar Java 21 y Node 22. Reserve al menos 2 GiB libres antes de compilar o descargar imágenes.

```sh
docker compose config --quiet
docker compose build
docker compose up -d
docker compose ps
```

No ejecute simultáneamente otra copia que publique los mismos puertos. Espere a que los cuatro servicios estén saludables.

| Servicio | Tecnología | Acceso |
| --- | --- | --- |
| festivos | Node 22 | http://127.0.0.1:8080 |
| calendario | Java 21 / Spring Boot | http://127.0.0.1:8081 |
| mongo | MongoDB 7 | Solo red interna |
| postgres | PostgreSQL 16 | Solo red interna |

Las variables y credenciales locales de demostración están en `compose.yaml`. No son credenciales de producción. Las APIs solo publican puertos en loopback. Los datos se conservan en volúmenes; para detener sin borrarlos, use `docker compose stop`.

## Comprobar la integración

```sh
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts -v
PYTHONDONTWRITEBYTECODE=1 python3 scripts/check.py
```

El segundo comando **escribe datos** de 2027 y 2028; úselo solo sobre este entorno de demostración. Verifica generación por POST, consulta de 365/366 fechas únicas, clasificación y regeneración. Para consultar sin escribir:

```sh
curl --fail http://127.0.0.1:8080/api/festivos/obtener/2027
curl --fail http://127.0.0.1:8081/api/calendario/listar/2027
```

## Evidencias

- [Registro de integración y persistencia](evidencias/integracion-persistencia.log).
- [Verificación visual de solo lectura](evidencias/verificacion-visual.json): salud, redes, volúmenes y comparación actual con los datos previamente conservados tras reinicio.
- [Imágenes de evidencia](evidencias/capturas/).

Las capturas HTTP y visualizaciones de resultados documentan observaciones reales. Las visualizaciones de salida no son capturas de Docker Desktop ni de terminal. La comparación actual no equivale a un nuevo ensayo de reinicio.

## Estructura

- `snapshots/`: código fuente de ambas APIs, preservado sin cambios.
- `docker/` y `compose.yaml`: empaquetado y orquestación.
- `scripts/`: comprobaciones de integración y pruebas del validador.
- `evidencias/`: registros, JSON e imágenes.
- `PROVENANCE.json`: commits de origen e integridad SHA-256 de los 70 archivos fuente.

## Alcance y límites

Se conserva el contrato POST de generación. El healthcheck TCP de Calendario comprueba escucha, no corrección funcional. `depends_on` ordena el arranque, no supervisa continuamente las dependencias; `restart: unless-stopped` tampoco reinicia automáticamente por estado unhealthy. No se ensayaron fallos ni recreación de contenedores, ni se contrastó el catálogo completo con un oráculo independiente de Pascua. Las imágenes usan tags versionados, no digests inmutables.
