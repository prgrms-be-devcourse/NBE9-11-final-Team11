import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '1m', target: 1000 },   // 1000명까지 점진적 증가
    { duration: '2m', target: 5000 },   // 5000명까지 증가
    { duration: '2m', target: 10000 },  // 1만 명까지 증가
    { duration: '3m', target: 10000 },  // 1만 명 유지
    { duration: '1m', target: 0 },      // 점진적 감소
  ],
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const EMAIL = __ENV.LOGIN_EMAIL || 'test@example.com';
const PASSWORD = __ENV.LOGIN_PASSWORD || 'password1234';

export default function () {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email: EMAIL, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  check(res, {
    'status is 200': (r) => r.status === 200,
    'has accessToken cookie': (r) => r.cookies['accessToken'] !== undefined,
  });

  sleep(1);
}
