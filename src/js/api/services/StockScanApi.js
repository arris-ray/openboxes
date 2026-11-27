import { apiClientCustomResponseHandler } from 'utils/apiClient';

const STOCK_SCANS = '/api/stockScans';
const CATEGORIES = '/api/categories';

/**
 * Stock scan API (StockScanApiController). Uses the client without global error popups, so the
 * page can show scan errors (wrong quantity, nothing in stock, ...) next to the form instead.
 */
export default {
  /**
   * @param {string} barcode a barcode, or a typed NDC
   * @param {string} [ndc] an NDC from elsewhere, such as a DataMatrix
   * @returns products with this barcode or NDC, each with its stock at the current location; for
   *   an unknown code, details from the product facts providers and a suggested new product
   */
  lookup: (barcode, ndc) => apiClientCustomResponseHandler.get(STOCK_SCANS, {
    params: { barcode: barcode || undefined, ndc: ndc || undefined },
  }),
  /** Adds or uses stock (action ADD or USE), optionally creating the product first */
  perform: (payload) => apiClientCustomResponseHandler.post(STOCK_SCANS, payload),
  getCategories: () => apiClientCustomResponseHandler.get(CATEGORIES),
};
