import flattenCategories from 'components/stock-scan/flattenCategories';
import { stockRowKey, stockRowLabel } from 'components/stock-scan/stockRowLabel';

const translate = (id, defaultMessage) => defaultMessage;

describe('flattenCategories', () => {
  it('returns leaf categories labelled with their path', () => {
    const tree = [
      { id: 'general', name: 'General', categories: [] },
      {
        id: 'food',
        name: 'Food',
        categories: [
          { id: 'coffee', name: 'Coffee', categories: [] },
          { id: 'tea', name: 'Tea' },
        ],
      },
    ];

    expect(flattenCategories(tree)).toEqual([
      { id: 'general', label: 'General' },
      { id: 'coffee', label: 'Food › Coffee' },
      { id: 'tea', label: 'Food › Tea' },
    ]);
  });

  it('leaves the root category out of the labels', () => {
    const tree = [{
      id: 'root',
      name: 'ROOT',
      isRoot: true,
      categories: [{ id: 'general', name: 'General', categories: [] }],
    }];

    expect(flattenCategories(tree)).toEqual([{ id: 'general', label: 'General' }]);
  });

  it('handles no categories', () => {
    expect(flattenCategories([])).toEqual([]);
  });
});

describe('stockRowLabel', () => {
  it('describes a row with a lot, bin and expiry', () => {
    const row = {
      inventoryItemId: 'i1', lotNumber: 'A12', expirationDate: '2027-03-31', binLocation: { id: 'b1', name: 'Shelf 3' },
    };
    expect(stockRowLabel(row, translate)).toBe('Lot A12 · Shelf 3 · expires 2027-03-31');
  });

  it('describes a row without a lot, bin or expiry', () => {
    expect(stockRowLabel({ inventoryItemId: 'i1' }, translate)).toBe('No lot · Default bin');
  });
});

describe('stockRowKey', () => {
  it('tells apart the same lot in different bins', () => {
    expect(stockRowKey({ inventoryItemId: 'i1', binLocation: { id: 'b1' } })).toBe('i1|b1');
    expect(stockRowKey({ inventoryItemId: 'i1', binLocation: null })).toBe('i1|');
  });
});
