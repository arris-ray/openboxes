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

/** How long to keep looking for a drug package's DataMatrix after reading its linear barcode. */
export const DATA_MATRIX_WAIT_MS = 1500;

/** @returns {string|null} the 10 NDC digits of a US drug GTIN (GS1 prefix 03), or null */
export const drugNdcDigits = (code) => {
  if (!/^(\d{12,14})$/.test(code || '')) {
    return null;
  }
  const gtin14 = code.padStart(14, '0');
  return gtin14.slice(1, 3) === '03' ? gtin14.slice(3, 13) : null;
};

/**
 * Decides which read to use. Most codes are used as soon as they're read. A drug's linear barcode
 * is held briefly: prescription packages also carry a DataMatrix with the lot and expiry, so if
 * that's read for the same drug within the wait, it's used instead.
 *
 * @param {object} options
 * @param {function} options.onResolve called once, with the read to use
 * @param {function} [options.onWait] called when a drug barcode is held
 * @param {number} [options.waitMs]
 * @returns {{offer: function, cancel: function}} offer(read) with read = {code, ndc, lot,
 *   expirationDate, matrix}; cancel() stops it resolving
 */
export const createScanResolver = ({
  onResolve, onWait = () => {}, waitMs = DATA_MATRIX_WAIT_MS,
  schedule = setTimeout, unschedule = clearTimeout,
}) => {
  let held = null;
  let timer = null;
  let done = false;

  const resolve = (read) => {
    if (done) {
      return;
    }
    done = true;
    if (timer) {
      unschedule(timer);
    }
    onResolve(read);
  };

  return {
    offer: (read) => {
      if (done) {
        return;
      }
      if (held) {
        // Only a DataMatrix for the same drug replaces the held barcode
        if (read.matrix && drugNdcDigits(read.code) === drugNdcDigits(held.code)) {
          resolve(read);
        }
        return;
      }
      if (!read.matrix && drugNdcDigits(read.code)) {
        held = read;
        timer = schedule(() => resolve(held), waitMs);
        onWait(read);
        return;
      }
      resolve(read);
    },
    cancel: () => {
      done = true;
      if (timer) {
        unschedule(timer);
      }
    },
  };
};
