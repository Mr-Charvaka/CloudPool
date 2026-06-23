package commands

import (
	"fmt"

	"github.com/spf13/cobra"
)

var networkCmd = &cobra.Command{
	Use:   "network",
	Short: "Network tunnels, pubsub, and domains",
	Long: `Manage tunnels, pub/sub messaging, custom domains,
and gateway authentication.`,
	Aliases: []string{"net", "tunnel"},
}

var networkTunnelCmd = &cobra.Command{
	Use:   "tunnel [start|stop|status]",
	Short: "Manage network tunnels",
	Long: `Start, stop, or check the status of a network tunnel.

Examples:
  cloudpool network tunnel start 3000
  cloudpool network tunnel start 8080 --subdomain my-app
  cloudpool network tunnel stop
  cloudpool network tunnel status`,
	Args: cobra.MinimumNArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		action := args[0]
		subdomain, _ := cmd.Flags().GetString("subdomain")

		switch action {
		case "start":
			port := "80"
			if len(args) > 1 {
				port = args[1]
			}
			data, err := GetContext(cmd).Client.Post(cmd.Context(), "/api/network/tunnel/start", map[string]interface{}{
				"port": port, "subdomain": subdomain,
			}, nil)
			if err != nil {
				return fmt.Errorf("tunnel start failed: %w", err)
			}
			if GetContext(cmd).Printer.IsJSON() || GetContext(cmd).Printer.IsYAML() {
				var v interface{}
				if err := unmarshalOrFatal(cmd, data, &v); err != nil { return err }
				GetContext(cmd).Printer.Print(v)
				return nil
			}
			GetContext(cmd).Printer.Success("Tunnel started on port %s", port)
			var resp struct {
				URL       string `json:"url"`
				ExpiresAt string `json:"expiresAt"`
			}
			if err := mustUnmarshal(data, &resp); err != nil {
				return err
			}
			GetContext(cmd).Printer.Detail("URL", resp.URL)
			if resp.ExpiresAt != "" {
				GetContext(cmd).Printer.Detail("Expires", resp.ExpiresAt)
			}
		case "stop":
			GetContext(cmd).Client.Post(cmd.Context(), "/api/network/tunnel/stop", nil, nil)
			GetContext(cmd).Printer.Success("Tunnel stopped")
		case "status":
			data, err := GetContext(cmd).Client.Get(cmd.Context(), "/api/network/tunnel/status", nil)
			if err != nil {
				return err
			}
			if GetContext(cmd).Printer.IsJSON() || GetContext(cmd).Printer.IsYAML() {
				var v interface{}
				if err := unmarshalOrFatal(cmd, data, &v); err != nil { return err }
				GetContext(cmd).Printer.Print(v)
				return nil
			}
			var s struct {
				Active    bool   `json:"active"`
				LocalPort string `json:"localPort"`
				RemoteURL string `json:"remoteUrl"`
				StartedAt string `json:"startedAt"`
			}
			if err := mustUnmarshal(data, &s); err != nil {
				return err
			}
			GetContext(cmd).Printer.Info("Tunnel Status")
			GetContext(cmd).Printer.Detail("Active", fmt.Sprintf("%v", s.Active))
			GetContext(cmd).Printer.Detail("Local Port", s.LocalPort)
			GetContext(cmd).Printer.Detail("Remote URL", s.RemoteURL)
			GetContext(cmd).Printer.Detail("Started", s.StartedAt)
		default:
			return fmt.Errorf("unknown action: %s (use: start, stop, status)", action)
		}
		return nil
	},
}

var networkPubsubCmd = &cobra.Command{
	Use:   "pubsub [broadcast|subscribe] [channel]",
	Short: "PubSub messaging operations",
	Long: `Broadcast messages to a channel or subscribe for real-time.

Examples:
  cloudpool network pubsub broadcast my-channel "hello world"
  cloudpool network pubsub subscribe my-channel`,
	Args: cobra.MinimumNArgs(2),
	RunE: func(cmd *cobra.Command, args []string) error {
		action := args[0]
		channel := args[1]
		switch action {
		case "broadcast":
			message := ""
			if len(args) > 2 {
				message = args[2]
			} else {
				return fmt.Errorf("message body required for broadcast")
			}
			data, err := GetContext(cmd).Client.Post(cmd.Context(), "/api/pubsub/broadcast", map[string]string{
				"channel": channel, "message": message,
			}, nil)
			if err != nil {
				return err
			}
			var v interface{}
			if err := unmarshalOrFatal(cmd, data, &v); err != nil { return err }
			GetContext(cmd).Printer.Print(v)
			GetContext(cmd).Printer.Success("Message broadcast to %s", channel)
		case "subscribe":
			GetContext(cmd).Printer.Warning("Use WebSocket at /api/pubsub/ws/%s for real-time", channel)
		default:
			return fmt.Errorf("unknown action: %s (use: broadcast, subscribe)", action)
		}
		return nil
	},
}

var networkDomainsCmd = &cobra.Command{
	Use:   "domains",
	Short: "List custom domains",
	Args:  cobra.NoArgs,
	RunE: func(cmd *cobra.Command, args []string) error {
		data, err := GetContext(cmd).Client.Get(cmd.Context(), "/api/network/domains", nil)
		if err != nil {
			return err
		}
		if GetContext(cmd).Printer.IsJSON() || GetContext(cmd).Printer.IsYAML() {
			var v interface{}
			if err := unmarshalOrFatal(cmd, data, &v); err != nil { return err }
			GetContext(cmd).Printer.Print(v)
			return nil
		}
		var domains []struct {
			Domain string `json:"domain"`
			Status string `json:"status"`
		}
		if err := mustUnmarshal(data, &domains); err != nil {
			return err
		}
		rows := make([][]string, 0)
		for _, d := range domains {
			rows = append(rows, []string{d.Domain, d.Status})
		}
		GetContext(cmd).Printer.PrintTable([]string{"Domain", "Status"}, rows)
		return nil
	},
}

var networkAuthCmd = &cobra.Command{
	Use:   "gateway-auth [register|list|delete]",
	Short: "Manage gateway authentication",
	Args:  cobra.MinimumNArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		action := args[0]
		switch action {
		case "register":
			if len(args) < 3 {
				return fmt.Errorf("usage: network gateway-auth register <email> <password>")
			}
			data, err := GetContext(cmd).Client.Post(cmd.Context(), "/api/gateway-auth/register", map[string]string{
				"email": args[1], "password": args[2],
			}, nil)
			if err != nil {
				return err
			}
			var v interface{}
			if err := unmarshalOrFatal(cmd, data, &v); err != nil { return err }
			GetContext(cmd).Printer.Print(v)
			GetContext(cmd).Printer.Success("Gateway auth registered: %s", args[1])
		case "list":
			data, err := GetContext(cmd).Client.Get(cmd.Context(), "/api/gateway-auth/list", nil)
			if err != nil {
				return err
			}
			if GetContext(cmd).Printer.IsJSON() || GetContext(cmd).Printer.IsYAML() {
				var v interface{}
				if err := unmarshalOrFatal(cmd, data, &v); err != nil { return err }
				GetContext(cmd).Printer.Print(v)
				return nil
			}
			var users []struct {
				Email string `json:"email"`
				Role  string `json:"role"`
			}
			if err := mustUnmarshal(data, &users); err != nil {
				return err
			}
			rows := make([][]string, 0)
			for _, u := range users {
				rows = append(rows, []string{u.Email, u.Role})
			}
			GetContext(cmd).Printer.PrintTable([]string{"Email", "Role"}, rows)
		case "delete":
			if len(args) < 2 {
				return fmt.Errorf("usage: network gateway-auth delete <email>")
			}
			_, err := GetContext(cmd).Client.Delete(cmd.Context(), "/api/gateway-auth/" + args[1])
			if err != nil {
				return err
			}
			GetContext(cmd).Printer.Success("Deleted Gateway Auth Profile %s", args[1])
		default:
			return fmt.Errorf("unknown action: %s (use: register, list, delete)", action)
		}
		return nil
	},
}

var networkBackupsCmd = &cobra.Command{
	Use:   "backups",
	Short: "List gateway backups",
	Args:  cobra.NoArgs,
	RunE: func(cmd *cobra.Command, args []string) error {
		data, err := GetContext(cmd).Client.Get(cmd.Context(), "/api/gateway/backups", nil)
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
	networkCmd.AddCommand(networkTunnelCmd)
	networkCmd.AddCommand(networkPubsubCmd)
	networkCmd.AddCommand(networkDomainsCmd)
	networkCmd.AddCommand(networkAuthCmd)
	networkCmd.AddCommand(networkBackupsCmd)
	networkTunnelCmd.Flags().StringP("subdomain", "s", "", "Custom subdomain")
}
