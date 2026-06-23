package commands

import (
	"fmt"
	"strings"

	"github.com/spf13/cobra"
)

var paymentsCmd = &cobra.Command{
	Use:   "payments",
	Short: "Billing, invoices & usage",
	Long: `View your billing balance, invoices, usage, and pricing plans.

All amounts are in the account's configured currency.`,
	Aliases: []string{"billing", "bill", "invoice"},
}

var paymentsBalanceCmd = &cobra.Command{
	Use:   "balance",
	Short: "Show account balance",
	Args:  cobra.NoArgs,
	RunE: func(cmd *cobra.Command, args []string) error {
		data, err := GetContext(cmd).Client.Get(cmd.Context(), "/api/payments/balance", nil)
		if err != nil {
			return err
		}
		if GetContext(cmd).Printer.IsJSON() || GetContext(cmd).Printer.IsYAML() {
			var v interface{}
			if err := unmarshalOrFatal(cmd, data, &v); err != nil { return err }
			GetContext(cmd).Printer.Print(v)
			return nil
		}
		var b struct {
			Balance  float64 `json:"balance"`
			Pending  float64 `json:"pending"`
			Currency string  `json:"currency"`
		}
		if err := mustUnmarshal(data, &b); err != nil { return err }
		GetContext(cmd).Printer.Info("Account Balance")
		GetContext(cmd).Printer.Detail("Available", fmt.Sprintf("%.2f %s", b.Balance, b.Currency))
		GetContext(cmd).Printer.Detail("Pending", fmt.Sprintf("%.2f %s", b.Pending, b.Currency))
		return nil
	},
}

var paymentsInvoicesCmd = &cobra.Command{
	Use:   "invoices",
	Short: "List invoices",
	Args:  cobra.NoArgs,
	RunE: func(cmd *cobra.Command, args []string) error {
		data, err := GetContext(cmd).Client.Get(cmd.Context(), "/api/payments/invoices", nil)
		if err != nil {
			return err
		}
		if GetContext(cmd).Printer.IsJSON() || GetContext(cmd).Printer.IsYAML() {
			var v interface{}
			if err := unmarshalOrFatal(cmd, data, &v); err != nil { return err }
			GetContext(cmd).Printer.Print(v)
			return nil
		}
		var invoices []struct {
			ID          string  `json:"id"`
			Amount      float64 `json:"amount"`
			Status      string  `json:"status"`
			DueDate     string  `json:"dueDate"`
			Description string  `json:"description"`
		}
		if err := mustUnmarshal(data, &invoices); err != nil { return err }
		rows := make([][]string, 0)
		for _, inv := range invoices {
			rows = append(rows, []string{
				trunc(inv.ID, 8), fmt.Sprintf("$%.2f", inv.Amount), inv.Status, trunc(inv.DueDate, 10), trunc(inv.Description, 30),
			})
		}
		GetContext(cmd).Printer.PrintTable([]string{"ID", "Amount", "Status", "Due", "Description"}, rows)
		return nil
	},
}

var paymentsPlansCmd = &cobra.Command{
	Use:   "plans",
	Short: "List available pricing plans",
	Args:  cobra.NoArgs,
	RunE: func(cmd *cobra.Command, args []string) error {
		data, err := GetContext(cmd).Client.Get(cmd.Context(), "/api/payments/plans", nil)
		if err != nil {
			return err
		}
		if GetContext(cmd).Printer.IsJSON() || GetContext(cmd).Printer.IsYAML() {
			var v interface{}
			if err := unmarshalOrFatal(cmd, data, &v); err != nil { return err }
			GetContext(cmd).Printer.Print(v)
			return nil
		}
		var plans []struct {
			Name     string   `json:"name"`
			Price    float64  `json:"price"`
			Currency string   `json:"currency"`
			Features []string `json:"features"`
		}
		if err := mustUnmarshal(data, &plans); err != nil { return err }
		rows := make([][]string, 0)
		for _, p := range plans {
			rows = append(rows, []string{p.Name, fmt.Sprintf("%.2f %s", p.Price, p.Currency), strings.Join(p.Features, ", ")})
		}
		GetContext(cmd).Printer.PrintTable([]string{"Plan", "Price", "Features"}, rows)
		return nil
	},
}

var paymentsUsageCmd = &cobra.Command{
	Use:   "usage",
	Short: "Show current billing period usage",
	Args:  cobra.NoArgs,
	RunE: func(cmd *cobra.Command, args []string) error {
		data, err := GetContext(cmd).Client.Get(cmd.Context(), "/api/payments/usage", nil)
		if err != nil {
			return err
		}
		if GetContext(cmd).Printer.IsJSON() || GetContext(cmd).Printer.IsYAML() {
			var v interface{}
			if err := unmarshalOrFatal(cmd, data, &v); err != nil { return err }
			GetContext(cmd).Printer.Print(v)
			return nil
		}
		var u struct {
			PeriodStart string             `json:"periodStart"`
			PeriodEnd   string             `json:"periodEnd"`
			Total       float64            `json:"total"`
			Breakdown   map[string]float64 `json:"breakdown"`
		}
		if err := mustUnmarshal(data, &u); err != nil { return err }
		GetContext(cmd).Printer.Info("Usage — Current Period")
		GetContext(cmd).Printer.Detail("Period", u.PeriodStart+" — "+u.PeriodEnd)
		GetContext(cmd).Printer.Detail("Total", fmt.Sprintf("$%.2f", u.Total))
		for k, v := range u.Breakdown {
			GetContext(cmd).Printer.Detail("  "+k, fmt.Sprintf("$%.2f", v))
		}
		return nil
	},
}

func init() {
	paymentsCmd.AddCommand(paymentsBalanceCmd)
	paymentsCmd.AddCommand(paymentsInvoicesCmd)
	paymentsCmd.AddCommand(paymentsPlansCmd)
	paymentsCmd.AddCommand(paymentsUsageCmd)
}
