'use strict';
// OpenToken reader/writer, written out step by step so the envelope is visible.
// Format: IETF draft-smith-opentoken-02 (the format PingFederate's OpenToken adapter emits).
//
// A real app team would normally use Ping's own OpenToken agent library. This file exists so
// the lab has no proprietary dependency and so you can read every step.
//
// Binary layout (before base64):
//   "OTK" | version(1) | cipherSuite(1) | HMAC-SHA1(20) | ivLen(1) | iv | keyInfoLen(1) | keyInfo
//         | payloadLen(2, big-endian) | payload
// payload = key=value lines  ->  zlib DEFLATE  ->  encrypt (CBC)
// key     = PBKDF2-HMAC-SHA1(password, salt = 8 zero bytes, 1000 iterations)
// HMAC    = over version, cipherSuite, iv, keyInfo and the CLEAR-TEXT payload

const crypto = require('node:crypto');
const zlib = require('node:zlib');

// Cipher suite 0 is "no encryption, no key" — never accept it. Suite 3 (3DES) is obsolete.
const SUITES = {
  1: { name: 'aes-256-cbc', keyBytes: 32, ivBytes: 16 },
  2: { name: 'aes-128-cbc', keyBytes: 16, ivBytes: 16 },
};
const ZERO_SALT = Buffer.alloc(8);

function deriveKey(password, suite) {
  return crypto.pbkdf2Sync(password, ZERO_SALT, 1000, suite.keyBytes, 'sha1');
}

function mac(key, version, suiteId, iv, keyInfo, clearPayload) {
  const h = crypto.createHmac('sha1', key);
  h.update(Buffer.from([version, suiteId]));
  h.update(iv);
  if (keyInfo.length) h.update(keyInfo);
  h.update(clearPayload);
  return h.digest();
}

// OpenToken's own base64 flavour: URL-safe alphabet, and '*' instead of '=' padding.
const fromOtkBase64 = (s) => Buffer.from(s.replace(/\*/g, '=').replace(/-/g, '+').replace(/_/g, '/'), 'base64');
const toOtkBase64 = (b) => b.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '*');

/** Decrypt and integrity-check a token. Returns { suiteId, attributes }. Throws on any problem. */
function decode(token, password) {
  if (!password) throw new Error('no OpenToken password configured');
  const buf = fromOtkBase64(String(token).trim());
  if (buf.length < 30 || buf.toString('latin1', 0, 3) !== 'OTK') throw new Error('not an OpenToken (missing OTK header)');
  let i = 3;
  const version = buf[i++];
  if (version !== 1) throw new Error(`unsupported OpenToken version ${version}`);
  const suiteId = buf[i++];
  const suite = SUITES[suiteId];
  if (!suite) throw new Error(`cipher suite ${suiteId} is not accepted`);
  const theirMac = buf.subarray(i, i + 20); i += 20;
  const ivLen = buf[i++];
  if (ivLen !== suite.ivBytes) throw new Error('unexpected IV length');
  const iv = buf.subarray(i, i + ivLen); i += ivLen;
  const keyInfoLen = buf[i++];
  const keyInfo = buf.subarray(i, i + keyInfoLen); i += keyInfoLen;
  const payloadLen = buf.readUInt16BE(i); i += 2;
  const cipherText = buf.subarray(i, i + payloadLen);
  if (cipherText.length !== payloadLen) throw new Error('truncated token');

  const key = deriveKey(password, suite);
  let clear;
  try {
    const d = crypto.createDecipheriv(suite.name, key, iv);
    clear = zlib.inflateSync(Buffer.concat([d.update(cipherText), d.final()]));
  } catch {
    throw new Error('could not decrypt (wrong password or corrupted token)');
  }
  const ourMac = mac(key, version, suiteId, iv, keyInfo, clear);
  if (!crypto.timingSafeEqual(ourMac, theirMac)) throw new Error('integrity check failed (HMAC mismatch)');

  return { suiteId, attributes: parsePayload(clear.toString('utf8')) };
}

/** key=value lines -> object. A key that repeats becomes an array (multi-valued attribute). */
function parsePayload(text) {
  const out = {};
  for (const line of text.split(/\r?\n/)) {
    const eq = line.indexOf('=');
    if (eq < 1) continue;
    const k = line.slice(0, eq).trim();
    const v = line.slice(eq + 1).trim();
    if (k in out) out[k] = [].concat(out[k], v);
    else out[k] = v;
  }
  return out;
}

/** Check the token's own time window. skewSeconds forgives small clock differences. */
function checkTimes(attributes, now = new Date(), skewSeconds = 120) {
  const skew = skewSeconds * 1000;
  const nb = Date.parse(attributes['not-before']);
  const noa = Date.parse(attributes['not-on-or-after']);
  if (Number.isNaN(nb) || Number.isNaN(noa)) throw new Error('token has no valid not-before / not-on-or-after');
  if (now.getTime() + skew < nb) throw new Error('token is not valid yet');
  if (now.getTime() - skew >= noa) throw new Error('token has expired');
  return { notBefore: new Date(nb), notOnOrAfter: new Date(noa) };
}

/** Build a token. Used by the tests and by scripts/mint-token.js to rehearse without PingFederate. */
function encode(attributes, password, suiteId = 2, lifetimeSeconds = 300) {
  const suite = SUITES[suiteId];
  if (!suite) throw new Error(`cipher suite ${suiteId} is not supported`);
  const now = new Date();
  const iso = (d) => d.toISOString().replace(/\.\d{3}Z$/, 'Z');
  const all = {
    'not-before': iso(now),
    'not-on-or-after': iso(new Date(now.getTime() + lifetimeSeconds * 1000)),
    'renew-until': iso(new Date(now.getTime() + 12 * 3600 * 1000)),
    ...attributes,
  };
  const lines = [];
  for (const [k, v] of Object.entries(all)) for (const one of [].concat(v)) lines.push(`${k}=${one}`);
  const clear = Buffer.from(lines.join('\n'), 'utf8');
  const key = deriveKey(password, suite);
  const iv = crypto.randomBytes(suite.ivBytes);
  const c = crypto.createCipheriv(suite.name, key, iv);
  const cipherText = Buffer.concat([c.update(zlib.deflateSync(clear)), c.final()]);
  const len = Buffer.alloc(2); len.writeUInt16BE(cipherText.length);
  return toOtkBase64(Buffer.concat([
    Buffer.from('OTK'), Buffer.from([1, suiteId]), mac(key, 1, suiteId, iv, Buffer.alloc(0), clear),
    Buffer.from([iv.length]), iv, Buffer.from([0]), len, cipherText,
  ]));
}

module.exports = { decode, encode, checkTimes, parsePayload, SUITES };
