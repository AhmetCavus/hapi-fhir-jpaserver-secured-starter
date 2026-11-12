import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { createHash, randomBytes } from 'node:crypto';

const required = (name) => {
  const value = process.env[name];
  assert(value, `Required server-side setting: ${name}`);
  return value;
};
const issuer = new URL(required('OIDC_ISSUER'));
const target = new URL(process.env.PRIVATE_FHIR_BASE_URL ?? 'http://127.0.0.1:18083');
assert(
  target.protocol === 'http:' &&
    target.hostname === '127.0.0.1' &&
    target.pathname === '/' &&
    !target.username &&
    !target.password &&
    !target.search &&
    !target.hash,
  'Verification requires loopback-only HAPI',
);
assert(issuer.pathname === '/realms/epa-demo', 'The existing approved realm is required');
const sensitive = new Set();
const remember = (value) => {
  if (typeof value === 'string' && value.length >= 8) {
    sensitive.add(value);
  }
  return value;
};
const secret = () => remember(randomBytes(32).toString('base64url'));
const username = `hapi-security-${randomBytes(10).toString('hex')}`;
const password = secret();
const providerCookies = new Map();
let userId;
let createdUser = false;
let adminToken;
let tokenEndpoint;
let authorizationEndpoint;
let parEndpoint;
let exchanges = 0;
let parRequests = 0;
let assertions = 0;
let syntheticVersionId;

function check(condition, message) {
  assertions += 1;
  assert(condition, message);
}

async function fetchPrivate(url, options = {}) {
  return fetch(url, { ...options, redirect: 'manual', signal: AbortSignal.timeout(10000) });
}

async function admin(path, method = 'GET', body) {
  const response = await fetchPrivate(`${issuer.origin}/admin/realms/epa-demo${path}`, {
    method,
    headers: { authorization: `Bearer ${adminToken}`, 'content-type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  check(response.ok, 'Disposable-identity administration failed');
  return response.status === 201 || response.status === 204 ? undefined : response.json();
}

function saveCookies(response) {
  for (const raw of response.headers.getSetCookie()) {
    const pair = raw.split(';')[0];
    const index = pair.indexOf('=');
    const name = pair.slice(0, index);
    const value = pair.slice(index + 1);
    if (value) {
      providerCookies.set(name, remember(value));
    } else {
      providerCookies.delete(name);
    }
  }
}

function providerHeaders() {
  return { cookie: [...providerCookies].map(([name, value]) => `${name}=${value}`).join('; ') };
}

async function authorize(profile, scope) {
  const standard = profile === 'standard';
  const clientId = required(standard ? 'PLAYGROUND_OIDC_CLIENT_ID' : 'OIDC_CLIENT_ID');
  const clientSecret = remember(
    required(standard ? 'PLAYGROUND_OIDC_CLIENT_SECRET' : 'OIDC_CLIENT_SECRET'),
  );
  const redirectUri = required(
    standard ? 'PLAYGROUND_OIDC_REDIRECT_URI' : 'OIDC_DEFAULT_REDIRECT_URI',
  );
  const authorization = remember(
    `Basic ${Buffer.from(`${clientId}:${clientSecret}`).toString('base64')}`,
  );
  const state = secret();
  const nonce = secret();
  const verifier = secret();
  const parameters = new URLSearchParams({
    client_id: clientId,
    redirect_uri: redirectUri,
    response_type: 'code',
    scope,
    state,
    nonce,
    code_challenge_method: 'S256',
    code_challenge: createHash('sha256').update(verifier).digest('base64url'),
  });
  let url = new URL(authorizationEndpoint);
  if (standard) {
    url.search = parameters.toString();
  } else {
    const pushed = await fetchPrivate(parEndpoint, {
      method: 'POST',
      headers: { authorization, 'content-type': 'application/x-www-form-urlencoded' },
      body: parameters,
    });
    check(pushed.status === 201, 'Existing ePA PAR failed');
    const reference = await pushed.json();
    check(typeof reference.request_uri === 'string', 'Invalid ePA PAR reference');
    url.search = new URLSearchParams({
      client_id: clientId,
      request_uri: reference.request_uri,
    }).toString();
    parRequests += 1;
  }
  let code;
  for (let step = 0; step < 8; step += 1) {
    const callback = new URL(redirectUri);
    if (url.origin === callback.origin && url.pathname === callback.pathname) {
      check(url.searchParams.get('state') === state, 'Provider correlation failed');
      code = remember(url.searchParams.get('code'));
      break;
    }
    check(url.origin === issuer.origin, 'Untrusted provider navigation');
    const response = await fetchPrivate(url, { headers: providerHeaders() });
    saveCookies(response);
    if ([302, 303].includes(response.status)) {
      const location = response.headers.get('location');
      check(location !== null, 'Provider redirect missing');
      await response.body?.cancel();
      url = new URL(location, url);
      continue;
    }
    check(response.status === 200, 'Provider login form unavailable');
    const html = await response.text();
    const form = /<form\b[^>]*\bid="kc-form-login"[^>]*>/.exec(html)?.[0];
    const action = form && /\baction="([^"]+)"/.exec(form)?.[1];
    check(Boolean(action), 'Provider login form action missing');
    const formUrl = new URL(action.replaceAll('&amp;', '&'), url);
    check(formUrl.origin === issuer.origin, 'Untrusted provider form');
    const loggedIn = await fetchPrivate(formUrl, {
      method: 'POST',
      headers: { ...providerHeaders(), 'content-type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ username, password, credentialId: '' }),
    });
    saveCookies(loggedIn);
    const location = loggedIn.headers.get('location');
    check(
      [302, 303].includes(loggedIn.status) && location !== null,
      'Disposable provider login failed',
    );
    await loggedIn.body?.cancel();
    url = new URL(location, formUrl);
  }
  check(typeof code === 'string' && code.length > 0, 'Authorization did not return a code');
  const body = new URLSearchParams({
    grant_type: 'authorization_code',
    code,
    redirect_uri: redirectUri,
    code_verifier: verifier,
  }).toString();
  remember(body);
  const response = await fetchPrivate(tokenEndpoint, {
    method: 'POST',
    headers: { authorization, 'content-type': 'application/x-www-form-urlencoded' },
    body,
  });
  check(response.status === 200, 'Real code exchange failed');
  const tokens = await response.json();
  for (const key of ['access_token', 'id_token', 'refresh_token']) {
    remember(tokens[key]);
  }
  check(
    typeof tokens.access_token === 'string' && typeof tokens.id_token === 'string',
    'Missing provider tokens',
  );
  exchanges += 1;
  return tokens;
}

async function resource(
  token,
  expectedStatus,
  reason,
  path = '/fhir/Basic/playground-demo',
  method = 'GET',
) {
  const response = await fetchPrivate(new URL(path, target), {
    method,
    headers: token === undefined ? {} : { authorization: `Bearer ${token}` },
  });
  check(response.status === expectedStatus, 'Resource response status mismatch');
  check(
    response.headers.get('X-Playground-Decision') ===
      (expectedStatus === 200 ? 'allowed' : 'denied'),
    'Decision report mismatch',
  );
  check(response.headers.get('X-Playground-Reason') === reason, 'Decision reason mismatch');
  const body = await response.text();
  if (expectedStatus === 200) {
    const basic = JSON.parse(body);
    check(
      basic.resourceType === 'Basic' && basic.id === 'playground-demo',
      'Synthetic resource mismatch',
    );
    check(
      Object.keys(basic).every((key) => ['resourceType', 'id', 'meta', 'code'].includes(key)),
      'Unexpected resource fields',
    );
    syntheticVersionId = basic.meta?.versionId;
    check(
      typeof syntheticVersionId === 'string' && /^\d+$/.test(syntheticVersionId),
      'Synthetic version metadata missing',
    );
  } else {
    check(body === '', 'Denial disclosed response data');
  }
}

async function main() {
  const metadataResponse = await fetchPrivate(`${issuer.href}/.well-known/openid-configuration`);
  check(metadataResponse.ok, 'Private issuer discovery unavailable');
  const metadata = await metadataResponse.json();
  check(metadata.issuer === issuer.href, 'Private issuer mismatch');
  for (const name of [
    'authorization_endpoint',
    'token_endpoint',
    'pushed_authorization_request_endpoint',
  ]) {
    check(
      typeof metadata[name] === 'string' && new URL(metadata[name]).origin === issuer.origin,
      'Untrusted discovered endpoint',
    );
  }
  authorizationEndpoint = metadata.authorization_endpoint;
  tokenEndpoint = metadata.token_endpoint;
  parEndpoint = metadata.pushed_authorization_request_endpoint;
  const adminPassword = remember(required('KEYCLOAK_ADMIN_PASSWORD'));
  const authenticated = await fetchPrivate(
    `${issuer.origin}/realms/master/protocol/openid-connect/token`,
    {
      method: 'POST',
      headers: { 'content-type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        client_id: 'admin-cli',
        grant_type: 'password',
        username: required('KEYCLOAK_ADMIN_USERNAME'),
        password: adminPassword,
      }),
    },
  );
  check(authenticated.ok, 'Private administrator authentication failed');
  adminToken = remember((await authenticated.json()).access_token);
  try {
    await admin('/users', 'POST', {
      username,
      enabled: true,
      firstName: 'Fictional',
      lastName: 'HapiVerifier',
      email: `${username}@example.invalid`,
      requiredActions: [],
      credentials: [{ type: 'password', value: password, temporary: false }],
    });
    createdUser = true;
    const users = await admin(`/users?username=${username}&exact=true`);
    check(users.length === 1 && users[0].username === username, 'Disposable user lookup failed');
    userId = users[0].id;
    const standard = await authorize('standard', 'openid demo.fhir.read');
    check(parRequests === 0, 'Standard authorization unexpectedly used PAR');
    await resource(standard.access_token, 200, 'demo-read-allowed');
    await resource(undefined, 401, 'missing-token');
    await resource('fictional-invalid.jwt', 401, 'invalid-token');
    await resource(standard.id_token, 401, 'invalid-token');
    const narrow = await authorize('standard', 'openid');
    await resource(narrow.access_token, 403, 'insufficient-permission');
    const epa = await authorize('epa', 'openid');
    await resource(epa.access_token, 401, 'invalid-token');
    for (const [method, path] of [
      ['GET', '/fhir/Patient'],
      ['GET', '/fhir/Basic/other'],
      ['GET', '/fhir/Basic'],
      ['GET', '/fhir/Basic/playground-demo/_history'],
      ['POST', '/fhir'],
      ['PUT', '/fhir/Basic/playground-demo'],
      ['GET', '/mcp/messages'],
      ['GET', '/cds-services'],
      ['GET', '/fhir/metadata'],
      ['GET', '/actuator/health'],
    ]) {
      await resource(standard.access_token, 403, 'operation-not-allowed', path, method);
    }
    for (const path of ['/actuator/health/liveness', '/actuator/health/readiness']) {
      const probe = await fetchPrivate(new URL(path, target), {
        headers: { authorization: 'Bearer invalid token' },
      });
      check(
        probe.status === 200 && (await probe.text()) === '{"status":"UP"}',
        'Data-free probe failed',
      );
    }
  } finally {
    if (createdUser) {
      if (!userId) {
        const users = await admin(`/users?username=${username}&exact=true`);
        userId = users.find((user) => user.username === username)?.id;
      }
      check(typeof userId === 'string', 'Disposable identity cleanup unavailable');
      await admin(`/users/${userId}`, 'DELETE');
    }
  }
  const logs = spawnSync('docker', ['logs', 'epa-lab-integration-res-server'], {
    encoding: 'utf8',
    maxBuffer: 16 * 1024 * 1024,
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  check(logs.status === 0, 'Isolated HAPI logs unavailable');
  const output = `${logs.stdout ?? ''}\n${logs.stderr ?? ''}`;
  check(
    [...sensitive].every((value) => !output.includes(value)),
    'Sensitive content appeared in HAPI logs',
  );
  console.log(
    JSON.stringify({
      result: 'passed',
      assertions,
      realCodeExchanges: exchanges,
      standardParRequests: parRequests - 1,
      epaParRequests: parRequests,
      disposableUserRemoved: true,
      sensitiveLogValuesAbsent: true,
      syntheticVersionId,
    }),
  );
}

main().catch((error) => {
  const reason =
    error instanceof assert.AssertionError
      ? error.message
      : 'Private upstream or verification operation failed';
  console.error(`Private HAPI security verification failed: ${reason}`);
  process.exitCode = 1;
});
