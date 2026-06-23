package cloudpool

import "context"

type PaymentsClient struct {
	client *CloudPoolClient
}

type BalanceInfo struct {
	Balance  float64 `json:"balance"`
	Pending  float64 `json:"pending"`
	Currency string  `json:"currency"`
}

type Invoice struct {
	ID          string  `json:"id"`
	Amount      float64 `json:"amount"`
	Status      string  `json:"status"`
	DueDate     string  `json:"dueDate,omitempty"`
	Description string  `json:"description,omitempty"`
}

type Plan struct {
	Name     string   `json:"name"`
	Price    float64  `json:"price"`
	Currency string   `json:"currency"`
	Features []string `json:"features,omitempty"`
}

type UsageInfo struct {
	PeriodStart string             `json:"periodStart"`
	PeriodEnd   string             `json:"periodEnd"`
	Total       float64            `json:"total"`
	Breakdown   map[string]float64 `json:"breakdown,omitempty"`
}

func (p *PaymentsClient) Balance(ctx context.Context) (*BalanceInfo, error) {
	var resp BalanceInfo
	err := p.client.GetJSON(ctx, "/api/payments/balance", nil, &resp)
	if err != nil {
		return nil, err
	}
	return &resp, nil
}

func (p *PaymentsClient) Invoices(ctx context.Context) ([]Invoice, error) {
	var resp []Invoice
	err := p.client.GetJSON(ctx, "/api/payments/invoices", nil, &resp)
	if err != nil {
		return nil, err
	}
	return resp, nil
}

func (p *PaymentsClient) Plans(ctx context.Context) ([]Plan, error) {
	var resp []Plan
	err := p.client.GetJSON(ctx, "/api/payments/plans", nil, &resp)
	if err != nil {
		return nil, err
	}
	return resp, nil
}

func (p *PaymentsClient) Usage(ctx context.Context) (*UsageInfo, error) {
	var resp UsageInfo
	err := p.client.GetJSON(ctx, "/api/payments/usage", nil, &resp)
	if err != nil {
		return nil, err
	}
	return &resp, nil
}
