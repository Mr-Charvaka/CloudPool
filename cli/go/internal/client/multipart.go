package client

import (
	"bytes"
	"fmt"
	"io"
	"mime/multipart"
	"net/textproto"
	"strings"
)

type multipartWriter struct {
	w      io.Writer
	writer *multipart.Writer
}

func newMultipartWriter(w io.Writer) *multipartWriter {
	return &multipartWriter{
		w:      w,
		writer: multipart.NewWriter(w),
	}
}

func (m *multipartWriter) createFormFile(fieldName, filename string) (io.Writer, error) {
	h := make(textproto.MIMEHeader)
	h.Set("Content-Disposition",
		fmt.Sprintf(`form-data; name="%s"; filename="%s"`, fieldName, filename))
	ct := detectContentType(filename)
	h.Set("Content-Type", ct)
	return m.writer.CreatePart(h)
}

func (m *multipartWriter) close() {
	m.writer.Close()
}

func (m *multipartWriter) contentType() string {
	return m.writer.FormDataContentType()
}

func detectContentType(filename string) string {
	dot := strings.LastIndex(filename, ".")
	if dot < 0 || dot == len(filename)-1 {
		return "application/octet-stream"
	}
	ext := strings.ToLower(filename[dot+1:])
	switch ext {
	case "txt":
		return "text/plain"
	case "json":
		return "application/json"
	case "pdf":
		return "application/pdf"
	case "png":
		return "image/png"
	case "jpg", "jpeg":
		return "image/jpeg"
	case "gif":
		return "image/gif"
	case "zip":
		return "application/zip"
	case "gz", "tar":
		return "application/gzip"
	case "csv":
		return "text/csv"
	case "html", "htm":
		return "text/html"
	case "yaml", "yml":
		return "application/x-yaml"
	case "md":
		return "text/markdown"
	default:
		return "application/octet-stream"
	}
}
