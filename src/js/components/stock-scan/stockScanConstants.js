/** What a scan does to stock. Matches StockScanAction on the server. */
export const ScanMode = {
  ADD: 'ADD',
  USE: 'USE',
};

/** The page's screens. */
export const Screen = {
  SCAN: 'SCAN',
  LOADING: 'LOADING',
  MATCHES: 'MATCHES',
  FOUND: 'FOUND',
  UNKNOWN: 'UNKNOWN',
};

export const MAX_QUANTITY = 10000;

/** localStorage key that keeps the Add/Use mode between visits. */
export const MODE_STORAGE_KEY = 'openboxes.stockScan.mode';

/** localStorage key that keeps the category chosen for the last new product. */
export const CATEGORY_STORAGE_KEY = 'openboxes.stockScan.categoryId';
