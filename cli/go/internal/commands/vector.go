package commands

import (
	"encoding/json"
	"fmt"
	"os"

	"github.com/spf13/cobra"
)

var vectorCmd = &cobra.Command{
	Use:   "vector",
	Short: "Vector search & embeddings",
	Long: `Search, import, and manage vector indexes.

Vector search uses embeddings for semantic similarity queries.
Supports multiple indexes with configurable schemas.`,
	Aliases: []string{"vec", "search", "embeddings"},
}

var vectorSearchCmd = &cobra.Command{
	Use:   "search [index] [query]",
	Short: "Search a vector index",
	Args:  cobra.ExactArgs(2),
	RunE: func(cmd *cobra.Command, args []string) error {
		limit, _ := cmd.Flags().GetInt("limit")
		if limit <= 0 {
			limit = 10
		}
		data, err := GetContext(cmd).Client.Post(cmd.Context(), "/api/vector/"+args[0]+"/search", map[string]interface{}{
			"query": args[1], "limit": limit,
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
		var results []map[string]interface{}
		if err := mustUnmarshal(data, &results); err != nil { return err }
		if len(results) == 0 {
			GetContext(cmd).Printer.Info("No results found")
			return nil
		}
		rows := make([][]string, 0)
		for _, r := range results {
			id, _ := r["id"].(string)
			score, _ := r["score"].(float64)
			text, _ := r["text"].(string)
			rows = append(rows, []string{trunc(id, 12), fmt.Sprintf("%.4f", score), trunc(text, 60)})
		}
		GetContext(cmd).Printer.PrintTable([]string{"ID", "Score", "Text"}, rows)
		return nil
	},
}

var vectorImportCmd = &cobra.Command{
	Use:   "import [index] [json-file]",
	Short: "Import vectors from a JSON file",
	Args:  cobra.ExactArgs(2),
	RunE: func(cmd *cobra.Command, args []string) error {
		content, err := os.ReadFile(args[1])
		if err != nil {
			return fmt.Errorf("read file: %w", err)
		}
		var vectors interface{}
		if err := json.Unmarshal(content, &vectors); err != nil {
			return fmt.Errorf("invalid JSON: %w", err)
		}
		data, err := GetContext(cmd).Client.Post(cmd.Context(), "/api/vector/"+args[0]+"/import", vectors, nil)
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
			Imported int `json:"imported"`
		}
		if err := mustUnmarshal(data, &resp); err != nil { return err }
		GetContext(cmd).Printer.Success("Imported %d vectors", resp.Imported)
		return nil
	},
}

var vectorDeleteCmd = &cobra.Command{
	Use:   "delete [index] [vector-id]",
	Short: "Delete a vector by ID",
	Args:  cobra.ExactArgs(2),
	RunE: func(cmd *cobra.Command, args []string) error {
		_, err := GetContext(cmd).Client.Delete(cmd.Context(), "/api/vector/"+args[0]+"/"+args[1])
		if err != nil {
			return err
		}
		GetContext(cmd).Printer.Success("Deleted document %s from collection %s", args[1], args[0])
		return nil
	},
}

var vectorSchemaCmd = &cobra.Command{
	Use:   "schema [index]",
	Short: "Show vector index schema",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		data, err := GetContext(cmd).Client.Get(cmd.Context(), "/api/vector/"+args[0]+"/schema", nil)
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
	vectorCmd.AddCommand(vectorSearchCmd)
	vectorCmd.AddCommand(vectorImportCmd)
	vectorCmd.AddCommand(vectorDeleteCmd)
	vectorCmd.AddCommand(vectorSchemaCmd)
	vectorSearchCmd.Flags().IntP("limit", "l", 10, "Maximum number of results")
}
