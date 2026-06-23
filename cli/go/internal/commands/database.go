package commands

import (
	"strconv"

	"github.com/spf13/cobra"
)

var databaseCmd = &cobra.Command{
	Use:   "database",
	Short: "Database operations (NoSQL + SQL)",
	Long: `Query and manage database tables and records.

Supports listing tables, querying records with filters,
and triggering backups.`,
	Aliases: []string{"db", "sql"},
}

var dbTablesCmd = &cobra.Command{
	Use:   "tables",
	Short: "List all database tables",
	Args:  cobra.NoArgs,
	RunE: func(cmd *cobra.Command, args []string) error {
		data, err := GetContext(cmd).Client.Get(cmd.Context(), "/api/db/tables", nil)
		if err != nil {
			return err
		}
		if GetContext(cmd).Printer.IsJSON() || GetContext(cmd).Printer.IsYAML() {
			var v interface{}
			if err := unmarshalOrFatal(cmd, data, &v); err != nil { return err }
			GetContext(cmd).Printer.Print(v)
			return nil
		}
		var tables []struct {
			Name     string `json:"name"`
			RowCount int    `json:"rowCount"`
		}
		if err := mustUnmarshal(data, &tables); err != nil { return err }
		rows := make([][]string, 0)
		for _, t := range tables {
			rows = append(rows, []string{t.Name, strconv.Itoa(t.RowCount)})
		}
		GetContext(cmd).Printer.PrintTable([]string{"Table", "Rows"}, rows)
		return nil
	},
}

var dbRecordsCmd = &cobra.Command{
	Use:   "records [table]",
	Short: "List records in a table",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		page, _ := cmd.Flags().GetInt("page")
		limit, _ := cmd.Flags().GetInt("limit")
		data, err := GetContext(cmd).Client.Get(cmd.Context(), "/api/db/"+args[0]+"/records", map[string]string{
			"page": strconv.Itoa(page), "limit": strconv.Itoa(limit),
		})
		if err != nil {
			return err
		}
		var v interface{}
		if err := unmarshalOrFatal(cmd, data, &v); err != nil { return err }
		GetContext(cmd).Printer.Print(v)
		return nil
	},
}

var dbQueryCmd = &cobra.Command{
	Use:   "query [table] [field] [value]",
	Short: "Query records with a field filter",
	Args:  cobra.ExactArgs(3),
	RunE: func(cmd *cobra.Command, args []string) error {
		data, err := GetContext(cmd).Client.Get(cmd.Context(), "/api/db/"+args[0]+"/query", map[string]string{
			"field": args[1], "value": args[2],
		})
		if err != nil {
			return err
		}
		var v interface{}
		if err := unmarshalOrFatal(cmd, data, &v); err != nil { return err }
		GetContext(cmd).Printer.Print(v)
		return nil
	},
}

var dbBackupCmd = &cobra.Command{
	Use:   "backup [table]",
	Short: "Trigger a table backup",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		data, err := GetContext(cmd).Client.Post(cmd.Context(), "/api/db/"+args[0]+"/backup", nil, nil)
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
			URL string `json:"url"`
		}
		if err := mustUnmarshal(data, &resp); err != nil { return err }
		GetContext(cmd).Printer.Success("Backup available at: %s", resp.URL)
		return nil
	},
}

func init() {
	databaseCmd.AddCommand(dbTablesCmd)
	databaseCmd.AddCommand(dbRecordsCmd)
	databaseCmd.AddCommand(dbQueryCmd)
	databaseCmd.AddCommand(dbBackupCmd)
	dbRecordsCmd.Flags().Int("page", 0, "Page number")
	dbRecordsCmd.Flags().Int("limit", 20, "Records per page")
}
