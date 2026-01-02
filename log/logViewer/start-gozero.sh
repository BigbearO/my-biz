#!/bin/bash
echo "Starting go-zero framework server..."
cd gozero
go run main.go -f ../etc/gozero/logviewer.yaml
