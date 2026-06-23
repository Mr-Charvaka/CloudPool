package cloudpool

import "context"

type NetworkClient struct {
	client *CloudPoolClient
}

type TunnelInfo struct {
	Active    bool   `json:"active"`
	LocalPort string `json:"localPort,omitempty"`
	RemoteURL string `json:"remoteUrl,omitempty"`
	URL       string `json:"url,omitempty"`
	ExpiresAt string `json:"expiresAt,omitempty"`
	StartedAt string `json:"startedAt,omitempty"`
}

type PubSubMessage struct {
	Channel string `json:"channel"`
	Message string `json:"message"`
}

type DomainInfo struct {
	Domain string `json:"domain"`
	Status string `json:"status"`
}

type GatewayUser struct {
	Email string `json:"email"`
	Role  string `json:"role"`
}

func (n *NetworkClient) TunnelStart(ctx context.Context, port, subdomain string) (*TunnelInfo, error) {
	var resp TunnelInfo
	err := n.client.PostJSON(ctx, "/api/network/tunnel/start", map[string]interface{}{
		"port": port, "subdomain": subdomain,
	}, nil, &resp)
	if err != nil {
		return nil, err
	}
	return &resp, nil
}

func (n *NetworkClient) TunnelStop(ctx context.Context) error {
	_, err := n.client.Post(ctx, "/api/network/tunnel/stop", nil, nil)
	return err
}

func (n *NetworkClient) TunnelStatus(ctx context.Context) (*TunnelInfo, error) {
	var resp TunnelInfo
	err := n.client.GetJSON(ctx, "/api/network/tunnel/status", nil, &resp)
	if err != nil {
		return nil, err
	}
	return &resp, nil
}

func (n *NetworkClient) PubSubBroadcast(ctx context.Context, channel, message string) (map[string]interface{}, error) {
	var resp map[string]interface{}
	err := n.client.PostJSON(ctx, "/api/pubsub/broadcast", PubSubMessage{
		Channel: channel, Message: message,
	}, nil, &resp)
	if err != nil {
		return nil, err
	}
	return resp, nil
}

func (n *NetworkClient) ListDomains(ctx context.Context) ([]DomainInfo, error) {
	var resp []DomainInfo
	err := n.client.GetJSON(ctx, "/api/network/domains", nil, &resp)
	if err != nil {
		return nil, err
	}
	return resp, nil
}

func (n *NetworkClient) GatewayRegister(ctx context.Context, email, password string) (map[string]interface{}, error) {
	var resp map[string]interface{}
	err := n.client.PostJSON(ctx, "/api/gateway-auth/register", map[string]string{
		"email": email, "password": password,
	}, nil, &resp)
	if err != nil {
		return nil, err
	}
	return resp, nil
}

func (n *NetworkClient) GatewayList(ctx context.Context) ([]GatewayUser, error) {
	var resp []GatewayUser
	err := n.client.GetJSON(ctx, "/api/gateway-auth/list", nil, &resp)
	if err != nil {
		return nil, err
	}
	return resp, nil
}

func (n *NetworkClient) GatewayDelete(ctx context.Context, email string) error {
	_, err := n.client.Delete(ctx, "/api/gateway-auth/"+email)
	return err
}

func (n *NetworkClient) Backups(ctx context.Context) (map[string]interface{}, error) {
	var resp map[string]interface{}
	err := n.client.GetJSON(ctx, "/api/gateway/backups", nil, &resp)
	if err != nil {
		return nil, err
	}
	return resp, nil
}
