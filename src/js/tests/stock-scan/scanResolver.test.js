import { createScanResolver, DATA_MATRIX_WAIT_MS, drugNdcDigits } from 'components/stock-scan/barcode';
import { stockRowForLot } from 'components/stock-scan/scannedLot';

describe('drugNdcDigits', () => {
  it.each([
    ['343547422103', '4354742210'],
    ['0343547422103', '4354742210'],
    ['00343547422103', '4354742210'],
    ['50343547422100', '4354742210'],
    ['012345678905', null],
    ['96385074', null],
    [null, null],
  ])('%p gives %p', (code, expected) => {
    expect(drugNdcDigits(code)).toBe(expected);
  });
});

describe('createScanResolver', () => {
  beforeEach(() => jest.useFakeTimers());
  afterEach(() => jest.useRealTimers());

  const drugUpc = { code: '0343547422103', matrix: false };
  const matrix = {
    code: '00343547422103', lot: 'L1', expirationDate: '2028-06-30', matrix: true,
  };

  it('uses a non-drug barcode at once', () => {
    const onResolve = jest.fn();
    createScanResolver({ onResolve }).offer({ code: '012345678905', matrix: false });
    expect(onResolve).toHaveBeenCalledWith({ code: '012345678905', matrix: false });
  });

  it('uses a DataMatrix at once', () => {
    const onResolve = jest.fn();
    createScanResolver({ onResolve }).offer(matrix);
    expect(onResolve).toHaveBeenCalledWith(matrix);
  });

  it('holds a drug barcode, and uses it if no DataMatrix is read in time', () => {
    const onResolve = jest.fn();
    const onWait = jest.fn();
    const resolver = createScanResolver({ onResolve, onWait });
    resolver.offer(drugUpc);
    resolver.offer(drugUpc);
    expect(onWait).toHaveBeenCalledTimes(1);
    expect(onResolve).not.toHaveBeenCalled();
    jest.advanceTimersByTime(DATA_MATRIX_WAIT_MS);
    expect(onResolve).toHaveBeenCalledTimes(1);
    expect(onResolve).toHaveBeenCalledWith(drugUpc);
  });

  it('uses the DataMatrix of the same drug read during the wait', () => {
    const onResolve = jest.fn();
    const resolver = createScanResolver({ onResolve });
    resolver.offer(drugUpc);
    jest.advanceTimersByTime(400);
    resolver.offer(matrix);
    jest.advanceTimersByTime(DATA_MATRIX_WAIT_MS);
    expect(onResolve).toHaveBeenCalledTimes(1);
    expect(onResolve).toHaveBeenCalledWith(matrix);
  });

  it('ignores other codes during the wait', () => {
    const onResolve = jest.fn();
    const resolver = createScanResolver({ onResolve });
    resolver.offer(drugUpc);
    resolver.offer({ code: '012345678905', matrix: false });
    resolver.offer({ ...matrix, code: '00300020000014' });
    jest.advanceTimersByTime(DATA_MATRIX_WAIT_MS);
    expect(onResolve).toHaveBeenCalledTimes(1);
    expect(onResolve).toHaveBeenCalledWith(drugUpc);
  });

  it('resolves nothing once cancelled', () => {
    const onResolve = jest.fn();
    const resolver = createScanResolver({ onResolve });
    resolver.offer(drugUpc);
    resolver.cancel();
    jest.advanceTimersByTime(DATA_MATRIX_WAIT_MS);
    resolver.offer(matrix);
    expect(onResolve).not.toHaveBeenCalled();
  });
});

describe('stockRowForLot', () => {
  const rows = [
    { lotNumber: 'A1', expirationDate: '2027-01-31', quantityOnHand: 4 },
    { lotNumber: 'B2', expirationDate: '2028-06-30', quantityOnHand: 0 },
    { lotNumber: 'b2', expirationDate: '2028-06-30', quantityOnHand: 3 },
    { lotNumber: 'C3', expirationDate: '2029-01-31', quantityOnHand: 1 },
  ];

  it('finds the in-stock row for the scanned lot, ignoring case', () => {
    expect(stockRowForLot(rows, { lotNumber: 'B2', expirationDate: '2028-06-30' })).toBe(rows[2]);
  });

  it('finds nothing for an unknown lot, or without a lot', () => {
    expect(stockRowForLot(rows, { lotNumber: 'Z9', expirationDate: '' })).toBeUndefined();
    expect(stockRowForLot(rows, null)).toBeUndefined();
    expect(stockRowForLot(rows, { lotNumber: '', expirationDate: '2027-01-31' })).toBeUndefined();
  });
});
