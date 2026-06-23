package commands

import (
	"github.com/spf13/cobra"
)

var projectCmd = &cobra.Command{
	Use:   "project",
	Short: "Manage projects",
	Long: `Create, list, and switch between projects.

Projects isolate resources (files, databases, compute, KV).
Use --project or CLOUDPOOL_PROJECT_ID to select an active project.`,
	Aliases: []string{"projects", "proj"},
}

var projectCreateCmd = &cobra.Command{
	Use:   "create [name]",
	Short: "Create a new project",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		desc, _ := cmd.Flags().GetString("description")
		body := map[string]interface{}{"name": args[0]}
		if desc != "" {
			body["description"] = desc
		}
		data, err := GetContext(cmd).Client.Post(cmd.Context(), "/api/projects", body, nil)
		if err != nil {
			return err
		}
		if GetContext(cmd).Printer.IsJSON() || GetContext(cmd).Printer.IsYAML() {
			var v interface{}
			if err := unmarshalOrFatal(cmd, data, &v); err != nil { return err }
			GetContext(cmd).Printer.Print(v)
			return nil
		}
		var p struct {
			ID          string `json:"id"`
			Name        string `json:"name"`
			Description string `json:"description"`
		}
		if err := mustUnmarshal(data, &p); err != nil { return err }
		GetContext(cmd).Printer.Success("Project created: %s", p.Name)
		GetContext(cmd).Printer.Detail("ID", p.ID)
		if p.Description != "" {
			GetContext(cmd).Printer.Detail("Description", p.Description)
		}
		return nil
	},
}

var projectListCmd = &cobra.Command{
	Use:   "list",
	Short: "List all projects",
	Args:  cobra.NoArgs,
	RunE: func(cmd *cobra.Command, args []string) error {
		data, err := GetContext(cmd).Client.Get(cmd.Context(), "/api/projects", nil)
		if err != nil {
			return err
		}
		if GetContext(cmd).Printer.IsJSON() || GetContext(cmd).Printer.IsYAML() {
			var v interface{}
			if err := unmarshalOrFatal(cmd, data, &v); err != nil { return err }
			GetContext(cmd).Printer.Print(v)
			return nil
		}
		var projects []struct {
			ID          string `json:"id"`
			Name        string `json:"name"`
			Description string `json:"description,omitempty"`
		}
		if err := mustUnmarshal(data, &projects); err != nil { return err }
		if len(projects) == 0 {
			GetContext(cmd).Printer.Info("No projects found")
			return nil
		}
		rows := make([][]string, 0)
		for _, p := range projects {
			active := ""
			if p.ID == GetContext(cmd).Config.ProjectID {
				active = "*"
			}
			rows = append(rows, []string{trunc(p.ID, 8), p.Name, p.Description, active})
		}
		GetContext(cmd).Printer.PrintTable([]string{"ID", "Name", "Description", "Active"}, rows)
		return nil
	},
}

var projectSetCmd = &cobra.Command{
	Use:   "set [project-id]",
	Short: "Set the active project",
	Long: `Set the active project for subsequent commands.

This updates the config file and sets the X-Project-Id header.`,
	Args: cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		if err := GetContext(cmd).Config.WriteConfigKey("project_id", args[0]); err != nil {
			return err
		}
		GetContext(cmd).Config.ProjectID = args[0]
		GetContext(cmd).Printer.Success("Active project set to %s", args[0])
		return nil
	},
}

func init() {
	projectCmd.AddCommand(projectCreateCmd)
	projectCmd.AddCommand(projectListCmd)
	projectCmd.AddCommand(projectSetCmd)
	projectCreateCmd.Flags().StringP("description", "d", "", "Project description")
}
