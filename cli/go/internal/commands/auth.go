package commands

import (
	"encoding/json"
	"fmt"
	"os"
	"syscall"

	"github.com/spf13/cobra"
	"golang.org/x/term"
)

var authCmd = &cobra.Command{
	Use:   "auth",
	Short: "Authentication & account management",
	Long: `Manage authentication: login, register, logout, and view profile.

Logs in with email/password or API key. Tokens are stored securely
in the OS keychain (or a file as fallback).`,
}

var authLoginCmd = &cobra.Command{
	Use:   "login [email] [password]",
	Short: "Login to CloudPool",
	Long: `Authenticate with your CloudPool credentials.

Example:
  cloudpool auth login user@example.com mypassword
  cloudpool auth login user@example.com  # prompts for password`,
	Args: cobra.RangeArgs(1, 2),
	RunE: func(cmd *cobra.Command, args []string) error {
		email := args[0]
		password := ""
		if len(args) > 1 {
			password = args[1]
		} else {
			GetContext(cmd).Printer.Info("Enter password for %s", email)
			fmt.Fprint(cmd.OutOrStderr(), "Password: ")
			bytePassword, err := term.ReadPassword(int(syscall.Stdin))
			if err != nil {
				return fmt.Errorf("failed to read password: %w", err)
			}
			password = string(bytePassword)
			fmt.Fprintln(cmd.OutOrStderr())
		}

		data, err := GetContext(cmd).Client.Post(cmd.Context(), "/api/auth/login", map[string]string{
			"email": email, "password": password,
		}, nil)
		if err != nil {
			return fmt.Errorf("login failed: %w", err)
		}

		var resp struct {
			Token        string `json:"token"`
			RefreshToken string `json:"refreshToken"`
		}
		if err := json.Unmarshal(data, &resp); err != nil {
			return fmt.Errorf("parse response: %w", err)
		}

		if err := GetContext(cmd).CredStore.Set("cloudpool", "jwt", resp.Token); err != nil {
			GetContext(cmd).Printer.Warning("Could not save token to keychain: %v", err)
		}
		GetContext(cmd).Printer.Success("Logged in as %s", email)
		return nil
	},
}

var authRegisterCmd = &cobra.Command{
	Use:   "register [email] [password] [name]",
	Short: "Register a new account",
	Args:  cobra.ExactArgs(3),
	RunE: func(cmd *cobra.Command, args []string) error {
		data, err := GetContext(cmd).Client.Post(cmd.Context(), "/api/auth/register", map[string]string{
			"email": args[0], "password": args[1], "name": args[2],
		}, nil)
		if err != nil {
			return fmt.Errorf("registration failed: %w", err)
		}
		var resp struct {
			Token        string `json:"token"`
			RefreshToken string `json:"refreshToken"`
		}
		if err := json.Unmarshal(data, &resp); err != nil {
			return fmt.Errorf("parse response: %w", err)
		}
		if err := GetContext(cmd).CredStore.Set("cloudpool", "jwt", resp.Token); err != nil {
			GetContext(cmd).Printer.Warning("Could not save token: %v", err)
		}
		GetContext(cmd).Printer.Success("Registered and logged in as %s", args[0])
		return nil
	},
}

var authLogoutCmd = &cobra.Command{
	Use:   "logout",
	Short: "Logout and clear stored credentials",
	Args:  cobra.NoArgs,
	RunE: func(cmd *cobra.Command, args []string) error {
		GetContext(cmd).Client.Post(cmd.Context(), "/api/auth/logout", nil, nil)
		if err := GetContext(cmd).CredStore.Delete("cloudpool", "jwt"); err != nil {
			GetContext(cmd).Printer.Warning("Could not clear keychain: %v", err)
		}
		if err := GetContext(cmd).CredStore.Delete("cloudpool", "api_key"); err != nil {
			GetContext(cmd).Printer.Warning("Could not clear API key: %v", err)
		}
		GetContext(cmd).Printer.Success("Logged out")
		return nil
	},
}

var authMeCmd = &cobra.Command{
	Use:   "me",
	Short: "Show current user profile",
	Args:  cobra.NoArgs,
	RunE: func(cmd *cobra.Command, args []string) error {
		data, err := GetContext(cmd).Client.Get(cmd.Context(), "/api/auth/me", nil)
		if err != nil {
			return err
		}
		if GetContext(cmd).Printer.IsJSON() || GetContext(cmd).Printer.IsYAML() {
			var v interface{}
			if err := unmarshalOrFatal(cmd, data, &v); err != nil { return err }
			GetContext(cmd).Printer.Print(v)
			return nil
		}
		var me struct {
			ID        string `json:"id"`
			Email     string `json:"email"`
			Name      string `json:"name"`
			Role      string `json:"role"`
			CreatedAt string `json:"createdAt"`
		}
		if err := mustUnmarshal(data, &me); err != nil {
			return err
		}
		GetContext(cmd).Printer.Info("Current User")
		GetContext(cmd).Printer.Detail("ID", me.ID)
		GetContext(cmd).Printer.Detail("Email", me.Email)
		GetContext(cmd).Printer.Detail("Name", me.Name)
		GetContext(cmd).Printer.Detail("Role", me.Role)
		GetContext(cmd).Printer.Detail("Created", me.CreatedAt)
		return nil
	},
}

var authRefreshCmd = &cobra.Command{
	Use:   "refresh",
	Short: "Refresh the current JWT token",
	Args:  cobra.NoArgs,
	RunE: func(cmd *cobra.Command, args []string) error {
		data, err := GetContext(cmd).Client.Post(cmd.Context(), "/api/auth/refresh", nil, nil)
		if err != nil {
			return fmt.Errorf("refresh failed: %w", err)
		}
		var resp struct {
			Token        string `json:"token"`
			RefreshToken string `json:"refreshToken"`
		}
		if err := mustUnmarshal(data, &resp); err != nil {
			return err
		}
		if resp.Token != "" {
			GetContext(cmd).CredStore.Set("cloudpool", "jwt", resp.Token)
			GetContext(cmd).Printer.Success("Token refreshed")
		}
		return nil
	},
}

func init() {
	authCmd.AddCommand(authLoginCmd)
	authCmd.AddCommand(authRegisterCmd)
	authCmd.AddCommand(authLogoutCmd)
	authCmd.AddCommand(authMeCmd)
	authCmd.AddCommand(authRefreshCmd)
}
