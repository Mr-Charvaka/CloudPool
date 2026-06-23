package commands

import (
	"fmt"
	"os"

	"github.com/spf13/cobra"
)

var completionCmd = &cobra.Command{
	Use:   "completion [bash|zsh|fish|powershell]",
	Short: "Generate shell completion scripts",
	Long: `Generate shell completion scripts for bash, zsh, fish, or PowerShell.

Usage:
  cloudpool completion bash > /etc/bash_completion.d/cloudpool
  cloudpool completion zsh  > /usr/local/share/zsh/site-functions/_cloudpool
  cloudpool completion fish > ~/.config/fish/completions/cloudpool.fish
  cloudpool completion powershell > cloudpool.ps1`,
	Args:      cobra.ExactValidArgs(1),
	ValidArgs: []string{"bash", "zsh", "fish", "powershell"},
	RunE: func(cmd *cobra.Command, args []string) error {
		var err error
		switch args[0] {
		case "bash":
			err = GetContext(cmd).RootCmd.GenBashCompletion(os.Stdout)
		case "zsh":
			err = GetContext(cmd).RootCmd.GenZshCompletion(os.Stdout)
		case "fish":
			err = GetContext(cmd).RootCmd.GenFishCompletion(os.Stdout, true)
		case "powershell":
			err = GetContext(cmd).RootCmd.GenPowerShellCompletionWithDesc(os.Stdout)
		default:
			return fmt.Errorf("unknown shell: %s", args[0])
		}
		return err
	},
}
