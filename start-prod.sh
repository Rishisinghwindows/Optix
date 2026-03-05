#!/bin/bash
# Start all Optix services in PROD mode
cd "$(dirname "$0")"
./optix.sh prod all start
