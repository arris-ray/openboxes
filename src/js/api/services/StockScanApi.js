import { apiClientCustomResponseHandler } from 'utils/apiClient';

const STOCK_SCANS = '/api/stockScans';
const CATEGORIES = '/api/categories';

/**
 * Stock scan API (StockScanApiController). Uses the client without global error popups, so the
 * page can show scan errors (wrong quantity, nothing in stock, ...) next to the form instead.
 */
export default {
  /** @returns products with this barcode, each with its stock at the current location */
  lookup: (barcode) => apiClientCustomResponseHandler.get(STOCK_SCANS, { params: { barcode } }),
  /** Adds or uses stock (action ADD or USE), optionally creating the product first */
  perform: (payload) => apiClientCustomResponseHandler.post(STOCK_SCANS, payload),
  getCategories: () => apiClientCustomResponseHandler.get(CATEGORIES),
};
