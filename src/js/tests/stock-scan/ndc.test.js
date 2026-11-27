import { looksLikeNdc, normalizeTypedCode } from 'components/stock-scan/ndc';

describe('looksLikeNdc', () => {
  it.each([
    '43547-422-10', '0002-3227-30', '43547-0422-10', '123456-1234-12', '4354742210', '43547042210',
    'NDC 43547-422-10', ' ndc: 4354742210 ',
  ])('treats %p as an NDC', (text) => {
    expect(looksLikeNdc(text)).toBe(true);
  });

  it.each([
    '012345678905', '0343547422103', '96385074', '0-12345-67890-5', '4354-742-210', '', null,
  ])('treats %p as something else', (text) => {
    expect(looksLikeNdc(text)).toBe(false);
  });
});

describe('normalizeTypedCode', () => {
  it('keeps an NDC as typed, without an NDC prefix', () => {
    expect(normalizeTypedCode(' NDC 43547-422-10 ')).toBe('43547-422-10');
    expect(normalizeTypedCode('4354742210')).toBe('4354742210');
  });

  it('reduces a barcode to its digits', () => {
    expect(normalizeTypedCode(' 0 12345 67890 5 ')).toBe('012345678905');
    expect(normalizeTypedCode('0-12345-67890-5')).toBe('012345678905');
  });
});
