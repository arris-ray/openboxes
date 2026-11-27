import {
  buildNewProductPayload, factRows, initialCategoryId, isNewProductComplete, rememberCategoryId,
} from 'components/stock-scan/newProduct';
import { CATEGORY_STORAGE_KEY } from 'components/stock-scan/stockScanConstants';

const translate = (id, defaultMessage) => defaultMessage;

const fakeStorage = (initial = {}) => {
  const values = { ...initial };
  return {
    getItem: (key) => (key in values ? values[key] : null),
    setItem: (key, value) => { values[key] = value; },
    values,
  };
};

const categories = [{ id: 'general', label: 'General' }, { id: 'meds', label: 'Medications' }];

describe('initialCategoryId', () => {
  it('uses the category chosen last time', () => {
    expect(initialCategoryId(categories, fakeStorage({ [CATEGORY_STORAGE_KEY]: 'meds' }))).toBe('meds');
  });

  it('ignores a remembered category that is no longer offered', () => {
    expect(initialCategoryId(categories, fakeStorage({ [CATEGORY_STORAGE_KEY]: 'gone' }))).toBe('');
  });

  it('uses the only category when nothing is remembered', () => {
    expect(initialCategoryId([categories[0]], fakeStorage())).toBe('general');
  });

  it('copes with storage that refuses access', () => {
    const storage = { getItem: () => { throw new Error('denied'); } };
    expect(initialCategoryId(categories, storage)).toBe('');
  });
});

describe('rememberCategoryId', () => {
  it('keeps the category for next time', () => {
    const storage = fakeStorage();
    rememberCategoryId('meds', storage);
    expect(storage.values[CATEGORY_STORAGE_KEY]).toBe('meds');
  });
});

const draft = {
  name: 'Lisinopril 20 mg Tablet',
  upc: '0343547422103',
  ndc: '43547-422-10',
  genericName: 'lisinopril',
  brandName: null,
  manufacturer: 'Solco Healthcare LLC',
  description: '100 Tablet in 1 Bottle',
  lotAndExpiryControl: true,
  controlledSubstance: false,
};

const form = {
  draft,
  barcode: '0343547422103',
  name: ' Lisinopril 20 mg Tablet (100) ',
  categoryId: 'meds',
  lotAndExpiryControl: true,
  controlledSubstance: false,
  quantity: 2,
  lotNumber: ' LOT-1 ',
  expirationDate: '2028-06-30',
};

describe('buildNewProductPayload', () => {
  it('sends the suggested details with the choices made on the form', () => {
    expect(buildNewProductPayload(form)).toEqual({
      action: 'ADD',
      newProduct: {
        ...draft,
        name: 'Lisinopril 20 mg Tablet (100)',
        categoryId: 'meds',
        lotAndExpiryControl: true,
        controlledSubstance: false,
      },
      quantity: 2,
      lotNumber: 'LOT-1',
      expirationDate: '2028-06-30',
    });
  });

  it('falls back to the barcode without a suggestion, and sends no empty lot or expiry', () => {
    const payload = buildNewProductPayload({
      ...form, draft: null, lotAndExpiryControl: false, lotNumber: ' ', expirationDate: '',
    });
    expect(payload.newProduct).toEqual({
      upc: '0343547422103',
      name: 'Lisinopril 20 mg Tablet (100)',
      categoryId: 'meds',
      lotAndExpiryControl: false,
      controlledSubstance: false,
    });
    expect(payload.lotNumber).toBeNull();
    expect(payload.expirationDate).toBeNull();
  });
});

describe('isNewProductComplete', () => {
  it('needs a name, category and quantity', () => {
    expect(isNewProductComplete(form)).toBe(true);
    expect(isNewProductComplete({ ...form, name: '  ' })).toBe(false);
    expect(isNewProductComplete({ ...form, categoryId: '' })).toBe(false);
    expect(isNewProductComplete({ ...form, quantity: null })).toBe(false);
  });

  it('needs a lot and expiry only when they are tracked', () => {
    expect(isNewProductComplete({ ...form, lotNumber: '' })).toBe(false);
    expect(isNewProductComplete({ ...form, expirationDate: '' })).toBe(false);
    expect(isNewProductComplete({
      ...form, lotAndExpiryControl: false, lotNumber: '', expirationDate: '',
    })).toBe(true);
  });
});

describe('factRows', () => {
  it('lists the details there are, in order', () => {
    const rows = factRows({
      kind: 'PRESCRIPTION_DRUG',
      genericName: 'lisinopril',
      brandName: null,
      manufacturer: 'Solco Healthcare LLC',
      ndc: '43547-422-10',
      packageDescription: '100 Tablet in 1 Bottle',
      deaSchedule: null,
      source: { id: 'openFda', name: 'openFDA' },
    }, translate);
    expect(rows.map((row) => [row.label, row.value])).toEqual([
      ['Type', 'Prescription drug'],
      ['Generic name', 'lisinopril'],
      ['Manufacturer', 'Solco Healthcare LLC'],
      ['NDC', '43547-422-10'],
      ['Package', '100 Tablet in 1 Bottle'],
    ]);
  });

  it('has no rows without details, or for an unlisted kind', () => {
    expect(factRows(null, translate)).toEqual([]);
    expect(factRows({ kind: 'OTHER', source: { id: 'x', name: 'X' } }, translate)).toEqual([]);
  });
});
