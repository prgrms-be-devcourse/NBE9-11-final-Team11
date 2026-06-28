import http from 'k6/http';
import { check } from 'k6';

export const options = {
    scenarios: {
        baseline: {
            executor: 'constant-vus',
            vus: 50,
            duration: '30s',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<10000'],
    },
};

const BASE_URL = 'http://localhost:8080';

export function setup() {
    const res = http.post(
        `${BASE_URL}/api/v1/auth/login`,
        JSON.stringify({ email: 'hong@example.com', password: 'Abcd1234!' }),
        { headers: { 'Content-Type': 'application/json' } }
    );

    check(res, { '로그인 200': r => r.status === 200 });

    const token = res.cookies.accessToken && res.cookies.accessToken[0].value;
    if (!token) throw new Error(`로그인 실패: status=${res.status}, body=${res.body}`);
    return { token };
}

export default function (data) {
    // 날짜 필터로 전체 데이터 중 일부만 조회 → 인덱스 효과가 나타나는 시나리오
    const res = http.get(
        `${BASE_URL}/api/v1/admin/transactions?page=0&size=20&from=2025-06-01&to=2025-06-30`,
        { headers: { Cookie: `accessToken=${data.token}` } }
    );
    check(res, { '200 OK': r => r.status === 200 });
}
