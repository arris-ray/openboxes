/**
 * Reads GS1 element strings, as found in the 2D DataMatrix on prescription drug packages (the US
 * DSCSA requires the GTIN, lot, expiry and serial number). Each element is an application
 * identifier (AI) and its value. Fixed-length values end by length; variable-length values end at
 * a group separator (ASCII 29, which the reader outputs for FNC1) or at the end of the string.
 *
 * The serial number (AI 21) is read past and never returned: it identifies a single package,
 * which the stock scan page has no use for, so it never leaves the phone.
 */

const GROUP_SEPARATOR = '\u001d';

/** Value length of fixed-length AIs. */
const FIXED = {
  '00': 18,
  '01': 14,
  '02': 14,
  11: 6,
  12: 6,
  13: 6,
  15: 6,
  16: 6,
  17: 6,
  20: 2,
  7003: 10,
};

/** Maximum value length of variable-length AIs. */
const VARIABLE = {
  10: 20,
  21: 20,
  22: 20,
  30: 8,
  37: 8,
  240: 30,
  241: 30,
  710: 20,
  711: 20,
  712: 20,
  713: 20,
  714: 20,
  715: 20,
};

const stripLeadingSeparators = (text) => {
  let index = 0;
  while (text[index] === GROUP_SEPARATOR) {
    index += 1;
  }
  return text.slice(index);
};

/** @returns {{ai: string, length: number, fixed: boolean}|null} the AI at the start of the text */
const identify = (text) => {
  // Measures (310n-369n) have a 4-digit AI and a 6-digit value
  if (/^3[1-6]\d\d/.test(text)) {
    return { ai: text.slice(0, 4), length: 6, fixed: true };
  }
  const candidates = [text.slice(0, 4), text.slice(0, 3), text.slice(0, 2)];
  const fixedAi = candidates.find((ai) => FIXED[ai]);
  if (fixedAi) {
    return { ai: fixedAi, length: FIXED[fixedAi], fixed: true };
  }
  const variableAi = candidates.find((ai) => VARIABLE[ai]);
  if (variableAi) {
    return { ai: variableAi, length: VARIABLE[variableAi], fixed: false };
  }
  return null;
};

/**
 * GS1 dates are YYMMDD. The century is the one that puts the year within 49 years before or 50
 * years after this year, and day 00 means the last day of the month.
 *
 * @returns {string|null} the date as YYYY-MM-DD, or null if it isn't a valid date
 */
export const gs1Date = (yymmdd, today = new Date()) => {
  if (!/^\d{6}$/.test(yymmdd || '')) {
    return null;
  }
  const yy = Number(yymmdd.slice(0, 2));
  const month = Number(yymmdd.slice(2, 4));
  let day = Number(yymmdd.slice(4, 6));
  const thisYear = today.getFullYear();
  const century = Math.floor(thisYear / 100) * 100;
  const difference = yy - (thisYear % 100);
  let year = century + yy;
  if (difference >= 51) {
    year -= 100;
  } else if (difference <= -50) {
    year += 100;
  }
  if (month < 1 || month > 12) {
    return null;
  }
  const lastDay = new Date(Date.UTC(year, month, 0)).getUTCDate();
  if (day === 0) {
    day = lastDay;
  }
  if (day > lastDay) {
    return null;
  }
  const pad = (number) => String(number).padStart(2, '0');
  return `${year}-${pad(month)}-${pad(day)}`;
};

/**
 * @param {string} text the decoded DataMatrix (or GS1-128) text
 * @returns {object|null} {gtin, ndc, lot, expirationDate}, each a string or null; or null if it
 *   isn't a GS1 element string with a GTIN or NDC
 */
export const parseGs1 = (text, today = new Date()) => {
  // Drop a symbology identifier (]d2 for GS1 DataMatrix) and a leading FNC1
  let rest = stripLeadingSeparators((text || '').replace(/^\][A-Za-z]\d/, ''));
  // Some readers show FNC1 as text
  rest = rest.split('<GS>').join(GROUP_SEPARATOR);
  const elements = {};

  while (rest.length) {
    const element = identify(rest);
    if (!element) {
      break; // An AI this parser doesn't know: its length is unknown, so stop
    }
    const start = element.ai.length;
    let value;
    if (element.fixed) {
      value = rest.slice(start, start + element.length);
      rest = rest.slice(start + element.length);
      if (value.length < element.length) {
        break;
      }
    } else {
      const end = rest.indexOf(GROUP_SEPARATOR, start);
      value = rest.slice(start, end === -1 ? undefined : end);
      rest = end === -1 ? '' : rest.slice(end);
      if (value.length > element.length) {
        break;
      }
    }
    rest = stripLeadingSeparators(rest);
    if (element.ai !== '21') {
      elements[element.ai] = value;
    }
  }

  const gtin = /^\d{14}$/.test(elements['01'] || '') ? elements['01'] : null;
  const ndc = /^\d{11,12}$/.test(elements['715'] || '') ? elements['715'] : null;
  if (!gtin && !ndc) {
    return null;
  }
  return {
    gtin,
    ndc,
    lot: elements['10'] || null,
    expirationDate: gs1Date(elements['17'], today),
  };
};
