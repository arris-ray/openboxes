import React, { useState } from 'react';

import PropTypes from 'prop-types';

import QuantityStepper from 'components/stock-scan/QuantityStepper';
import { MAX_QUANTITY, ScanMode } from 'components/stock-scan/stockScanConstants';
import { CATEGORY_URL } from 'consts/applicationUrls';
import useTranslate from 'hooks/useTranslate';

/**
 * A barcode OpenBoxes doesn't know yet, scanned in Add mode: create the product and add stock in
 * one step.
 */
const NewProductForm = ({
  barcode, categories, submitting, error, onSubmit, onCancel,
}) => {
  const translate = useTranslate();
  const [name, setName] = useState('');
  const [categoryId, setCategoryId] = useState(categories.length === 1 ? categories[0].id : '');
  const [quantity, setQuantity] = useState(1);

  const submit = (event) => {
    event.preventDefault();
    onSubmit({
      action: ScanMode.ADD,
      newProduct: { name: name.trim(), categoryId, upc: barcode },
      quantity,
    });
  };

  return (
    <form className="stock-scan__panel" onSubmit={submit}>
      <div className="stock-scan__product">
        <div className="stock-scan__product-name">
          {translate('react.stockScan.newProduct.label', 'New product')}
        </div>
        <div className="stock-scan__product-meta">
          {translate('react.stockScan.barcode.label', 'Barcode')}
          {` ${barcode}`}
        </div>
      </div>

      <label htmlFor="stock-scan-name" className="stock-scan__label">
        {translate('react.stockScan.name.label', 'Name')}
      </label>
      <input
        id="stock-scan-name"
        className="stock-scan__input"
        type="text"
        maxLength={255}
        value={name}
        onChange={(event) => setName(event.target.value)}
        disabled={submitting}
        required
      />

      <label htmlFor="stock-scan-category" className="stock-scan__label">
        {translate('react.stockScan.category.label', 'Category')}
      </label>
      {categories.length ? (
        <select
          id="stock-scan-category"
          className="stock-scan__input"
          value={categoryId}
          onChange={(event) => setCategoryId(event.target.value)}
          disabled={submitting}
          required
        >
          <option value="" disabled>
            {translate('react.stockScan.chooseCategory.label', 'Choose a category')}
          </option>
          {categories.map((category) => (
            <option key={category.id} value={category.id}>{category.label}</option>
          ))}
        </select>
      ) : (
        <p className="stock-scan__message">
          {translate('react.stockScan.noCategories.label', 'There are no categories yet.')}
          {' '}
          <a href={CATEGORY_URL.tree()}>
            {translate('react.stockScan.createCategory.label', 'Create one')}
          </a>
        </p>
      )}

      <label htmlFor="stock-scan-quantity" className="stock-scan__label">
        {translate('react.stockScan.quantityToAdd.label', 'Quantity to add')}
      </label>
      <QuantityStepper
        id="stock-scan-quantity"
        value={quantity}
        max={MAX_QUANTITY}
        onChange={setQuantity}
        disabled={submitting}
      />

      {error && <div className="stock-scan__error" role="alert">{error}</div>}

      <button
        type="submit"
        className="stock-scan__button stock-scan__button--add"
        disabled={submitting || !name.trim() || !categoryId || !quantity}
      >
        {translate('react.stockScan.createAndAdd.label', 'Create and add')}
        {quantity ? ` ${quantity}` : ''}
      </button>
      <button type="button" className="stock-scan__button stock-scan__button--link" onClick={onCancel} disabled={submitting}>
        {translate('react.stockScan.cancel.label', 'Cancel')}
      </button>
    </form>
  );
};

export default NewProductForm;

NewProductForm.propTypes = {
  barcode: PropTypes.string.isRequired,
  /** Categories a product can be assigned to, with labels showing their path */
  categories: PropTypes.arrayOf(PropTypes.shape({
    id: PropTypes.string.isRequired,
    label: PropTypes.string.isRequired,
  })).isRequired,
  submitting: PropTypes.bool.isRequired,
  error: PropTypes.string,
  onSubmit: PropTypes.func.isRequired,
  onCancel: PropTypes.func.isRequired,
};

NewProductForm.defaultProps = {
  error: null,
};
