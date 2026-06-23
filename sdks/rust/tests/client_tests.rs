use cloudpool::CloudPoolClient;

#[tokio::test]
async fn test_client_instantiation() {
    let client = CloudPoolClient::new(
        "http://localhost:8080/api",
        Some("test_api_key_123".to_string()),
        None,
    );

    // Verify accessor methods compile and exist
    let _files = client.files();
    let _database = client.database();
    let _vector = client.vector();
    let _auth = client.auth();
    let _compute = client.compute();
    let _network = client.network();
    let _payments = client.payments();
    let _kv = client.kv();
    let _emails = client.emails();
}
