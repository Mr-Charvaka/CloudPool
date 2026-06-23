package commands

import (
	"github.com/spf13/cobra"
)

var healthCmd = &cobra.Command{
	Use:   "health",
	Short: "Check system health",
	Long: `Check the health status of all CloudPool services.

Returns the status of gateway, data, auth, compute, network,
and vector database services.`,
	Aliases: []string{"status", "ping"},
}

var healthCheckCmd = &cobra.Command{
	Use:   "check",
	Short: "Perform a full health check",
	Args:  cobra.NoArgs,
	RunE: func(cmd *cobra.Command, args []string) error {
		data, err := GetContext(cmd).Client.Get(cmd.Context(), "/api/health", nil)
		if err != nil {
			return err
		}
		if GetContext(cmd).Printer.IsJSON() || GetContext(cmd).Printer.IsYAML() {
			var v interface{}
			if err := unmarshalOrFatal(cmd, data, &v); err != nil { return err }
			GetContext(cmd).Printer.Print(v)
			return nil
		}
		var h struct {
			Status   string `json:"status"`
			Gateway  string `json:"gateway"`
			Data     string `json:"data"`
			Auth     string `json:"auth"`
			Compute  string `json:"compute"`
			Network  string `json:"network"`
			Weaviate string `json:"weaviate"`
		}
		if err := mustUnmarshal(data, &h); err != nil { return err }
		GetContext(cmd).Printer.Info("System Health")
		GetContext(cmd).Printer.Detail("Overall", healthBadge(h.Status))
		GetContext(cmd).Printer.Detail("Gateway", healthBadge(h.Gateway))
		GetContext(cmd).Printer.Detail("Data", healthBadge(h.Data))
		GetContext(cmd).Printer.Detail("Auth", healthBadge(h.Auth))
		GetContext(cmd).Printer.Detail("Compute", healthBadge(h.Compute))
		GetContext(cmd).Printer.Detail("Network", healthBadge(h.Network))
		GetContext(cmd).Printer.Detail("Weaviate", healthBadge(h.Weaviate))
		return nil
	},
}

func healthBadge(s string) string {
	switch s {
	case "UP", "up", "healthy", "true":
		return "✓ UP"
	case "DOWN", "down", "unhealthy", "false":
		return "✗ DOWN"
	default:
		return "? " + s
	}
}

var healthServicesCmd = &cobra.Command{
	Use:   "services",
	Short: "List all registered services",
	Args:  cobra.NoArgs,
	RunE: func(cmd *cobra.Command, args []string) error {
		data, err := GetContext(cmd).Client.Get(cmd.Context(), "/api/gateway/services", nil)
		if err != nil {
			return err
		}
		var v interface{}
		if err := unmarshalOrFatal(cmd, data, &v); err != nil { return err }
		GetContext(cmd).Printer.Print(v)
		return nil
	},
}

func init() {
	healthCmd.AddCommand(healthCheckCmd)
	healthCmd.AddCommand(healthServicesCmd)
}
