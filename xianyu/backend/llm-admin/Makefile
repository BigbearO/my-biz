.PHONY: help init api openapi ent gen build build_only run all test vet clean

help: ## 显示帮助
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-10s\033[0m %s\n", $$1, $$2}'

init: ## 安装 protoc 插件与 CLI 工具
	go install google.golang.org/protobuf/cmd/protoc-gen-go@latest
	go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest
	go install github.com/go-kratos/kratos/cmd/protoc-gen-go-http/v3@latest
	go install github.com/google/gnostic/cmd/protoc-gen-openapi@latest
	go install github.com/bufbuild/buf/cmd/buf@latest

api: ## proto → go 代码(buf)
	cd api && buf generate --template buf.gen.yaml

openapi: ## proto → openapi.yaml
	cd api && buf generate --template buf.gen.openapi.yaml

gen: api openapi ## 全部代码生成

build: ## 编译全部服务
	@for mk in app/*/*/Makefile; do $(MAKE) -C $$(dirname $$mk) build; done

build_only: ## 仅编译(跳过代码生成)
	@for mk in app/*/*/Makefile; do $(MAKE) -C $$(dirname $$mk) build_only; done

run: ## 运行全部服务
	@for mk in app/*/*/Makefile; do $(MAKE) -C $$(dirname $$mk) run; done

test: ## 全量测试
	go test ./...

vet: ## go vet
	go vet ./...

clean: ## 清理产物
	@for mk in app/*/*/Makefile; do $(MAKE) -C $$(dirname $$mk) clean; done
