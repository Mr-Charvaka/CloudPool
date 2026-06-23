package commands

import (
	"github.com/spf13/cobra"
)

var emailsCmd = &cobra.Command{
	Use:   "emails",
	Short: "Send emails & view inbox",
	Long: `Send transactional emails and view the inbox.

Each CloudPool account comes with a built-in email service.`,
	Aliases: []string{"email", "mail"},
}

var emailsSendCmd = &cobra.Command{
	Use:   "send [to] [subject] [body]",
	Short: "Send an email",
	Args:  cobra.ExactArgs(3),
	RunE: func(cmd *cobra.Command, args []string) error {
		data, err := GetContext(cmd).Client.Post(cmd.Context(), "/api/emails/send", map[string]string{
			"to": args[0], "subject": args[1], "body": args[2],
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
			ID     string `json:"id"`
			Status string `json:"status"`
		}
		if err := mustUnmarshal(data, &resp); err != nil { return err }
		GetContext(cmd).Printer.Success("Email sent to %s (status: %s)", args[0], resp.Status)
		return nil
	},
}

var emailsInboxCmd = &cobra.Command{
	Use:   "inbox",
	Short: "View received emails",
	Args:  cobra.NoArgs,
	RunE: func(cmd *cobra.Command, args []string) error {
		data, err := GetContext(cmd).Client.Get(cmd.Context(), "/api/emails/inbox", nil)
		if err != nil {
			return err
		}
		if GetContext(cmd).Printer.IsJSON() || GetContext(cmd).Printer.IsYAML() {
			var v interface{}
			if err := unmarshalOrFatal(cmd, data, &v); err != nil { return err }
			GetContext(cmd).Printer.Print(v)
			return nil
		}
		var msgs []struct {
			ID       string `json:"id"`
			From     string `json:"from"`
			Subject  string `json:"subject"`
			Read     bool   `json:"read"`
			Received string `json:"receivedAt"`
		}
		if err := mustUnmarshal(data, &msgs); err != nil { return err }
		rows := make([][]string, 0)
		for _, m := range msgs {
			readStr := "✓"
			if !m.Read {
				readStr = "○"
			}
			rows = append(rows, []string{
				trunc(m.ID, 8), m.From, trunc(m.Subject, 40), readStr, trunc(m.Received, 10),
			})
		}
		GetContext(cmd).Printer.PrintTable([]string{"ID", "From", "Subject", "Read", "Received"}, rows)
		return nil
	},
}

func init() {
	emailsCmd.AddCommand(emailsSendCmd)
	emailsCmd.AddCommand(emailsInboxCmd)
}
