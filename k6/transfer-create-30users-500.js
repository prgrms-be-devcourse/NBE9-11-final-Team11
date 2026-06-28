import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';

const TARGET_VUS = Number(__ENV.TARGET_VUS || '500');
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PASSWORD = __ENV.LOGIN_PASSWORD || "Qwer1234#'";
const SEND_AMOUNT_KRW = Number(__ENV.SEND_AMOUNT_KRW || '10000');
const REASON = __ENV.REASON || 'LIVING_EXPENSES';

export const options = {
  stages: [
    { duration: '30s', target: TARGET_VUS },
    { duration: '1m', target: TARGET_VUS },
    { duration: '30s', target: 0 },
  ],
};

const USERS = new SharedArray('k6 remittance users', () =>
  Array.from({ length: 30 }, (_, index) => {
    const number = String(index + 1).padStart(2, '0');

    return {
      email: `k6user${number}@example.com`,
      password: PASSWORD,
      recipientId: index + 4,
    };
  })
);

const sessions = {};
let printedFailures = 0;

function pickUser() {
  return USERS[(__VU - 1) % USERS.length];
}

function authHeaders(accessToken, extraHeaders = {}) {
  return {
    'Content-Type': 'application/json',
    Cookie: `accessToken=${accessToken}`,
    ...extraHeaders,
  };
}

function login(user) {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email: user.email, password: user.password }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { name: 'POST /api/v1/auth/login' },
    }
  );

  const ok = check(res, {
    'login status is 200': (r) => r.status === 200,
    'login has accessToken cookie': (r) => r.cookies.accessToken !== undefined,
  });

  if (!ok) {
    if (printedFailures < 5) {
      printedFailures += 1;
      console.log(`login failed email=${user.email} status=${res.status} body=${res.body}`);
    }
    return null;
  }

  return res.cookies.accessToken[0].value;
}

function getAccessToken(user) {
  if (!sessions[user.email]) {
    sessions[user.email] = login(user);
  }

  return sessions[user.email];
}

function createQuote(user, accessToken) {
  const res = http.post(
    `${BASE_URL}/api/v1/transfers/quote`,
    JSON.stringify({
      recipientId: user.recipientId,
      sendAmountKrw: SEND_AMOUNT_KRW,
      reason: REASON,
    }),
    {
      headers: authHeaders(accessToken),
      tags: { name: 'POST /api/v1/transfers/quote' },
    }
  );

  const ok = check(res, {
    'quote status is 200': (r) => r.status === 200,
    'quote has quoteId': (r) => {
      try {
        return Boolean(r.json('quoteId'));
      } catch {
        return false;
      }
    },
  });

  if (!ok) {
    if (printedFailures < 10) {
      printedFailures += 1;
      console.log(`quote failed email=${user.email} status=${res.status} body=${res.body}`);
    }
    return null;
  }

  return res.json('quoteId');
}

function createTransfer(user, accessToken, quoteId) {
  const idempotencyKey = `k6-create-30users-500-${user.recipientId}-${Date.now()}-${__VU}-${__ITER}`;

  const res = http.post(
    `${BASE_URL}/api/v1/transfers`,
    JSON.stringify({
      quoteId,
      reason: REASON,
      reasonDetail: 'k6 30 users 500 VU transfer create load test',
    }),
    {
      headers: authHeaders(accessToken, { 'Idempotency-Key': idempotencyKey }),
      tags: { name: 'POST /api/v1/transfers' },
    }
  );

  check(res, {
    'transfer create status is 201': (r) => r.status === 201,
    'transfer has transferId': (r) => {
      try {
        return Boolean(r.json('transferId'));
      } catch {
        return false;
      }
    },
  });

  if (res.status !== 201 && printedFailures < 20) {
    printedFailures += 1;
    console.log(`transfer create failed email=${user.email} status=${res.status} body=${res.body}`);
  }
}

export default function () {
  const user = pickUser();
  const accessToken = getAccessToken(user);

  if (!accessToken) {
    sleep(1);
    return;
  }

  const quoteId = createQuote(user, accessToken);

  if (quoteId) {
    createTransfer(user, accessToken, quoteId);
  }

  sleep(1);
}
