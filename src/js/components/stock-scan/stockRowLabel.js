/** A stable key for a stock row (a lot in a bin). */
export const stockRowKey = (row) => `${row.inventoryItemId}|${row.binLocation?.id || ''}`;

/**
 * Describes a stock row, e.g. "Lot A12 · Shelf 3 · expires 2027-03-31".
 * @param {function} translate the app's translate function
 */
export const stockRowLabel = (row, translate) => [
  row.lotNumber
    ? `${translate('react.stockScan.lot.label', 'Lot')} ${row.lotNumber}`
    : translate('react.stockScan.noLot.label', 'No lot'),
  row.binLocation?.name || translate('react.stockScan.defaultBin.label', 'Default bin'),
  row.expirationDate
    ? `${translate('react.stockScan.expires.label', 'expires')} ${row.expirationDate}`
    : null,
].filter(Boolean).join(' · ');
