#!/bin/sh
set -eu
node scripts/cargar-festivos.js
exec node server.js
