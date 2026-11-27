import { gs1Date, parseGs1 } from 'components/stock-scan/gs1';

const GS = '\u001d';
const today = new Date(2026, 10, 26);

describe('parseGs1', () => {
  it('reads a DSCSA DataMatrix, discarding the serial number', () => {
    const text = `${GS}0100343547422103172806301012345AB${GS}21SERIAL0001`;
    expect(parseGs1(text, today)).toEqual({
      gtin: '00343547422103', ndc: null, lot: '12345AB', expirationDate: '2028-06-30',
    });
  });

  it('reads elements in any order, with a symbology identifier', () => {
    const text = `]d201003435474221032112345${GS}10LOT-9${GS}17290100`;
    expect(parseGs1(text, today)).toEqual({
      gtin: '00343547422103', ndc: null, lot: 'LOT-9', expirationDate: '2029-01-31',
    });
  });

  it('reads the 12-digit NDC in AI 715', () => {
    expect(parseGs1(`715043547042210${GS}10L1${GS}17300101`, today)).toEqual({
      gtin: null, ndc: '043547042210', lot: 'L1', expirationDate: '2030-01-01',
    });
  });

  it('accepts FNC1 written as <GS>', () => {
    expect(parseGs1('010034354742210310LOT<GS>17280630', today).lot).toBe('LOT');
  });

  it('keeps what it read before an unknown AI', () => {
    expect(parseGs1(`01003435474221031728063099INTERNAL${GS}10LOT`, today)).toEqual({
      gtin: '00343547422103', ndc: null, lot: null, expirationDate: '2028-06-30',
    });
  });

  it('skips fixed-length measures and other known AIs', () => {
    expect(parseGs1(`0100343547422103310200150011260101${GS}10ABC`, today).lot).toBe('ABC');
  });

  it.each([
    ['plain text', 'hello world'],
    ['a URL', 'https://example.org/01/00343547422103'],
    ['a truncated GTIN', '01003435474'],
    ['no GTIN or NDC', `10LOT${GS}17280630`],
    ['nothing', ''],
  ])('returns null for %s', (name, text) => {
    expect(parseGs1(text, today)).toBeNull();
  });

  it('gives no expiry for an invalid date', () => {
    expect(parseGs1('010034354742210317281340', today).expirationDate).toBeNull();
  });
});

describe('gs1Date', () => {
  it.each([
    ['280630', '2028-06-30'],
    ['280600', '2028-06-30'],
    ['280200', '2028-02-29'],
    ['270200', '2027-02-28'],
    // Within 50 years ahead: this century; further: the last one
    ['760101', '2076-01-01'],
    ['770101', '1977-01-01'],
  ])('reads %p as %p', (yymmdd, expected) => {
    expect(gs1Date(yymmdd, today)).toBe(expected);
  });

  it.each(['281301', '280231', '2806', 'abcdef', undefined])('rejects %p', (yymmdd) => {
    expect(gs1Date(yymmdd, today)).toBeNull();
  });
});
