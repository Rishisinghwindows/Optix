#!/bin/bash
# Start all Optix services in QA mode
cd "$(dirname "$0")"
./optix.sh qa all start
