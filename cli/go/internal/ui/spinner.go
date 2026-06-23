package ui

import (
	"fmt"
	"os"
	"time"

	"golang.org/x/term"
)

type Spinner struct {
	Message string
	stop    chan bool
	done    chan bool
}

func NewSpinner(msg string) *Spinner {
	return &Spinner{Message: msg, stop: make(chan bool), done: make(chan bool)}
}

func (s *Spinner) Start() {
	if !term.IsTerminal(int(os.Stderr.Fd())) {
		fmt.Fprintln(os.Stderr, s.Message+"...")
		s.done <- true
		return
	}
	go func() {
		frames := []string{"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"}
		i := 0
		for {
			select {
			case <-s.stop:
				fmt.Fprintf(os.Stderr, "\r%s ✓\n", s.Message)
				s.done <- true
				return
			default:
				fmt.Fprintf(os.Stderr, "\r%s %s", frames[i%len(frames)], s.Message)
				i++
				time.Sleep(80 * time.Millisecond)
			}
		}
	}()
}

func (s *Spinner) Stop() {
	s.stop <- true
	<-s.done
}
