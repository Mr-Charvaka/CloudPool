package cloudpool

import (
	"context"
	"strconv"
)

type ComputeClient struct {
	client *CloudPoolClient
}

type ServerlessDeployment struct {
	ID     string `json:"id"`
	Name   string `json:"name"`
	Status string `json:"status"`
	URL    string `json:"url,omitempty"`
}

type CronJob struct {
	ID       string `json:"id"`
	Name     string `json:"name"`
	Schedule string `json:"schedule"`
}

type PodInfo struct {
	ID     string `json:"id"`
	Image  string `json:"image"`
	Status string `json:"status"`
	CPU    string `json:"cpu,omitempty"`
	Memory string `json:"memory,omitempty"`
}

type LogEntry struct {
	Timestamp string `json:"timestamp"`
	Level     string `json:"level"`
	Message   string `json:"message"`
}

func (c *ComputeClient) DeployServerless(ctx context.Context, name, runtime string) (*ServerlessDeployment, error) {
	var resp ServerlessDeployment
	err := c.client.PostJSON(ctx, "/api/compute/serverless", map[string]interface{}{
		"name": name, "runtime": runtime,
	}, nil, &resp)
	if err != nil {
		return nil, err
	}
	return &resp, nil
}

func (c *ComputeClient) UploadCode(ctx context.Context, name, filePath string) (map[string]interface{}, error) {
	data, err := c.client.Upload(ctx, "/api/compute/serverless/"+name+"/code", filePath, "serverless")
	if err != nil {
		return nil, err
	}
	var resp map[string]interface{}
	if err := unmarshalResponse(data, &resp); err != nil {
		return nil, err
	}
	return resp, nil
}

func (c *ComputeClient) CreateCron(ctx context.Context, name, schedule string, code string) (*CronJob, error) {
	body := map[string]interface{}{"name": name, "schedule": schedule}
	if code != "" {
		body["code"] = code
	}
	var resp CronJob
	err := c.client.PostJSON(ctx, "/api/compute/cron", body, nil, &resp)
	if err != nil {
		return nil, err
	}
	return &resp, nil
}

func (c *ComputeClient) Deploy(ctx context.Context, directory string) (map[string]interface{}, error) {
	var resp map[string]interface{}
	err := c.client.PostJSON(ctx, "/api/compute/deploy", map[string]string{
		"path": directory,
	}, nil, &resp)
	if err != nil {
		return nil, err
	}
	return resp, nil
}

func (c *ComputeClient) Logs(ctx context.Context, functionID string, tail int) ([]LogEntry, error) {
	var resp []LogEntry
	err := c.client.GetJSON(ctx, "/api/compute/"+functionID+"/logs", map[string]string{
		"tail": strconv.Itoa(tail),
	}, &resp)
	if err != nil {
		return nil, err
	}
	return resp, nil
}

func (c *ComputeClient) ListPods(ctx context.Context) ([]PodInfo, error) {
	var resp []PodInfo
	err := c.client.GetJSON(ctx, "/api/compute/pods", nil, &resp)
	if err != nil {
		return nil, err
	}
	return resp, nil
}
