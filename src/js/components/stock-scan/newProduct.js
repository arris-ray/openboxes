import { CATEGORY_STORAGE_KEY, ScanMode } from 'components/stock-scan/stockScanConstants';

/**
 * Helpers for creating a product from a scan. The server suggests the new product (a draft
 * filled in from the product facts providers); the person checks it, chooses a category and
 * adds the stock.
 */

/**
 * @param {Array<{id: string}>} categories the categories offered
 * @param {Storage} [storage] where the last category is kept
 * @returns {string} the category chosen last time, if it's still offered; the only category if
 *   there's one; otherwise ''
 */
export const initialCategoryId = (categories, storage = window.localStorage) => {
  let stored = null;
  try {
    stored = storage.getItem(CATEGORY_STORAGE_KEY);
  } catch (e) {
    // Private browsing can refuse storage
  }
  if (stored && categories.some((category) => category.id === stored)) {
    return stored;
  }
  return categories.length === 1 ? categories[0].id : '';
};

export const rememberCategoryId = (categoryId, storage = window.localStorage) => {
  try {
    storage.setItem(CATEGORY_STORAGE_KEY, categoryId);
  } catch (e) {
    // The category just won't be remembered
  }
};

/**
 * @param {object} form
 * @param {object} form.draft the server's suggested new product (newProduct in the lookup)
 * @param {string} form.barcode the code that was looked up, used if there's no draft
 * @returns {object} the request that creates the product and adds the stock
 */
export const buildNewProductPayload = ({
  draft, barcode, name, categoryId, lotAndExpiryControl, controlledSubstance, quantity,
  lotNumber, expirationDate,
}) => {
  const base = draft || { upc: barcode };
  return {
    action: ScanMode.ADD,
    newProduct: {
      ...base,
      name: name.trim(),
      categoryId,
      lotAndExpiryControl,
      controlledSubstance,
    },
    quantity,
    lotNumber: lotNumber.trim() || null,
    expirationDate: expirationDate || null,
  };
};

/**
 * @returns {boolean} true when everything needed is filled in: lot and expiry are needed when
 *   the product tracks them
 */
export const isNewProductComplete = ({
  name, categoryId, quantity, lotAndExpiryControl, lotNumber, expirationDate,
}) => Boolean(name.trim() && categoryId && quantity
  && (!lotAndExpiryControl || (lotNumber.trim() && expirationDate)));

/**
 * @param {object} facts details from a provider (facts in the lookup)
 * @param {function} translate
 * @returns {Array<{key: string, label: string, value: string}>} the details worth showing
 */
export const factRows = (facts, translate) => {
  if (!facts) {
    return [];
  }
  const kinds = {
    PRESCRIPTION_DRUG: translate('react.stockScan.kindPrescription.label', 'Prescription drug'),
    OTC_DRUG: translate('react.stockScan.kindOtc.label', 'Over-the-counter drug'),
    FOOD: translate('react.stockScan.kindFood.label', 'Food'),
  };
  return [
    { key: 'kind', label: translate('react.stockScan.kind.label', 'Type'), value: kinds[facts.kind] },
    { key: 'genericName', label: translate('react.stockScan.genericName.label', 'Generic name'), value: facts.genericName },
    { key: 'brandName', label: translate('react.stockScan.brandName.label', 'Brand'), value: facts.brandName },
    { key: 'manufacturer', label: translate('react.stockScan.manufacturer.label', 'Manufacturer'), value: facts.manufacturer },
    { key: 'ndc', label: translate('react.stockScan.ndc.label', 'NDC'), value: facts.ndc },
    { key: 'package', label: translate('react.stockScan.package.label', 'Package'), value: facts.packageDescription },
    { key: 'deaSchedule', label: translate('react.stockScan.deaSchedule.label', 'DEA schedule'), value: facts.deaSchedule },
  ].filter((row) => row.value);
};
