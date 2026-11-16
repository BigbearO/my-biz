# app.mk —— 每个服务的 Makefile 仅需一行 `include ../../../app.mk`
# 由根 Makefile 递归调用;也可在服务目录内直接 make build/run/ent

SERVICE_PATH := $(patsubst %/,%,$(dir $(firstword $(MAKEFILE_LIST))))
SERVICE_NAME := $(notdir $(patsubst %/service,%,$(SERVICE_PATH)))
ROOT_DIR := $(abspath $(SERVICE_PATH)/../../..)
BIN_NAME := server

# 版本:git tag 优先,否则回落 .env 的 SERVICE_APP_VERSION,再否则 dev
VERSION ?= $(shell git describe --tags --abbrev=0 2>/dev/null || echo dev)
ifeq ($(VERSION), dev)
  ifneq ($(wildcard $(ROOT_DIR)/.env),)
    VERSION = $(shell grep -E "^SERVICE_APP_VERSION=" $(ROOT_DIR)/.env | cut -d '=' -f2)
  endif
endif
LDFLAGS := -X main.version=$(VERSION)

# 运行环境:go run 经 -e 传入;容器经 APP_ENV 注入(见 Dockerfile)。从仓库根可 make run APP_ENV=test
APP_ENV ?= dev

.PHONY: build build_only run api openapi ent test clean

build: api openapi ## 生成 + 编译
	go build -ldflags "$(LDFLAGS)" -o ./bin/$(BIN_NAME) ./cmd/$(BIN_NAME)

build_only: ## 仅编译
	go build -ldflags "$(LDFLAGS)" -o ./bin/$(BIN_NAME) ./cmd/$(BIN_NAME)

run: api openapi ## 生成 + 运行
	go run ./cmd/$(BIN_NAME) -conf ./configs -e "$(APP_ENV)"

api: ## proto → go
	cd $(ROOT_DIR)/api && buf generate --template buf.gen.yaml

openapi: ## proto → openapi.yaml
	cd $(ROOT_DIR)/api && buf generate --template buf.gen.openapi.yaml

ent: ## ent codegen(存在 schema 时)
	@if [ -d internal/data/ent/schema ]; then \
		go run entgo.io/ent/cmd/ent generate ./internal/data/ent/schema; \
	fi

test: ## 服务内测试
	go test ./...

clean: ## 清理
	rm -rf ./bin
