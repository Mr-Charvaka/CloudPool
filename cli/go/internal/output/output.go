package output

import (
	"encoding/json"
	"fmt"
	"io"
	"os"
	"strings"

	"github.com/fatih/color"
	"github.com/rodaine/table"
	"gopkg.in/yaml.v3"
)

type Format int

const (
	FormatTable Format = iota
	FormatJSON
	FormatYAML
	FormatText
)

func (f Format) String() string {
	switch f {
	case FormatTable:
		return "table"
	case FormatJSON:
		return "json"
	case FormatYAML:
		return "yaml"
	case FormatText:
		return "text"
	}
	return "table"
}

func ParseFormat(s string) Format {
	switch strings.ToLower(s) {
	case "json":
		return FormatJSON
	case "yaml", "yml":
		return FormatYAML
	case "text":
		return FormatText
	case "table":
		return FormatTable
	}
	return FormatTable
}

type Printer struct {
	Format Format
	Stdout io.Writer
	Stderr io.Writer
	Color  bool
}

func New(f Format) *Printer {
	return &Printer{
		Format: f,
		Stdout: os.Stdout,
		Stderr: os.Stderr,
		Color:  true,
	}
}

func (p *Printer) PrintJSON(v interface{}) {
	data, err := json.MarshalIndent(v, "", "  ")
	if err != nil {
		fmt.Fprintf(p.Stderr, "json error: %v\n", err)
		return
	}
	fmt.Fprintln(p.Stdout, string(data))
}

func (p *Printer) PrintYAML(v interface{}) {
	data, err := yaml.Marshal(v)
	if err != nil {
		fmt.Fprintf(p.Stderr, "yaml error: %v\n", err)
		return
	}
	fmt.Fprintln(p.Stdout, string(data))
}

func (p *Printer) PrintTable(headers []string, rows [][]string) {
	if p.Format == FormatJSON {
		records := make([]map[string]string, len(rows))
		for i, row := range rows {
			m := make(map[string]string)
			for j, h := range headers {
				if j < len(row) {
					m[h] = row[j]
				}
			}
			records[i] = m
		}
		p.PrintJSON(records)
		return
	}
	if p.Format == FormatYAML {
		records := make([]map[string]string, len(rows))
		for i, row := range rows {
			m := make(map[string]string)
			for j, h := range headers {
				if j < len(row) {
					m[h] = row[j]
				}
			}
			records[i] = m
		}
		p.PrintYAML(records)
		return
	}
	headerObjs := make([]interface{}, len(headers))
	for i, h := range headers {
		headerObjs[i] = h
	}
	tbl := table.New(headerObjs...)
	tbl.WithHeaderFormatter(func(s string) string {
		if p.Color {
			return color.New(color.FgCyan, color.Bold).Sprint(s)
		}
		return s
	})
	for _, row := range rows {
		rowObjs := make([]interface{}, len(row))
		for i, v := range row {
			rowObjs[i] = v
		}
		tbl.AddRow(rowObjs...)
	}
	fmt.Fprintln(p.Stdout, "")
	tbl.Print()
	fmt.Fprintln(p.Stdout, "")
}

func (p *Printer) Print(v interface{}) {
	switch p.Format {
	case FormatJSON:
		p.PrintJSON(v)
	case FormatYAML:
		p.PrintYAML(v)
	default:
		fmt.Fprintln(p.Stdout, v)
	}
}

func (p *Printer) Success(format string, args ...interface{}) {
	msg := fmt.Sprintf(format, args...)
	if p.Color {
		msg = color.New(color.FgGreen).Sprint("✓ " + msg)
	} else {
		msg = "✓ " + msg
	}
	fmt.Fprintln(p.Stderr, msg)
}

func (p *Printer) Warning(format string, args ...interface{}) {
	msg := fmt.Sprintf(format, args...)
	if p.Color {
		msg = color.New(color.FgYellow).Sprint("! " + msg)
	} else {
		msg = "! " + msg
	}
	fmt.Fprintln(p.Stderr, msg)
}

func (p *Printer) Error(format string, args ...interface{}) {
	msg := fmt.Sprintf(format, args...)
	if p.Color {
		msg = color.New(color.FgRed, color.Bold).Sprint("✗ " + msg)
	} else {
		msg = "✗ " + msg
	}
	fmt.Fprintln(p.Stderr, msg)
}

func (p *Printer) Info(format string, args ...interface{}) {
	msg := fmt.Sprintf(format, args...)
	if p.Color {
		msg = color.New(color.FgBlue).Sprint("ℹ " + msg)
	} else {
		msg = "ℹ " + msg
	}
	fmt.Fprintln(p.Stderr, msg)
}

func (p *Printer) Detail(key, value string) {
	if p.Color {
		fmt.Fprintf(p.Stderr, "  %s: %s\n",
			color.New(color.FgYellow).Sprint(key),
			color.New(color.FgWhite).Sprint(value))
	} else {
		fmt.Fprintf(p.Stderr, "  %s: %s\n", key, value)
	}
}

func (p *Printer) Fatal(format string, a ...interface{}) {
	p.Error(format, a...)
}

func (p *Printer) FatalErr(err error) {
	p.Error("%v", err)
}

func (p *Printer) IsJSON() bool { return p.Format == FormatJSON }
func (p *Printer) IsYAML() bool { return p.Format == FormatYAML }
