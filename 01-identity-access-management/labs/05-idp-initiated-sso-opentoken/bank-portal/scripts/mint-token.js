#!/usr/bin/env node
'use strict';
// Rehearse the app WITHOUT PingFederate: mint an OpenToken the way the adapter would.
// Lab use only. Usage:
//   OPENTOKEN_PASSWORD=<same as the app> node scripts/mint-token.js alice@bank-a.example org-1001 [app-origin]
const { encode } = require('../opentoken');
const [subject, orgId, origin = 'http://localhost:3000'] = process.argv.slice(2);
if (!subject || !orgId || !process.env.OPENTOKEN_PASSWORD) {
  console.error('usage: OPENTOKEN_PASSWORD=... node scripts/mint-token.js <subject> <org-id> [app-origin]');
  process.exit(1);
}
const token = encode({ subject, email: subject }, process.env.OPENTOKEN_PASSWORD);
console.log(`${origin.replace(/\/+$/, '')}/sso/launch/${orgId}?opentoken=${token}`);
