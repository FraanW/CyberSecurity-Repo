// QR code (SVG) for the device-flow verification URL — self-hosted, no external CDN.
const QRCode = require('qrcode');
exports.handler = async (event) => {
  const data = (event.queryStringParameters || {}).data;
  if (!data) return { statusCode: 400, body: 'missing ?data' };
  const svg = await QRCode.toString(data, { type: 'svg', margin: 1, width: 240 });
  return { statusCode: 200, headers: { 'Content-Type': 'image/svg+xml', 'Cache-Control': 'no-store' }, body: svg };
};
