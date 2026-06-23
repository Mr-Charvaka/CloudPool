FROM golang:1.22-alpine AS builder

RUN apk add --no-cache git ca-certificates

WORKDIR /build
COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 go build \
    -ldflags "-s -w -X github.com/cloudpool/cli/pkg/version.Version=$(git describe --tags --always --dirty 2>/dev/null || echo dev)" \
    -o /build/cloudpool .

FROM alpine:3.19
RUN apk add --no-cache ca-certificates tzdata
COPY --from=builder /build/cloudpool /usr/local/bin/cloudpool
ENTRYPOINT ["cloudpool"]
CMD ["--help"]
