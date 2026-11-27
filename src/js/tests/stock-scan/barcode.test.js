import { expandUpcE, gtinCheckDigit, normalizeBarcode } from 'components/stock-scan/barcode';

describe('expandUpcE', () => {
  it.each([
    // Last digit 0–2: manufacturer digits, the last digit, then 0000
    ['04252614', '042100005264'],
    ['01200003', '012000000003'],
    // Last digit 3
    ['01234531', '012300000451'],
    // Last digit 4
    ['01234547', '012340000057'],
    // Last digit 5–9
    ['01234565', '012345000065'],
    ['11234565', '112345000065'],
  ])('expands %s to %s', (upcE, upcA) => {
    expect(expandUpcE(upcE)).toBe(upcA);
  });

  it('computes the check digit for the 6-digit form', () => {
    expect(expandUpcE('425261')).toBe('042100005264');
  });

  it.each([null, '', '1234567', '123456789', 'abcdefgh', '21234565'])('rejects %p', (code) => {
    expect(expandUpcE(code)).toBeNull();
  });
});

describe('gtinCheckDigit', () => {
  it.each([
    ['01234567890', '5'], // UPC-A 012345678905
    ['400638133393', '1'], // EAN-13 4006381333931
    ['9638507', '4'], // EAN-8 96385074
  ])('for %s is %s', (digits, check) => {
    expect(gtinCheckDigit(digits)).toBe(check);
  });
});

describe('normalizeBarcode', () => {
  it('removes spaces and dashes', () => {
    expect(normalizeBarcode(' 0-12345 67890-5 ')).toBe('012345678905');
  });

  it('expands UPC-E', () => {
    expect(normalizeBarcode('04252614', 'UPC_E')).toBe('042100005264');
  });

  it('leaves other formats as they are', () => {
    expect(normalizeBarcode('4006381333931', 'EAN_13')).toBe('4006381333931');
    expect(normalizeBarcode('96385074', 'EAN_8')).toBe('96385074');
  });

  it('keeps an invalid UPC-E code as read', () => {
    expect(normalizeBarcode('21234565', 'UPC_E')).toBe('21234565');
  });

  it('handles nothing', () => {
    expect(normalizeBarcode(undefined)).toBe('');
  });
});
