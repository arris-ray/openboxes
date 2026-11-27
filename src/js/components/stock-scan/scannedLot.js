import PropTypes from 'prop-types';

/** A lot and expiry read from a package's DataMatrix: {lotNumber, expirationDate (YYYY-MM-DD)} */
export const scannedLotShape = PropTypes.shape({
  lotNumber: PropTypes.string,
  expirationDate: PropTypes.string,
});

/**
 * @param {Array<object>} rows the product's stock rows
 * @param {object} scannedLot the lot read from the package, or null
 * @returns {object|undefined} the in-stock row for that lot (and expiry, if both are known)
 */
export const stockRowForLot = (rows, scannedLot) => {
  const lot = scannedLot?.lotNumber?.trim();
  if (!lot) {
    return undefined;
  }
  const sameLot = rows.filter((row) => row.quantityOnHand > 0
    && (row.lotNumber || '').trim().toUpperCase() === lot.toUpperCase());
  const expiry = scannedLot.expirationDate;
  return sameLot.find((row) => !expiry || !row.expirationDate || row.expirationDate === expiry)
    || sameLot[0];
};
