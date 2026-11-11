'use strict';
const test = require('node:test');
const assert = require('node:assert');
const { once } = require('node:events');
const ot = require('./opentoken');

const PW = 'lab-only-password';
process.env.OPENTOKEN_PASSWORD = PW;
process.env.PORT = '0';
const { server } = require('./server');

test('round trip, both AES suites, multi-valued attribute', () => {
  for (const suite of [1, 2]) {
    const t = ot.encode({ subject: 'alice@bank-a.example', group: ['a', 'b'] }, PW, suite);
    const { suiteId, attributes } = ot.decode(t, PW);
    assert.equal(suiteId, suite);
    assert.equal(attributes.subject, 'alice@bank-a.example');
    assert.deepEqual(attributes.group, ['a', 'b']);
    ot.checkTimes(attributes);
  }
});

test('wrong password, tampering and the null cipher are rejected', () => {
  const t = ot.encode({ subject: 'x' }, PW);
  assert.throws(() => ot.decode(t, 'other'), /decrypt|integrity/);
  const raw = Buffer.from(t.replace(/\*/g, '=').replace(/-/g, '+').replace(/_/g, '/'), 'base64');
  raw[10] ^= 0xff; // flip a bit inside the HMAC
  assert.throws(() => ot.decode(raw.toString('base64'), PW), /integrity/);
  raw[4] = 0;      // claim "cipher suite 0"
  assert.throws(() => ot.decode(raw.toString('base64'), PW), /not accepted/);
});

test('expired token fails the time check', () => {
  const { attributes } = ot.decode(ot.encode({ subject: 'x' }, PW, 2, -600), PW);
  assert.throws(() => ot.checkTimes(attributes), /expired/);
});

test('landing endpoint: accept, replay, unknown user, wrong org, no token', async (t) => {
  server.listen(0);
  await once(server, 'listening');
  t.after(() => server.close());
  const base = `http://localhost:${server.address().port}`;
  const get = (p) => fetch(base + p, { redirect: 'manual' });

  const good = ot.encode({ subject: 'alice@bank-a.example' }, PW);
  const r1 = await get(`/sso/launch/org-1001?opentoken=${good}`);
  assert.equal(r1.status, 303);
  const cookie = r1.headers.get('set-cookie').split(';')[0];
  const homePage = await (await fetch(base + '/', { headers: { cookie } })).text();
  assert.match(homePage, /Alice Anand/);

  assert.equal((await get(`/sso/launch/org-1001?opentoken=${good}`)).status, 401);            // replay
  const carol = ot.encode({ subject: 'carol@bank-a.example' }, PW);
  assert.equal((await get(`/sso/launch/org-1001?opentoken=${carol}`)).status, 403);           // not provisioned
  const alice2 = ot.encode({ subject: 'alice@bank-a.example' }, PW);
  assert.equal((await get(`/sso/launch/org-2002?opentoken=${alice2}`)).status, 403);          // Bank A user on Bank B URL
  assert.equal((await get('/sso/launch/org-9999?opentoken=x')).status, 404);                  // unknown org
  assert.equal((await get('/sso/launch/org-1001')).status, 400);                              // no token

  const post = ot.encode({ subject: 'bob@bank-b.example' }, PW, 1);
  const r2 = await fetch(base + '/sso/launch/org-2002', { method: 'POST', redirect: 'manual', body: new URLSearchParams({ opentoken: post }) });
  assert.equal(r2.status, 303);                                                               // form POST transport
});
