package commands

import (
	"fmt"
	"os"

	"github.com/spf13/cobra"
)

var computeCmd = &cobra.Command{
	Use:   "compute",
	Short: "Serverless functions & cron jobs",
	Long: `Deploy and manage serverless functions and cron jobs.

Supports multiple runtimes (Node.js, Python, Go, Rust).
Functions can be triggered via HTTP, cron schedule, or events.`,
	Aliases: []string{"fn", "serverless"},
}

var computeServerlessCmd = &cobra.Command{
	Use:   "serverless [name] [file]",
	Short: "Deploy a serverless function from a file",
	Args:  cobra.ExactArgs(2),
	RunE: func(cmd *cobra.Command, args []string) error {
		runtime, _ := cmd.Flags().GetString("runtime")
		if runtime == "" {
			runtime = "node18"
		}
		data, err := GetContext(cmd).Client.Post(cmd.Context(), "/api/compute/serverless", map[string]interface{}{
			"name": args[0], "runtime": runtime,
		}, nil)
		if err != nil {
			return fmt.Errorf("create function: %w", err)
		}
		_, err = GetContext(cmd).Client.Upload(cmd.Context(), "/api/compute/serverless/"+args[0]+"/code", args[1], "serverless")
		if err != nil {
			return fmt.Errorf("upload code: %w", err)
		}
		if GetContext(cmd).Printer.IsJSON() || GetContext(cmd).Printer.IsYAML() {
			var v interface{}
			if err := unmarshalOrFatal(cmd, data, &v); err != nil { return err }
			GetContext(cmd).Printer.Print(v)
			return nil
		}
		var resp struct {
			ID     string `json:"id"`
			Name   string `json:"name"`
			Status string `json:"status"`
			URL    string `json:"url"`
		}
		if err := mustUnmarshal(data, &resp); err != nil { return err }
		GetContext(cmd).Printer.Success("Deployed: %s", resp.Name)
		GetContext(cmd).Printer.Detail("ID", resp.ID)
		GetContext(cmd).Printer.Detail("Status", resp.Status)
		GetContext(cmd).Printer.Detail("URL", resp.URL)
		return nil
	},
}

var computeCronCmd = &cobra.Command{
	Use:   "cron [name] [schedule]",
	Short: "Create a cron job",
	Long: `Create a cron job with a cron expression.

Example:
  cloudpool compute cron my-job "0 */6 * * *"`,
	Args: cobra.ExactArgs(2),
	RunE: func(cmd *cobra.Command, args []string) error {
		code, _ := cmd.Flags().GetString("code")
		body := map[string]interface{}{
			"name": args[0], "schedule": args[1],
		}
		if code != "" {
			c, err := os.ReadFile(code)
			if err != nil {
				return fmt.Errorf("read code file: %w", err)
			}
			body["code"] = string(c)
		}
		data, err := GetContext(cmd).Client.Post(cmd.Context(), "/api/compute/cron", body, nil)
		if err != nil {
			return fmt.Errorf("create cron: %w", err)
		}
		if GetContext(cmd).Printer.IsJSON() || GetContext(cmd).Printer.IsYAML() {
			var v interface{}
			if err := unmarshalOrFatal(cmd, data, &v); err != nil { return err }
			GetContext(cmd).Printer.Print(v)
			return nil
		}
		var resp struct {
			ID       string `json:"id"`
			Name     string `json:"name"`
			Schedule string `json:"schedule"`
		}
		if err := mustUnmarshal(data, &resp); err != nil { return err }
		GetContext(cmd).Printer.Success("Cron created: %s (%s)", resp.Name, resp.Schedule)
		GetContext(cmd).Printer.Detail("ID", resp.ID)
		return nil
	},
}

var computeDeployCmd = &cobra.Command{
	Use:   "deploy [directory]",
	Short: "Deploy a project directory",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		info, err := os.Stat(args[0])
		if err != nil {
			return fmt.Errorf("path error: %w", err)
		}
		if !info.IsDir() {
			return fmt.Errorf("expected a directory, got file")
		}
		data, err := GetContext(cmd).Client.Post(cmd.Context(), "/api/compute/deploy", map[string]string{
			"path": args[0],
		}, nil)
		if err != nil {
			return fmt.Errorf("deploy failed: %w", err)
		}
		var v interface{}
		if err := unmarshalOrFatal(cmd, data, &v); err != nil { return err }
		GetContext(cmd).Printer.Print(v)
		return nil
	},
}

var computeLogsCmd = &cobra.Command{
	Use:   "logs [function-id]",
	Short: "Show function execution logs",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		tail, _ := cmd.Flags().GetInt("tail")
		data, err := GetContext(cmd).Client.Get(cmd.Context(), "/api/compute/"+args[0]+"/logs", map[string]string{
			"tail": fmt.Sprintf("%d", tail),
		})
		if err != nil {
			return err
		}
		if GetContext(cmd).Printer.IsJSON() || GetContext(cmd).Printer.IsYAML() {
			var v interface{}
			if err := unmarshalOrFatal(cmd, data, &v); err != nil { return err }
			GetContext(cmd).Printer.Print(v)
			return nil
		}
		var logs []struct {
			Timestamp string `json:"timestamp"`
			Level     string `json:"level"`
			Message   string `json:"message"`
		}
		if err := mustUnmarshal(data, &logs); err != nil { return err }
		rows := make([][]string, 0)
		for _, l := range logs {
			rows = append(rows, []string{trunc(l.Timestamp, 19), l.Level, trunc(l.Message, 80)})
		}
		GetContext(cmd).Printer.PrintTable([]string{"Timestamp", "Level", "Message"}, rows)
		return nil
	},
}

var computePodsCmd = &cobra.Command{
	Use:   "pods",
	Short: "List active compute pods",
	Args:  cobra.NoArgs,
	RunE: func(cmd *cobra.Command, args []string) error {
		data, err := GetContext(cmd).Client.Get(cmd.Context(), "/api/compute/pods", nil)
		if err != nil {
			return err
		}
		if GetContext(cmd).Printer.IsJSON() || GetContext(cmd).Printer.IsYAML() {
			var v interface{}
			if err := unmarshalOrFatal(cmd, data, &v); err != nil { return err }
			GetContext(cmd).Printer.Print(v)
			return nil
		}
		var pods []struct {
			ID     string `json:"id"`
			Image  string `json:"image"`
			Status string `json:"status"`
			CPU    string `json:"cpu"`
			Mem    string `json:"memory"`
		}
		if err := mustUnmarshal(data, &pods); err != nil { return err }
		rows := make([][]string, 0)
		for _, p := range pods {
			rows = append(rows, []string{trunc(p.ID, 8), p.Image, p.Status, p.CPU, p.Mem})
		}
		GetContext(cmd).Printer.PrintTable([]string{"ID", "Image", "Status", "CPU", "Memory"}, rows)
		return nil
	},
}

func init() {
	computeCmd.AddCommand(computeServerlessCmd)
	computeCmd.AddCommand(computeCronCmd)
	computeCmd.AddCommand(computeDeployCmd)
	computeCmd.AddCommand(computeLogsCmd)
	computeCmd.AddCommand(computePodsCmd)
	computeServerlessCmd.Flags().StringP("runtime", "r", "node18", "Runtime (node18, python3, go, rust)")
	computeCronCmd.Flags().String("code", "", "Path to code file")
	computeLogsCmd.Flags().IntP("tail", "t", 50, "Number of recent log lines")
}
