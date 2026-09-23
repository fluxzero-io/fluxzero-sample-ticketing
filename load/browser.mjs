import assert from 'node:assert/strict';

/** One local browser session, using the app's OIDC redirects and opaque cookies. */
export class Browser {
  static completedRequests = 0;
  static samples = [];
  cookies = new Map();
  constructor(base) { this.base = base; }

  async request(path, options = {}) {
    const url = new URL(path, this.base);
    assert.equal(url.origin, this.base, 'Load traffic and login redirects must stay on the configured origin');
    const response = await fetch(url, {
      ...options, redirect: 'manual', signal: AbortSignal.timeout(30_000),
      headers: { Cookie: [...this.cookies].map(([k, v]) => `${k}=${v}`).join('; '), ...options.headers },
    });
    for (const cookie of response.headers.getSetCookie()) {
      const pair = cookie.split(';')[0], split = pair.indexOf('=');
      this.cookies.set(pair.slice(0, split), pair.slice(split + 1));
    }
    return response;
  }

  async follow(path, options) {
    for (let redirects = 0; redirects < 10; redirects++) {
      const response = await this.request(path, options);
      if (![301, 302, 303, 307, 308].includes(response.status)) return response;
      path = new URL(response.headers.get('location'), new URL(path, this.base)).href;
      await response.arrayBuffer();
      options = undefined;
    }
    throw new Error('Too many login redirects');
  }

  async login(subject) {
    const page = await this.follow('/app/login');
    assert.equal(page.status, 200);
    assert.match(await page.text(), /Fluxzero Local IDP/, 'Use the managed local IDP');
    const result = await this.follow(page.url, {
      method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ username: subject }).toString(),
    });
    await result.arrayBuffer();
    assert.deepEqual(await this.get('/app/auth/session'), { authenticated: true, name: subject });
    this.subject = subject;
    return this;
  }

  async json(path, body) {
    const started = performance.now();
    const response = await this.request(path, body === undefined ? {} : {
      method: 'POST', body: JSON.stringify(body),
      headers: { 'Content-Type': 'application/json', Origin: this.base, 'X-Ticketing-Request': '1' },
    });
    const raw = await response.text();
    Browser.completedRequests++;
    Browser.samples.push({ route: `${body === undefined ? 'GET' : 'POST'} ${path.split('?')[0]
      .replace(/[0-9a-f]{8}-[0-9a-f-]{27,}/g, ':id')}`, status: response.status,
      ms: performance.now() - started, bytes: Buffer.byteLength(raw) });
    let data = raw;
    if (raw) { try { data = JSON.parse(raw); } catch { /* Preserve plain HTTP errors. */ } }
    if (!response.ok) throw new HttpError(response.status, path, data);
    return data;
  }
  get(path) { return this.json(path); }
  post(path, body = {}) { return this.json(path, body); }
}

export class HttpError extends Error {
  constructor(status, path, body) {
    super(`HTTP ${status} ${path}: ${typeof body === 'string' ? body : JSON.stringify(body)}`);
    this.status = status; this.body = body;
  }
}
