#!/bin/bash
# Start all Optix services in DEV mode
cd "$(dirname "$0")"
./optix.sh dev all start
