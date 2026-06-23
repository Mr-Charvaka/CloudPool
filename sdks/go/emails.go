package cloudpool

import "context"

type EmailsClient struct {
	client *CloudPoolClient
}

type SendEmailRequest struct {
	To      string `json:"to"`
	Subject string `json:"subject"`
	Body    string `json:"body"`
}

type SendEmailResponse struct {
	ID     string `json:"id"`
	Status string `json:"status"`
}

type EmailMessage struct {
	ID         string `json:"id"`
	From       string `json:"from"`
	Subject    string `json:"subject"`
	Read       bool   `json:"read"`
	ReceivedAt string `json:"receivedAt"`
}

func (e *EmailsClient) Send(ctx context.Context, to, subject, body string) (*SendEmailResponse, error) {
	var resp SendEmailResponse
	err := e.client.PostJSON(ctx, "/api/emails/send", SendEmailRequest{
		To: to, Subject: subject, Body: body,
	}, nil, &resp)
	if err != nil {
		return nil, err
	}
	return &resp, nil
}

func (e *EmailsClient) Inbox(ctx context.Context) ([]EmailMessage, error) {
	var resp []EmailMessage
	err := e.client.GetJSON(ctx, "/api/emails/inbox", nil, &resp)
	if err != nil {
		return nil, err
	}
	return resp, nil
}
