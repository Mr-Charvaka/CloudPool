# CloudPool CLI — Build System
# =============================

BINARY      ?= cloudpool
VERSION     ?= $(shell git describe --tags --always --dirty 2>/dev/null || echo "0.1.0-dev")
COMMIT      ?= $(shell git rev-parse --short HEAD 2>/dev/null || echo "none")
DATE        ?= $(shell date -u +"%Y-%m-%dT%H:%M:%SZ")
BUILT_BY    ?= $(shell whoami)
GO_VERSION  ?= $(shell go version | cut -d' ' -f3)
OUTPUT_DIR  ?= build
LDFLAGS     := -s -w \
               -X github.com/cloudpool/cli/pkg/version.Version=$(VERSION) \
               -X github.com/cloudpool/cli/pkg/version.Commit=$(COMMIT) \
               -X github.com/cloudpool/cli/pkg/version.Date=$(DATE) \
               -X github.com/cloudpool/cli/pkg/version.BuiltBy=$(BUILT_BY) \
               -X github.com/cloudpool/cli/pkg/version.GoVersion=$(GO_VERSION)

.PHONY: all build clean test lint cover install uninstall release

all: clean lint test build

build:
	@mkdir -p $(OUTPUT_DIR)
	CGO_ENABLED=0 go build -ldflags "$(LDFLAGS)" -o $(OUTPUT_DIR)/$(BINARY) .

build-all:
	@mkdir -p $(OUTPUT_DIR)
	GOOS=darwin  GOARCH=amd64 CGO_ENABLED=0 go build -ldflags "$(LDFLAGS)" -o $(OUTPUT_DIR)/$(BINARY)-darwin-amd64 .
	GOOS=darwin  GOARCH=arm64 CGO_ENABLED=0 go build -ldflags "$(LDFLAGS)" -o $(OUTPUT_DIR)/$(BINARY)-darwin-arm64 .
	GOOS=linux   GOARCH=amd64 CGO_ENABLED=0 go build -ldflags "$(LDFLAGS)" -o $(OUTPUT_DIR)/$(BINARY)-linux-amd64 .
	GOOS=linux   GOARCH=arm64 CGO_ENABLED=0 go build -ldflags "$(LDFLAGS)" -o $(OUTPUT_DIR)/$(BINARY)-linux-arm64 .
	GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -ldflags "$(LDFLAGS)" -o $(OUTPUT_DIR)/$(BINARY)-windows-amd64.exe .

install:
	CGO_ENABLED=0 go install -ldflags "$(LDFLAGS)" .

clean:
	rm -rf $(OUTPUT_DIR)/
	go clean -cache

test:
	go test -v -race -count=1 -coverprofile=coverage.out ./...

test-short:
	go test -v -short -count=1 ./...

cover: test
	go tool cover -html=coverage.out -o coverage.html

lint:
	go vet ./...
	@which golangci-lint > /dev/null 2>&1 && golangci-lint run ./... || echo "golangci-lint not installed, skipping"

tidy:
	go mod tidy
	go mod verify

completions:
	@mkdir -p completions
	go run . completion bash > completions/cloudpool.bash
	go run . completion zsh > completions/cloudpool.zsh
	go run . completion fish > completions/cloudpool.fish
	go run . completion powershell > completions/cloudpool.ps1

release: tidy build-all completions

dev:
	go run . --base-url http://localhost:8080
