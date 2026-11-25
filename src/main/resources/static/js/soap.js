/* Minimal SOAP 1.1 client for the StockSense endpoint (document/literal, namespace urn:stocksense:ws). */
const SoapClient = (() => {
  const NS = 'urn:stocksense:ws';
  const ENV = 'http://schemas.xmlsoap.org/soap/envelope/';
  // Wrapper elements whose children are repeated items -> always converted to arrays.
  const LISTS = new Set(['stocks', 'bars', 'signals', 'series', 'models', 'drivers', 'predictions', 'metrics', 'items', 'providers']);

  class SoapError extends Error {
    constructor(code, message) { super(message); this.code = code; }
  }

  const esc = s => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

  function build(op, params) {
    let inner = '';
    for (const [k, v] of Object.entries(params || {})) {
      if (v === undefined || v === null || v === '') continue;
      if (Array.isArray(v)) {
        const item = k.replace(/s$/, '');
        inner += `<tf:${k}>${v.map(x => `<tf:${item}>${esc(x)}</tf:${item}>`).join('')}</tf:${k}>`;
      } else {
        inner += `<tf:${k}>${esc(v)}</tf:${k}>`;
      }
    }
    return `<?xml version="1.0" encoding="UTF-8"?>` +
      `<soapenv:Envelope xmlns:soapenv="${ENV}" xmlns:tf="${NS}"><soapenv:Body>` +
      `<tf:${op}>${inner}</tf:${op}></soapenv:Body></soapenv:Envelope>`;
  }

  function toObj(el) {
    const kids = Array.from(el.children);
    if (!kids.length) return LISTS.has(el.localName) ? [] : el.textContent;
    if (LISTS.has(el.localName)) return kids.map(toObj);
    const o = {};
    for (const k of kids) o[k.localName] = toObj(k);
    return o;
  }

  async function call(op, params) {
    let res;
    try {
      res = await fetch('/ws/stocksense', {
        method: 'POST',
        headers: { 'Content-Type': 'text/xml; charset=utf-8', 'SOAPAction': `urn:stocksense:ws/${op}` },
        body: build(op, params),
      });
    } catch (e) {
      throw new SoapError('NETWORK', 'Cannot reach the StockSense server');
    }
    const text = await res.text();
    const doc = new DOMParser().parseFromString(text, 'text/xml');
    const fault = doc.getElementsByTagNameNS(ENV, 'Fault')[0];
    if (fault) {
      const code = doc.getElementsByTagNameNS(NS, 'code')[0]?.textContent || 'FAULT';
      const msg = fault.getElementsByTagName('faultstring')[0]?.textContent || 'Request failed';
      throw new SoapError(code, msg);
    }
    const body = doc.getElementsByTagNameNS(ENV, 'Body')[0];
    const resp = body && body.firstElementChild;
    if (!resp) throw new SoapError('BAD_RESPONSE', 'Unexpected response from server');
    return toObj(resp);
  }

  return { call, SoapError };
})();
