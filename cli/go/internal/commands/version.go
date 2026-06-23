package commands

import (
	"fmt"

	"github.com/cloudpool/cli/pkg/version"
	"github.com/spf13/cobra"
)

var versionCmd = &cobra.Command{
	Use:   "version",
	Short: "Print CLI version and build info",
	Args:  cobra.NoArgs,
	RunE: func(cmd *cobra.Command, args []string) error {
		info := version.Get()
		if GetContext(cmd).Printer.IsJSON() || GetContext(cmd).Printer.IsYAML() {
			GetContext(cmd).Printer.Print(info)
			return nil
		}
		fmt.Printf("CloudPool CLI v%s\n", info.Version)
		fmt.Printf("  Commit:    %s\n", info.Commit)
		fmt.Printf("  Built:     %s\n", info.Date)
		fmt.Printf("  Go:        %s\n", info.GoVersion)
		fmt.Printf("  BuildBy:   %s\n", info.BuiltBy)
		return nil
	},
}
