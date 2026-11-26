/**
 * Retail barcode helpers. Readers can return the same item in different forms: UPC-E is a
 * compressed form of UPC-A, and UPC-A can be read as EAN-13 with a leading 0. UPC-E is expanded
 * here so the same item always gets the same code; the server treats codes that differ only in
 * leading zeros as equal.
 */

/**
 * @param {string} digits a GTIN without its check digit
 * @returns {string} the check digit
 */
export const gtinCheckDigit = (digits) => {
  // From the right, digits are weighted 3, 1, 3, 1, ...
  const sum = digits.split('').reverse()
    .reduce((total, digit, index) => total + Number(digit) * (index % 2 === 0 ? 3 : 1), 0);
  return String((10 - (sum % 10)) % 10);
};

/**
 * Expands a UPC-E code to its UPC-A form.
 * @param {string} upcE 8 digits (number system, 6 digits, check digit), or the 6 middle digits
 *   (number system 0 is then assumed and the check digit is computed)
 * @returns {string|null} the 12-digit UPC-A code, or null if it isn't a valid UPC-E code
 */
export const expandUpcE = (upcE) => {
  if (!/^(\d{6}|\d{8})$/.test(upcE || '')) {
    return null;
  }
  const numberSystem = upcE.length === 8 ? upcE[0] : '0';
  const digits = upcE.length === 8 ? upcE.slice(1, 7) : upcE;
  // Only number systems 0 and 1 have a UPC-E form
  if (numberSystem !== '0' && numberSystem !== '1') {
    return null;
  }

  const [d1, d2, d3, d4, d5, d6] = digits;
  let body;
  switch (d6) {
    case '0':
    case '1':
    case '2':
      body = `${d1}${d2}${d6}0000${d3}${d4}${d5}`;
      break;
    case '3':
      body = `${d1}${d2}${d3}00000${d4}${d5}`;
      break;
    case '4':
      body = `${d1}${d2}${d3}${d4}00000${d5}`;
      break;
    default:
      body = `${d1}${d2}${d3}${d4}${d5}0000${d6}`;
  }

  const withoutCheck = `${numberSystem}${body}`;
  const check = upcE.length === 8 ? upcE[7] : gtinCheckDigit(withoutCheck);
  return `${withoutCheck}${check}`;
};

/**
 * @param {string} code a scanned or typed barcode
 * @param {string} [format] the reader's format name, e.g. "UPC_E"
 * @returns {string} the code's digits, with UPC-E expanded to UPC-A
 */
export const normalizeBarcode = (code, format) => {
  const digits = (code || '').replace(/[\s-]/g, '');
  if (format === 'UPC_E') {
    return expandUpcE(digits) || digits;
  }
  return digits;
};
