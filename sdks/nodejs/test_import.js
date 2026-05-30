const { CloudPoolClient } = require('./dist/index.js');

try {
  const client = new CloudPoolClient({
    baseUrl: 'http://localhost:8080/api',
    apiKey: 'test_api_key_123'
  });
  
  if (client.files && client.database && client.vector) {
    console.log('✅ Node.js/TypeScript SDK client instantiated successfully!');
  } else {
    throw new Error('Some sub-client modules are missing');
  }
} catch (err) {
  console.error('❌ Failed to instantiate client:', err);
  process.exit(1);
}
