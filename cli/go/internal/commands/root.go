package commands

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"strings"

	"github.com/cloudpool/cli/internal/client"
	"github.com/cloudpool/cli/internal/config"
	"github.com/cloudpool/cli/internal/credstore"
	"github.com/cloudpool/cli/internal/output"
	"github.com/cloudpool/cli/pkg/version"
	"github.com/spf13/cobra"
)

type Context struct {
	Config    *config.Config
	Client    *client.CloudPoolClient
	Printer   *output.Printer
	CredStore credstore.Store
	RootCmd   *cobra.Command
}

type contextKey string

func GetContext(cmd *cobra.Command) *Context {
	ctx := cmd.Context()
	if ctx == nil {
		return nil
	}
	if app, ok := ctx.Value(contextKey("appCtx")).(*Context); ok {
		return app
	}
	return nil
}

func Execute(ctx context.Context) {

	root := &cobra.Command{
		Use:   "cloudpool",
		Short: "CloudPool — AI-Native Backend Platform",
		Long: `CloudPool is a decentralized Backend-as-a-Service platform.

This CLI manages files, databases, compute, auth, networking,
vector search, key-value storage, email, and billing.

Documentation: https://cloudpool.dev/docs
Support:       https://cloudpool.dev/support`,
		SilenceUsage:  true,
		SilenceErrors: true,
		Version:       version.Version,
		PersistentPreRunE: func(cmd *cobra.Command, args []string) error {
			return initialize(cmd)
		},
		Run: func(cmd *cobra.Command, args []string) {
			cmd.Help()
		},
	}

	root.PersistentFlags().StringP("base-url", "b", "", "API base URL (env: CLOUDPOOL_BASE_URL)")
	root.PersistentFlags().StringP("profile", "p", "", "Config profile (env: CLOUDPOOL_PROFILE)")
	root.PersistentFlags().StringP("output", "o", "", "Output format: table|json|yaml|text (env: CLOUDPOOL_OUTPUT)")
	root.PersistentFlags().String("color", "auto", "Color mode: auto|always|never (env: CLOUDPOOL_COLOR)")
	root.PersistentFlags().BoolP("verbose", "v", false, "Verbose logging (env: CLOUDPOOL_VERBOSE)")
	root.PersistentFlags().Bool("insecure", false, "Skip TLS verification (env: CLOUDPOOL_INSECURE)")
	root.PersistentFlags().Bool("no-progress", false, "Disable progress bars (env: CLOUDPOOL_NO_PROGRESS)")
	root.PersistentFlags().Int("timeout", 30, "Request timeout in seconds (env: CLOUDPOOL_TIMEOUT)")
	root.PersistentFlags().Int("retry-max", 3, "Max retry attempts (env: CLOUDPOOL_RETRY_MAX)")
	root.PersistentFlags().StringP("project", "P", "", "Project ID (env: CLOUDPOOL_PROJECT_ID)")
	root.PersistentFlags().String("api-key", "", "API key (env: CLOUDPOOL_API_KEY)")
	root.PersistentFlags().String("jwt", "", "JWT token (env: CLOUDPOOL_JWT_TOKEN)")

	root.SetVersionTemplate("CloudPool CLI {{.Version}}\n")

	registerCommands(root)

	root.CompletionOptions.DisableDefaultCmd = true

	cobra.OnInitialize(func() {})

	if err := root.ExecuteContext(ctx); err != nil {
		if app := GetContext(root); app != nil && app.Printer != nil {
			app.Printer.Fatal(err)
		} else {
			fmt.Fprintf(os.Stderr, "Error: %v\n", err)
		}
	}
}

func initialize(cmd *cobra.Command) error {
	flags := cmd.Flags()
	if root := cmd.Root(); root != nil {
		flags = root.PersistentFlags()
	}

	app := &Context{}
	ctx := context.WithValue(cmd.Context(), contextKey("appCtx"), app)
	cmd.SetContext(ctx)

	cfg, err := config.Init(flags)
	if err != nil {
		return fmt.Errorf("config init: %w", err)
	}
	app.Config = cfg

	outFmt := output.ParseFormat(cfg.Output)
	if f := lookupFlag(cmd, "output"); f != "" {
		outFmt = output.ParseFormat(f)
	}
	colorEnabled := cfg.Color != "never"
	if cfg.Color == "auto" {
		colorEnabled = isTerminal()
	}

	printer := output.New(outFmt)
	printer.Color = colorEnabled
	app.Printer = printer

	cs := credstore.New(cfg.CredentialsPath())
	app.CredStore = cs

	jwtToken := cfg.JWTToken
	apiKey := cfg.APIKey

	if jwtToken == "" && apiKey == "" {
		if t, err := cs.Get("cloudpool", "jwt"); err == nil {
			jwtToken = t
		}
		if k, err := cs.Get("cloudpool", "api_key"); err == nil {
			apiKey = k
		}
	}

	if f := lookupFlag(cmd, "jwt"); f != "" {
		jwtToken = f
	}
	if f := lookupFlag(cmd, "api-key"); f != "" {
		apiKey = f
	}

	timeout := cfg.Timeout
	if t := lookupFlagInt(cmd, "timeout"); t > 0 {
		timeout = t
	}
	retryMax := cfg.RetryMax
	if r := lookupFlagInt(cmd, "retry-max"); r > 0 {
		retryMax = r
	}

	cl := client.New(cfg.BaseURL,
		client.WithJWT(jwtToken),
		client.WithAPIKey(apiKey),
		client.WithProjectID(cfg.ProjectID),
		client.WithRetryMax(retryMax),
		client.WithVerbose(cfg.Verbose),
		client.WithInsecure(cfg.Insecure),
		client.WithNoProgress(cfg.NoProgress),
		client.WithCredStore(cs),
		client.WithTimeout(time.Duration(timeout)*time.Second),
	)
	app.Client = cl

	return nil
}

func registerCommands(root *cobra.Command) {
	root.AddCommand(authCmd)
	root.AddCommand(apiKeysCmd)
	root.AddCommand(filesCmd)
	root.AddCommand(databaseCmd)
	root.AddCommand(vectorCmd)
	root.AddCommand(computeCmd)
	root.AddCommand(networkCmd)
	root.AddCommand(paymentsCmd)
	root.AddCommand(kvCmd)
	root.AddCommand(emailsCmd)
	root.AddCommand(healthCmd)
	root.AddCommand(projectCmd)
	root.AddCommand(completionCmd)
	root.AddCommand(versionCmd)
}

func isTerminal() bool {
	stat, _ := os.Stdout.Stat()
	return (stat.Mode() & os.ModeCharDevice) != 0
}

func lookupFlag(cmd *cobra.Command, name string) string {
	if cmd == nil {
		return ""
	}
	f := cmd.Flag(name)
	if f != nil && f.Changed {
		return f.Value.String()
	}
	if root := cmd.Root(); root != nil && root != cmd {
		f = root.PersistentFlags().Lookup(name)
		if f != nil && f.Changed {
			return f.Value.String()
		}
	}
	return ""
}

func lookupFlagInt(cmd *cobra.Command, name string) int {
	f := cmd.Flag(name)
	if f != nil && f.Changed {
		if v, err := cmd.Flags().GetInt(name); err == nil {
			return v
		}
	}
	return 0
}

// extractFilename extracts the filename from Content-Disposition or falls back to override.
func extractFilename(resp *http.Response, override string) string {
	if override != "" {
		return override
	}
	if cd := resp.Header.Get("Content-Disposition"); cd != "" {
		if parts := strings.Split(cd, "filename="); len(parts) > 1 {
			return strings.Trim(parts[1], `" `)
		}
	}
	return "download"
}

// unmarshalOrFatal attempts to unmarshal JSON data into target. On failure it returns
// an error. Use for the JSON/YAML display paths where we intentionally want to show raw output.
func unmarshalOrFatal(cmd *cobra.Command, data []byte, target interface{}) error {
	if err := json.Unmarshal(data, target); err != nil {
		return fmt.Errorf("failed to parse API response as JSON: %w\nData: %s", err, string(data))
	}
	return nil
}

// mustUnmarshal unmarshals JSON data into target and returns an error
// (wrapping the original) on failure. Use for typed struct unmarshalling
// to avoid silently producing zero-values when the API returns unexpected
// content (HTML, proxy errors, 5xx stack traces, etc.).
func mustUnmarshal(data []byte, target interface{}) error {
	if err := json.Unmarshal(data, target); err != nil {
		return fmt.Errorf("parse response: %w", err)
	}
	return nil
}
