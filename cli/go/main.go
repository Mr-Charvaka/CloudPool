package main

import (
	"context"
	"os"
	"os/signal"
	"syscall"

	"github.com/cloudpool/cli/internal/commands"
)

func main() {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	sig := make(chan os.Signal, 1)
	signal.Notify(sig, syscall.SIGINT, syscall.SIGTERM)
	go func() {
		<-sig
		cancel()
	}()

	commands.Execute(ctx)
}
