#!/bin/bash
echo "Starting Gin framework server..."
cd gin
go run main.go -f ../etc/gin/config.yaml
