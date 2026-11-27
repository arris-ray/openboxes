import React, { useState } from 'react';

import PropTypes from 'prop-types';

import QuantityStepper from 'components/stock-scan/QuantityStepper';
import { stockRowKey, stockRowLabel } from 'components/stock-scan/stockRowLabel';
import { MAX_QUANTITY, ScanMode } from 'components/stock-scan/stockScanConstants';
import useTranslate from 'hooks/useTranslate';

/**
 * A scanned product that OpenBoxes knows: shows its stock and adds to it or uses from it,
 * depending on the mode.
 */
const FoundProduct = ({
  mode, productStock, submitting, error, onSubmit, onCancel, onSwitchMode,
}) => {
  const translate = useTranslate();
  const { product, quantityOnHand, items } = productStock;
  const isUse = mode === ScanMode.USE;

  const [quantity, setQuantity] = useState(1);
  // USE: rows are sorted soonest expiry first, so the first one is the default
  const [rowKey, setRowKey] = useState(items.length ? stockRowKey(items[0]) : null);
  const [showOptions, setShowOptions] = useState(false);
  const [lotNumber, setLotNumber] = useState('');
  const [expirationDate, setExpirationDate] = useState('');
  const [comment, setComment] = useState('');

  const selectedRow = items.find((row) => stockRowKey(row) === rowKey);
  const maxQuantity = isUse
    ? Math.min(selectedRow?.quantityOnHand || 0, MAX_QUANTITY)
    : MAX_QUANTITY;

  const submit = (event) => {
    event.preventDefault();
    if (!quantity) {
      return;
    }
    if (isUse) {
      onSubmit({
        action: ScanMode.USE,
        productId: product.id,
        quantity,
        inventoryItemId: selectedRow.inventoryItemId,
        binLocationId: selectedRow.binLocation?.id || null,
        comment: comment || null,
      });
    } else {
      onSubmit({
        action: ScanMode.ADD,
        productId: product.id,
        quantity,
        lotNumber: lotNumber || null,
        expirationDate: expirationDate || null,
        comment: comment || null,
      });
    }
  };

  const header = (
    <div className="stock-scan__product">
      <div className="stock-scan__product-name">{product.name}</div>
      <div className="stock-scan__product-meta">
        {product.productCode}
        {product.upc ? ` · ${product.upc}` : ''}
      </div>
      <div className="stock-scan__on-hand">
        {translate('react.stockScan.onHand.label', 'On hand')}
        {': '}
        <strong>{quantityOnHand}</strong>
      </div>
      {!isUse && items.length > 1 && (
        <ul className="stock-scan__rows">
          {items.map((row) => (
            <li key={stockRowKey(row)}>
              {stockRowLabel(row, translate)}
              {': '}
              {row.quantityOnHand}
            </li>
          ))}
        </ul>
      )}
    </div>
  );

  if (isUse && !items.length) {
    return (
      <div className="stock-scan__panel">
        {header}
        <p className="stock-scan__message">
          {translate('react.stockScan.noneInStock.label', 'There\'s none of this in stock here.')}
        </p>
        <button type="button" className="stock-scan__button stock-scan__button--add" onClick={onSwitchMode}>
          {translate('react.stockScan.switchToAdd.label', 'Switch to Add')}
        </button>
        <button type="button" className="stock-scan__button stock-scan__button--link" onClick={onCancel}>
          {translate('react.stockScan.scanAnother.label', 'Scan another')}
        </button>
      </div>
    );
  }

  return (
    <form className="stock-scan__panel" onSubmit={submit}>
      {header}

      {isUse && items.length > 1 && (
        <fieldset className="stock-scan__fieldset">
          <legend className="stock-scan__label">
            {translate('react.stockScan.useFrom.label', 'Use from')}
          </legend>
          {items.map((row) => {
            const key = stockRowKey(row);
            return (
              <label key={key} htmlFor={`stock-scan-row-${key}`} className="stock-scan__choice">
                <input
                  id={`stock-scan-row-${key}`}
                  type="radio"
                  name="stock-scan-row"
                  checked={key === rowKey}
                  onChange={() => {
                    setRowKey(key);
                    setQuantity((current) => Math.min(current || 1, row.quantityOnHand));
                  }}
                  disabled={submitting}
                />
                <span>
                  {stockRowLabel(row, translate)}
                  {' — '}
                  <strong>{row.quantityOnHand}</strong>
                </span>
              </label>
            );
          })}
        </fieldset>
      )}

      <label htmlFor="stock-scan-quantity" className="stock-scan__label">
        {isUse
          ? translate('react.stockScan.quantityToUse.label', 'Quantity to use')
          : translate('react.stockScan.quantityToAdd.label', 'Quantity to add')}
      </label>
      <QuantityStepper
        id="stock-scan-quantity"
        value={quantity}
        max={maxQuantity}
        onChange={setQuantity}
        disabled={submitting}
      />

      <button
        type="button"
        className="stock-scan__button stock-scan__button--link"
        onClick={() => setShowOptions(!showOptions)}
        aria-expanded={showOptions}
      >
        {showOptions
          ? translate('react.stockScan.fewerOptions.label', 'Fewer options')
          : translate('react.stockScan.moreOptions.label', 'More options')}
      </button>
      {showOptions && (
        <div className="stock-scan__options">
          {!isUse && (
            <>
              <label htmlFor="stock-scan-lot" className="stock-scan__label">
                {translate('react.stockScan.lotNumber.label', 'Lot number')}
              </label>
              <input
                id="stock-scan-lot"
                className="stock-scan__input"
                type="text"
                autoComplete="off"
                value={lotNumber}
                onChange={(event) => setLotNumber(event.target.value)}
                disabled={submitting}
              />
              <label htmlFor="stock-scan-expiry" className="stock-scan__label">
                {translate('react.stockScan.expirationDate.label', 'Expiration date')}
              </label>
              <input
                id="stock-scan-expiry"
                className="stock-scan__input"
                type="date"
                value={expirationDate}
                onChange={(event) => setExpirationDate(event.target.value)}
                disabled={submitting}
              />
            </>
          )}
          <label htmlFor="stock-scan-comment" className="stock-scan__label">
            {translate('react.stockScan.comment.label', 'Comment')}
          </label>
          <input
            id="stock-scan-comment"
            className="stock-scan__input"
            type="text"
            value={comment}
            onChange={(event) => setComment(event.target.value)}
            disabled={submitting}
          />
        </div>
      )}

      {error && <div className="stock-scan__error" role="alert">{error}</div>}

      <button
        type="submit"
        className={`stock-scan__button stock-scan__button--${isUse ? 'use' : 'add'}`}
        disabled={submitting || !quantity}
      >
        {isUse
          ? `${translate('react.stockScan.use.label', 'Use')} ${quantity || ''}`
          : `${translate('react.stockScan.add.label', 'Add')} ${quantity || ''}`}
      </button>
      <button type="button" className="stock-scan__button stock-scan__button--link" onClick={onCancel} disabled={submitting}>
        {translate('react.stockScan.cancel.label', 'Cancel')}
      </button>
    </form>
  );
};

export default FoundProduct;

const stockRowShape = PropTypes.shape({
  inventoryItemId: PropTypes.string,
  lotNumber: PropTypes.string,
  expirationDate: PropTypes.string,
  binLocation: PropTypes.shape({ id: PropTypes.string, name: PropTypes.string }),
  quantityOnHand: PropTypes.number,
});

export const productStockShape = PropTypes.shape({
  product: PropTypes.shape({
    id: PropTypes.string.isRequired,
    name: PropTypes.string.isRequired,
    productCode: PropTypes.string,
    upc: PropTypes.string,
  }).isRequired,
  quantityOnHand: PropTypes.number.isRequired,
  items: PropTypes.arrayOf(stockRowShape).isRequired,
});

FoundProduct.propTypes = {
  mode: PropTypes.oneOf(Object.values(ScanMode)).isRequired,
  productStock: productStockShape.isRequired,
  submitting: PropTypes.bool.isRequired,
  error: PropTypes.string,
  onSubmit: PropTypes.func.isRequired,
  onCancel: PropTypes.func.isRequired,
  onSwitchMode: PropTypes.func.isRequired,
};

FoundProduct.defaultProps = {
  error: null,
};
