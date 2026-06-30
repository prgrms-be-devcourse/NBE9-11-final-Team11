import http from 'k6/http';
import { check, sleep } from 'k6';

export let options = {
    vus: 50,
    duration: '30s',
    thresholds: {
        http_req_duration: ['p(95)<500', 'p(99)<1000'],
        http_req_failed: ['rate<0.01'],
    },
};

export default function () {
    let randomUserId = Math.floor(Math.random() * 50000) + 1;
    let randomPage = Math.floor(Math.random() * 50);

    let res = http.get(
        `http://localhost:8080/api/v1/wallets/transactions?page=${randomPage}&size=100`,
        {
            headers: {
                'X-Test-User-Id': randomUserId.toString(),
                'Content-Type': 'application/json',
            },
        }
    );

    check(res, {
        'is status 200': (r) => r.status === 200,
        'has transactions': (r) => r.json('transactionResponseList') !== undefined,
    });

    sleep(0.1);
}