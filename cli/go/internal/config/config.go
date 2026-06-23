package config

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/spf13/pflag"
	"github.com/spf13/viper"
)

type Config struct {
	BaseURL    string `mapstructure:"base_url"`
	JWTToken   string `mapstructure:"jwt_token"`
	APIKey     string `mapstructure:"api_key"`
	ProjectID  string `mapstructure:"project_id"`
	Profile    string `mapstructure:"profile"`
	Output     string `mapstructure:"output"`
	Color      string `mapstructure:"color"`
	Timeout    int    `mapstructure:"timeout"`
	RetryMax   int    `mapstructure:"retry_max"`
	Verbose    bool   `mapstructure:"verbose"`
	Insecure   bool   `mapstructure:"insecure"`
	NoProgress bool   `mapstructure:"no_progress"`
	ConfigDir  string `mapstructure:"-"`
	viper      *viper.Viper
}

func configDir() (string, error) {
	if d := os.Getenv("CLOUDPOOL_CONFIG_DIR"); d != "" {
		return d, nil
	}
	home, err := os.UserHomeDir()
	if err != nil {
		return "", fmt.Errorf("cannot find home dir: %w", err)
	}
	return filepath.Join(home, ".cloudpool"), nil
}

func configFile(dir string) string {
	return filepath.Join(dir, "config.yaml")
}

func credentialsFile(dir string) string {
	return filepath.Join(dir, "credentials.json")
}

func Init(flags *pflag.FlagSet) (*Config, error) {
	dir, err := configDir()
	if err != nil {
		return nil, err
	}
	if err := os.MkdirAll(dir, 0700); err != nil {
		return nil, fmt.Errorf("cannot create config dir %s: %w", dir, err)
	}
	v := viper.New()
	v.SetConfigFile(configFile(dir))
	v.SetConfigType("yaml")
	v.SetDefault("base_url", "https://api.cloudpool.dev")
	v.SetDefault("profile", "default")
	v.SetDefault("output", "table")
	v.SetDefault("color", "auto")
	v.SetDefault("timeout", 30)
	v.SetDefault("retry_max", 3)
	v.SetDefault("verbose", false)
	v.SetDefault("insecure", false)
	v.SetDefault("no_progress", false)
	v.SetEnvPrefix("CLOUDPOOL")
	v.SetEnvKeyReplacer(strings.NewReplacer("-", "_", ".", "_"))
	v.AutomaticEnv()
	if err := v.ReadInConfig(); err != nil {
		if _, ok := err.(viper.ConfigFileNotFoundError); !ok {
			return nil, fmt.Errorf("config read error: %w", err)
		}
	}
	if flags != nil {
		flags.VisitAll(func(f *pflag.Flag) {
			if f.Changed {
				v.Set(f.Name, f.Value)
			}
		})
	}
	var cfg Config
	if err := v.Unmarshal(&cfg); err != nil {
		return nil, fmt.Errorf("config unmarshal: %w", err)
	}
	cfg.ConfigDir = dir
	cfg.viper = v
	return &cfg, nil
}

func (c *Config) CredentialsPath() string {
	return credentialsFile(c.ConfigDir)
}

func (c *Config) SaveJWT(token string) error {
	return writeJSON(c.CredentialsPath(), map[string]string{"jwt_token": token})
}

func (c *Config) SaveAPIKey(key string) error {
	return writeJSON(c.CredentialsPath(), map[string]string{"api_key": key})
}

func (c *Config) ClearAuth() error {
	if err := os.Remove(c.CredentialsPath()); err != nil && !os.IsNotExist(err) {
		return err
	}
	return nil
}

func (c *Config) WriteConfigKey(key, value string) error {
	if c.viper != nil {
		c.viper.Set(key, value)
		return c.viper.WriteConfig()
	}
	return fmt.Errorf("config not initialized")
}

func writeJSON(path string, v interface{}) error {
	data, err := json.MarshalIndent(v, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(path, data, 0600)
}
