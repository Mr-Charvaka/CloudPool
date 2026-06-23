package output

import (
	"bytes"
	"strings"
	"testing"
)

func TestParseFormat(t *testing.T) {
	tests := []struct {
		input string
		want  Format
	}{
		{"json", FormatJSON},
		{"JSON", FormatJSON},
		{"yaml", FormatYAML},
		{"yml", FormatYAML},
		{"table", FormatTable},
		{"text", FormatText},
		{"unknown", FormatTable},
		{"", FormatTable},
	}
	for _, tt := range tests {
		got := ParseFormat(tt.input)
		if got != tt.want {
			t.Errorf("ParseFormat(%q) = %v, want %v", tt.input, got, tt.want)
		}
	}
}

func TestPrintSuccess(t *testing.T) {
	var buf bytes.Buffer
	p := New(FormatText)
	p.Stderr = &buf
	p.Color = false

	p.Success("Hello %s", "world")
	if !strings.Contains(buf.String(), "✓ Hello world") {
		t.Errorf("unexpected output: %s", buf.String())
	}
}

func TestPrintJSON(t *testing.T) {
	var buf bytes.Buffer
	p := New(FormatJSON)
	p.Stdout = &buf
	p.PrintJSON(map[string]string{"key": "value"})

	if !strings.Contains(buf.String(), `"key": "value"`) {
		t.Errorf("unexpected json output: %s", buf.String())
	}
}

func TestPrintError(t *testing.T) {
	var buf bytes.Buffer
	p := New(FormatText)
	p.Stderr = &buf
	p.Color = false
	p.Error("something went wrong")
	if !strings.Contains(buf.String(), "✗ something went wrong") {
		t.Errorf("unexpected error output: %s", buf.String())
	}
}

func TestPrintTableJSON(t *testing.T) {
	var buf bytes.Buffer
	p := New(FormatJSON)
	p.Stdout = &buf
	p.PrintTable([]string{"Name", "Age"}, [][]string{{"Alice", "30"}, {"Bob", "25"}})

	if !strings.Contains(buf.String(), `"Name": "Alice"`) {
		t.Errorf("unexpected table-as-json output: %s", buf.String())
	}
}
