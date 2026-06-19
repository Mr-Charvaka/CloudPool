import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '5s', target: 20 },  // Ramp-up to 20 users
    { duration: '10s', target: 50 }, // Steady state at 50 users
    { duration: '5s', target: 0 },   // Ramp-down to 0 users
  ],
  thresholds: {
    http_req_duration: ['p(95)<200'], // 95% of requests must complete below 200ms
    http_req_failed: ['rate<0.05'],   // Less than 5% failed requests
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(95)', 'p(99)'],
};

const BASE_URL = __ENV.TARGET_URL || 'http://localhost:8080';

export default function () {
  // 1. Health check endpoint load test
  let healthRes = http.get(`${BASE_URL}/api/health`);
  check(healthRes, {
    'health status is 200': (r) => r.status === 200,
    'health service is UP': (r) => {
      try {
        return JSON.parse(r.body).status === 'UP';
      } catch (e) {
        return false;
      }
    }
  });

  sleep(0.5);

  // 2. Authentication endpoint load test (Login)
  const loginPayload = JSON.stringify({
    email: 'benchmark_dev@cloudpool.com',
    password: 'BenchmarkPass123!'
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',

    },
  };

  let authRes = http.post(`${BASE_URL}/api/auth/login`, loginPayload, params);
  check(authRes, {
    'auth status is 200/401/404/429': (r) => r.status === 200 || r.status === 401 || r.status === 404 || r.status === 429,
  });

  sleep(0.5);
}
