package commands

import (
	"fmt"
	"strconv"
	"strings"

	"github.com/spf13/cobra"
)

var apiKeysCmd = &cobra.Command{
	Use:   "api-keys",
	Short: "Manage API keys",
	Long: `Create, list, delete, and analyze API keys.

API keys can be used instead of JWT tokens for programmatic access.
They have configurable expiration and usage limits.`,
	Aliases: []string{"keys", "apikeys"},
}

var apiKeysListCmd = &cobra.Command{
	Use:   "list",
	Short: "List all API keys",
	Args:  cobra.NoArgs,
	RunE: func(cmd *cobra.Command, args []string) error {
		data, err := GetContext(cmd).Client.Get(cmd.Context(), "/api/keys", nil)
		if err != nil {
			return err
		}
		if GetContext(cmd).Printer.IsJSON() || GetContext(cmd).Printer.IsYAML() {
			var v interface{}
			if err := unmarshalOrFatal(cmd, data, &v); err != nil { return err }
			GetContext(cmd).Printer.Print(v)
			return nil
		}
		var keys []struct {
			ID        string `json:"id"`
			Name      string `json:"name"`
			KeyPrefix string `json:"keyPrefix"`
			Active    bool   `json:"active"`
			ExpiresAt string `json:"expiresAt"`
		}
		if err := mustUnmarshal(data, &keys); err != nil { return err }
		rows := make([][]string, 0)
		for _, k := range keys {
			activeStr := "✓"
			if !k.Active {
				activeStr = "✗"
			}
			rows = append(rows, []string{trunc(k.ID, 8), k.Name, k.KeyPrefix + "...", activeStr, trunc(k.ExpiresAt, 10)})
		}
		GetContext(cmd).Printer.PrintTable([]string{"ID", "Name", "Prefix", "Active", "Expires"}, rows)
		return nil
	},
}

var apiKeysGenerateCmd = &cobra.Command{
	Use:   "generate [name]",
	Short: "Generate a new API key",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		desc, _ := cmd.Flags().GetString("description")
		ttl, _ := cmd.Flags().GetInt("ttl")
		if ttl <= 0 {
			ttl = 90
		}
		data, err := GetContext(cmd).Client.Post(cmd.Context(), "/api/keys/generate", map[string]interface{}{
			"name": args[0], "description": desc, "daysToLive": ttl,
		}, nil)
		if err != nil {
			return err
		}
		if GetContext(cmd).Printer.IsJSON() || GetContext(cmd).Printer.IsYAML() {
			var v interface{}
			if err := unmarshalOrFatal(cmd, data, &v); err != nil { return err }
			GetContext(cmd).Printer.Print(v)
			return nil
		}
		var resp struct {
			ID        string `json:"id"`
			Name      string `json:"name"`
			Key       string `json:"key"`
			ExpiresAt string `json:"expiresAt"`
		}
		if err := mustUnmarshal(data, &resp); err != nil { return err }
		GetContext(cmd).Printer.Success("API key generated: %s", resp.Name)
		GetContext(cmd).Printer.Detail("Key", resp.Key)
		GetContext(cmd).Printer.Detail("Expires", resp.ExpiresAt)
		GetContext(cmd).Printer.Warning("This key will not be shown again. Store it securely.")
		return nil
	},
}

var apiKeysDeleteCmd = &cobra.Command{
	Use:   "delete [key-id]",
	Short: "Delete an API key",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		path := "/api/keys/" + args[0]
		if !looksLikeUUID(args[0]) {
			path = "/api/keys/" + args[0]
		}
		_, err := GetContext(cmd).Client.Delete(cmd.Context(), path)
		if err != nil {
			return err
		}
		GetContext(cmd).Printer.Success("API key deleted")
		return nil
	},
}

var apiKeysAnalyticsCmd = &cobra.Command{
	Use:   "analytics",
	Short: "Show API key usage analytics",
	Args:  cobra.NoArgs,
	RunE: func(cmd *cobra.Command, args []string) error {
		data, err := GetContext(cmd).Client.Get(cmd.Context(), "/api/keys/analytics/by-key", nil)
		if err != nil {
			return err
		}
		if GetContext(cmd).Printer.IsJSON() || GetContext(cmd).Printer.IsYAML() {
			var v interface{}
			if err := unmarshalOrFatal(cmd, data, &v); err != nil { return err }
			GetContext(cmd).Printer.Print(v)
			return nil
		}
		var stats []struct {
			KeyName  string  `json:"keyName"`
			Requests int     `json:"totalRequests"`
			Success  int     `json:"successCount"`
			Errors   int     `json:"errorCount"`
			AvgMs    float64 `json:"avgResponseTimeMs"`
		}
		if err := mustUnmarshal(data, &stats); err != nil { return err }
		rows := make([][]string, 0)
		for _, s := range stats {
			rows = append(rows, []string{
				s.KeyName,
				strconv.Itoa(s.Requests),
				strconv.Itoa(s.Success),
				strconv.Itoa(s.Errors),
				fmt.Sprintf("%.1fms", s.AvgMs),
			})
		}
		GetContext(cmd).Printer.PrintTable([]string{"Key", "Requests", "Success", "Errors", "Avg Time"}, rows)
		return nil
	},
}

func init() {
	apiKeysCmd.AddCommand(apiKeysListCmd)
	apiKeysCmd.AddCommand(apiKeysGenerateCmd)
	apiKeysCmd.AddCommand(apiKeysDeleteCmd)
	apiKeysCmd.AddCommand(apiKeysAnalyticsCmd)
	apiKeysGenerateCmd.Flags().StringP("description", "d", "", "Human-readable description")
	apiKeysGenerateCmd.Flags().IntP("ttl", "t", 90, "Days until expiration")
}

func trunc(s string, n int) string {
	if len(s) <= n {
		return s
	}
	return s[:n] + "..."
}

func looksLikeUUID(s string) bool {
	return len(s) == 36 && strings.Count(s, "-") == 4
}
