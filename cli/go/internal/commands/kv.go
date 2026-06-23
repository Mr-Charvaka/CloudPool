package commands

import (
	"github.com/spf13/cobra"
)

var kvCmd = &cobra.Command{
	Use:   "kv",
	Short: "Key-value store",
	Long: `Distributed key-value storage with optional TTL.

Supports string values and automatic expiration.`,
	Aliases: []string{"keyvalue", "keystore"},
}

var kvSetCmd = &cobra.Command{
	Use:   "set [key] [value]",
	Short: "Set a key-value pair",
	Args:  cobra.ExactArgs(2),
	RunE: func(cmd *cobra.Command, args []string) error {
		ttl, _ := cmd.Flags().GetInt("ttl")
		body := map[string]interface{}{"value": args[1]}
		if ttl > 0 {
			body["ttl"] = ttl
		}
		_, err := GetContext(cmd).Client.Post(cmd.Context(), "/api/kv/"+args[0], body, nil)
		if err != nil {
			return err
		}
		GetContext(cmd).Printer.Success("Set: %s", args[0])
		return nil
	},
}

var kvGetCmd = &cobra.Command{
	Use:   "get [key]",
	Short: "Get a value by key",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		data, err := GetContext(cmd).Client.Get(cmd.Context(), "/api/kv/"+args[0], nil)
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
			Key   string `json:"key"`
			Value string `json:"value"`
		}
		if err := mustUnmarshal(data, &resp); err != nil { return err }
		GetContext(cmd).Printer.Info("Key: %s", args[0])
		GetContext(cmd).Printer.Detail("Value", resp.Value)
		return nil
	},
}

var kvListCmd = &cobra.Command{
	Use:   "list",
	Short: "List all key-value entries",
	Args:  cobra.NoArgs,
	RunE: func(cmd *cobra.Command, args []string) error {
		data, err := GetContext(cmd).Client.Get(cmd.Context(), "/api/kv", nil)
		if err != nil {
			return err
		}
		if GetContext(cmd).Printer.IsJSON() || GetContext(cmd).Printer.IsYAML() {
			var v interface{}
			if err := unmarshalOrFatal(cmd, data, &v); err != nil { return err }
			GetContext(cmd).Printer.Print(v)
			return nil
		}
		var entries []struct {
			Key   string `json:"key"`
			Value string `json:"value"`
			TTL   string `json:"ttl"`
		}
		if err := mustUnmarshal(data, &entries); err != nil { return err }
		rows := make([][]string, 0)
		for _, e := range entries {
			rows = append(rows, []string{e.Key, trunc(e.Value, 40), e.TTL})
		}
		GetContext(cmd).Printer.PrintTable([]string{"Key", "Value", "TTL"}, rows)
		return nil
	},
}

var kvDeleteCmd = &cobra.Command{
	Use:   "delete [key]",
	Short: "Delete a key-value entry",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		_, err := GetContext(cmd).Client.Delete(cmd.Context(), "/api/kv/"+args[0])
		if err != nil {
			return err
		}
		GetContext(cmd).Printer.Success("Deleted key %s", args[0])
		return nil
	},
}

func init() {
	kvCmd.AddCommand(kvSetCmd)
	kvCmd.AddCommand(kvGetCmd)
	kvCmd.AddCommand(kvListCmd)
	kvCmd.AddCommand(kvDeleteCmd)
	kvSetCmd.Flags().IntP("ttl", "t", 0, "Time-to-live in seconds (0 = no expiry)")
}
