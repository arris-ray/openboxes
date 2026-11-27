/**
 * US National Drug Code helpers. The server does the real parsing (Ndc.groovy); the page only
 * needs to tell a typed NDC from a barcode, so it isn't stripped of its hyphens.
 */

const NDC_PREFIX = /^ndc[\s:#]*/i;

/**
 * @param {string} text typed text
 * @returns {boolean} true if it's written like an NDC: hyphenated as on the label (e.g.
 *   43547-422-10), or 10 or 11 digits, which no barcode has
 */
export const looksLikeNdc = (text) => {
  const value = (text || '').trim().replace(NDC_PREFIX, '');
  return /^\d{4,6}-\d{3,4}-\d{1,2}$/.test(value) || /^\d{10,11}$/.test(value);
};

/**
 * @param {string} text a typed barcode or NDC
 * @returns {string} an NDC as typed (without an "NDC" prefix), or a barcode's digits
 */
export const normalizeTypedCode = (text) => {
  const value = (text || '').trim();
  if (looksLikeNdc(value)) {
    return value.replace(NDC_PREFIX, '');
  }
  return value.replace(/[\s-]/g, '');
};
