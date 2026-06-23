package commands

import (
	"fmt"
	"io"
	"os"
	"strconv"

	"github.com/cloudpool/cli/internal/client"
	"github.com/spf13/cobra"
)

var filesCmd = &cobra.Command{
	Use:   "files",
	Short: "File storage operations",
	Long: `Upload, download, list, and share files.

Files are stored in buckets with checksum verification and optional
compression. Supports sharing with time-limited tokens.`,
	Aliases: []string{"file", "storage"},
}

var filesUploadCmd = &cobra.Command{
	Use:   "upload [file-path]",
	Short: "Upload a file",
	Long: `Upload a file to a storage bucket.

Examples:
  cloudpool files upload ./photo.jpg
  cloudpool files upload ./data.csv --bucket analytics`,
	Args: cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		bucket, _ := cmd.Flags().GetString("bucket")
		if bucket == "" {
			bucket = "default"
		}
		data, err := GetContext(cmd).Client.Upload(cmd.Context(), "/api/files/upload", args[0], bucket)
		if err != nil {
			return fmt.Errorf("upload failed: %w", err)
		}
		if GetContext(cmd).Printer.IsJSON() || GetContext(cmd).Printer.IsYAML() {
			var v interface{}
			if err := unmarshalOrFatal(cmd, data, &v); err != nil { return err }
			GetContext(cmd).Printer.Print(v)
			return nil
		}
		var f struct {
			ID           string `json:"id"`
			OriginalName string `json:"originalName"`
			Size         int64  `json:"size"`
			Checksum     string `json:"checksum"`
		}
		if err := mustUnmarshal(data, &f); err != nil { return err }
		GetContext(cmd).Printer.Success("Uploaded: %s", f.OriginalName)
		GetContext(cmd).Printer.Detail("ID", f.ID)
		GetContext(cmd).Printer.Detail("Size", formatBytes(f.Size))
		GetContext(cmd).Printer.Detail("Checksum", trunc(f.Checksum, 16))
		return nil
	},
}

var filesListCmd = &cobra.Command{
	Use:   "list",
	Short: "List uploaded files",
	Args:  cobra.NoArgs,
	RunE: func(cmd *cobra.Command, args []string) error {
		page, _ := cmd.Flags().GetInt("page")
		size, _ := cmd.Flags().GetInt("size")
		resp, err := GetContext(cmd).Client.DoRaw(cmd.Context(), "GET", "/api/files", map[string]string{
			"page": strconv.Itoa(page), "size": strconv.Itoa(size),
		})
		if err != nil {
			return err
		}
		defer resp.Body.Close()

		if resp.StatusCode >= 400 {
			body, _ := io.ReadAll(resp.Body)
			return fmt.Errorf("[%d] %s", resp.StatusCode, client.SanitizeErrorBody(string(body)))
		}

		if GetContext(cmd).Printer.IsJSON() || GetContext(cmd).Printer.IsYAML() {
			// For JSON/YAML, streaming directly to stdout is safest to avoid OOM
			_, err := io.Copy(os.Stdout, resp.Body)
			return err
		}

		type fileItem struct {
			ID           string `json:"id"`
			OriginalName string `json:"originalName"`
			Size         int64  `json:"size"`
			MimeType     string `json:"mimeType"`
			CreatedAt    string `json:"createdAt"`
		}

		dec := json.NewDecoder(resp.Body)
		// Read open bracket
		t, err := dec.Token()
		if err != nil || t != json.Delim('[') {
			return fmt.Errorf("expected JSON array")
		}

		fmt.Printf("%-10s %-30s %-10s %-20s %-12s\n", "ID", "Name", "Size", "Type", "Uploaded")
		fmt.Println("--------------------------------------------------------------------------------------")
		for dec.More() {
			var f fileItem
			if err := dec.Decode(&f); err != nil {
				return fmt.Errorf("decode file: %w", err)
			}
			fmt.Printf("%-10s %-30s %-10s %-20s %-12s\n",
				trunc(f.ID, 8), trunc(f.OriginalName, 29), formatBytes(f.Size), trunc(f.MimeType, 19), trunc(f.CreatedAt, 10))
		}
		// Read close bracket
		_, _ = dec.Token()
		return nil
	},
}

var filesDownloadCmd = &cobra.Command{
	Use:   "download [file-id]",
	Short: "Download a file",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		out, _ := cmd.Flags().GetString("output")
		apiPath := "/api/files/download/" + args[0]

		// First call with a tee'd request to sniff Content-Length and filename
		// without buffering the payload in memory.
		GetContext(cmd).Printer.Info("Downloading %s...", args[0])

		resp, err := GetContext(cmd).Client.DoRaw(cmd.Context(), "GET", apiPath, nil)
		if err != nil {
			return fmt.Errorf("request failed: %w", err)
		}
		defer resp.Body.Close()

		if resp.StatusCode >= 400 {
			body, _ := io.ReadAll(resp.Body)
			return fmt.Errorf("[%d] %s", resp.StatusCode, client.SanitizeErrorBody(string(body)))
		}

		filename := extractFilename(resp, out)

		f, err := os.Create(filename)
		if err != nil {
			return fmt.Errorf("create file: %w", err)
		}
		defer f.Close()

		written, err := io.Copy(f, resp.Body)
		if err != nil {
			return fmt.Errorf("write file: %w", err)
		}
		GetContext(cmd).Printer.Success("Downloaded %s (%s)", filename, formatBytes(written))
		return nil
	},
}

var filesShareCmd = &cobra.Command{
	Use:   "share [file-id]",
	Short: "Share a file with a time-limited token",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		email, _ := cmd.Flags().GetString("email")
		expiry, _ := cmd.Flags().GetInt("expiry")
		body := map[string]interface{}{}
		if email != "" {
			body["sharedWithEmail"] = email
		}
		if expiry > 0 {
			body["expiryHours"] = expiry
		}
		data, err := GetContext(cmd).Client.Post(cmd.Context(), "/api/files/"+args[0]+"/share", body, nil)
		if err != nil {
			return err
		}
		if GetContext(cmd).Printer.IsJSON() || GetContext(cmd).Printer.IsYAML() {
			var v interface{}
			if err := unmarshalOrFatal(cmd, data, &v); err != nil { return err }
			GetContext(cmd).Printer.Print(v)
			return nil
		}
		var s struct {
			Token     string `json:"token"`
			ExpiresAt string `json:"expiresAt"`
		}
		if err := mustUnmarshal(data, &s); err != nil { return err }
		GetContext(cmd).Printer.Success("File shared")
		GetContext(cmd).Printer.Detail("Token", s.Token)
		GetContext(cmd).Printer.Detail("Expires", s.ExpiresAt)
		return nil
	},
}

var filesBucketsCmd = &cobra.Command{
	Use:   "buckets",
	Short: "List available storage buckets",
	Args:  cobra.NoArgs,
	RunE: func(cmd *cobra.Command, args []string) error {
		data, err := GetContext(cmd).Client.Get(cmd.Context(), "/api/files/buckets", nil)
		if err != nil {
			return err
		}
		if GetContext(cmd).Printer.IsJSON() || GetContext(cmd).Printer.IsYAML() {
			var v interface{}
			if err := unmarshalOrFatal(cmd, data, &v); err != nil { return err }
			GetContext(cmd).Printer.Print(v)
			return nil
		}
		var buckets []struct {
			ID   string `json:"id"`
			Name string `json:"name"`
			Desc string `json:"description"`
		}
		if err := mustUnmarshal(data, &buckets); err != nil { return err }
		rows := make([][]string, 0)
		for _, b := range buckets {
			rows = append(rows, []string{trunc(b.ID, 8), b.Name, b.Desc})
		}
		GetContext(cmd).Printer.PrintTable([]string{"ID", "Name", "Description"}, rows)
		return nil
	},
}

var filesQuotaCmd = &cobra.Command{
	Use:   "quota",
	Short: "Show storage quota and usage",
	Args:  cobra.NoArgs,
	RunE: func(cmd *cobra.Command, args []string) error {
		data, err := GetContext(cmd).Client.Get(cmd.Context(), "/api/files/quota", nil)
		if err != nil {
			return err
		}
		if GetContext(cmd).Printer.IsJSON() || GetContext(cmd).Printer.IsYAML() {
			var v interface{}
			if err := unmarshalOrFatal(cmd, data, &v); err != nil { return err }
			GetContext(cmd).Printer.Print(v)
			return nil
		}
		var q struct {
			Limit int64 `json:"limit"`
			Usage int64 `json:"usage"`
		}
		if err := mustUnmarshal(data, &q); err != nil { return err }
		pct := 0.0
		if q.Limit > 0 {
			pct = float64(q.Usage) / float64(q.Limit) * 100
		}
		GetContext(cmd).Printer.Info("Storage Quota")
		GetContext(cmd).Printer.Detail("Used", formatBytes(q.Usage))
		GetContext(cmd).Printer.Detail("Limit", formatBytes(q.Limit))
		GetContext(cmd).Printer.Detail("Usage", fmt.Sprintf("%.1f%%", pct))
		return nil
	},
}

var filesLogsCmd = &cobra.Command{
	Use:   "logs",
	Short: "Show file audit logs",
	Args:  cobra.NoArgs,
	RunE: func(cmd *cobra.Command, args []string) error {
		data, err := GetContext(cmd).Client.Get(cmd.Context(), "/api/files/logs", nil)
		if err != nil {
			return err
		}
		if GetContext(cmd).Printer.IsJSON() || GetContext(cmd).Printer.IsYAML() {
			var v interface{}
			if err := unmarshalOrFatal(cmd, data, &v); err != nil { return err }
			GetContext(cmd).Printer.Print(v)
			return nil
		}
		var logs []struct {
			Action    string `json:"action"`
			Details   string `json:"details"`
			Timestamp string `json:"timestamp"`
		}
		if err := mustUnmarshal(data, &logs); err != nil { return err }
		rows := make([][]string, 0)
		for _, l := range logs {
			rows = append(rows, []string{l.Action, trunc(l.Details, 60), trunc(l.Timestamp, 19)})
		}
		GetContext(cmd).Printer.PrintTable([]string{"Action", "Details", "Timestamp"}, rows)
		return nil
	},
}

func init() {
	filesCmd.AddCommand(filesUploadCmd)
	filesCmd.AddCommand(filesListCmd)
	filesCmd.AddCommand(filesDownloadCmd)
	filesCmd.AddCommand(filesShareCmd)
	filesCmd.AddCommand(filesBucketsCmd)
	filesCmd.AddCommand(filesQuotaCmd)
	filesCmd.AddCommand(filesLogsCmd)
	filesUploadCmd.Flags().StringP("bucket", "b", "default", "Target bucket name")
	filesListCmd.Flags().Int("page", 0, "Page number (0-indexed)")
	filesListCmd.Flags().Int("size", 20, "Page size")
	filesDownloadCmd.Flags().StringP("output", "o", "", "Output file path (defaults to original filename)")
	filesShareCmd.Flags().StringP("email", "e", "", "Share with specific email")
	filesShareCmd.Flags().IntP("expiry", "x", 24, "Expiry in hours")
}

func formatBytes(b int64) string {
	if b < 1024 {
		return fmt.Sprintf("%d B", b)
	}
	if b < 1024*1024 {
		return fmt.Sprintf("%.1f KB", float64(b)/1024)
	}
	if b < 1024*1024*1024 {
		return fmt.Sprintf("%.1f MB", float64(b)/(1024*1024))
	}
	return fmt.Sprintf("%.1f GB", float64(b)/(1024*1024*1024))
}
