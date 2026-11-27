import React, { useState } from 'react';

import PropTypes from 'prop-types';

import {
  buildNewProductPayload, factRows, initialCategoryId, isNewProductComplete, rememberCategoryId,
} from 'components/stock-scan/newProduct';
import QuantityStepper from 'components/stock-scan/QuantityStepper';
import { MAX_QUANTITY } from 'components/stock-scan/stockScanConstants';
import { CATEGORY_URL } from 'consts/applicationUrls';
import useTranslate from 'hooks/useTranslate';

/**
 * A code OpenBoxes doesn't know yet, scanned in Add mode: create the product and add stock in
 * one step. The form starts from the server's suggestion, filled in from the product facts
 * providers when they found the product.
 */
const NewProductForm = ({
  barcode, draft, facts, categories, submitting, error, onSubmit, onCancel,
}) => {
  const translate = useTranslate();
  const [name, setName] = useState(draft?.name || '');
  const [categoryId, setCategoryId] = useState(() => initialCategoryId(categories));
  const [lotAndExpiryControl, setLotAndExpiryControl] = useState(
    Boolean(draft?.lotAndExpiryControl),
  );
  const [controlledSubstance, setControlledSubstance] = useState(
    Boolean(draft?.controlledSubstance),
  );
  const [lotNumber, setLotNumber] = useState('');
  const [expirationDate, setExpirationDate] = useState('');
  const [quantity, setQuantity] = useState(1);

  const form = {
    draft,
    barcode,
    name,
    categoryId,
    lotAndExpiryControl,
    controlledSubstance,
    quantity,
    lotNumber,
    expirationDate,
  };
  const rows = factRows(facts, translate);

  const submit = (event) => {
    event.preventDefault();
    rememberCategoryId(categoryId);
    onSubmit(buildNewProductPayload(form));
  };

  const codeLabel = draft && !draft.upc && draft.ndc
    ? `${translate('react.stockScan.ndc.label', 'NDC')} ${draft.ndc}`
    : `${translate('react.stockScan.barcode.label', 'Barcode')} ${barcode}`;

  return (
    <form className="stock-scan__panel" onSubmit={submit}>
      <div className="stock-scan__product">
        <div className="stock-scan__product-name">
          {translate('react.stockScan.newProduct.label', 'New product')}
        </div>
        <div className="stock-scan__product-meta">{codeLabel}</div>
      </div>

      {facts ? (
        // No product image: loading it would have the phone contact the provider directly, and only
        // the server talks to providers
        <div className="stock-scan__facts">
          <dl className="stock-scan__facts-list">
            {rows.map((row) => (
              <React.Fragment key={row.key}>
                <dt>{row.label}</dt>
                <dd>{row.value}</dd>
              </React.Fragment>
            ))}
          </dl>
          <div className="stock-scan__facts-source">
            {translate('react.stockScan.detailsFrom.label', 'Details from')}
            {' '}
            {facts.source.url ? (
              <a href={facts.source.url} target="_blank" rel="noopener noreferrer">{facts.source.name}</a>
            ) : facts.source.name}
            {'. '}
            {translate('react.stockScan.checkDetails.label', 'Check them against the package.')}
          </div>
        </div>
      ) : (
        <p className="stock-scan__message">
          {translate('react.stockScan.noDetails.label', 'No details were found for this code. Enter them below.')}
        </p>
      )}

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

      <label htmlFor="stock-scan-lot-control" className="stock-scan__choice">
        <input
          id="stock-scan-lot-control"
          type="checkbox"
          checked={lotAndExpiryControl}
          onChange={(event) => setLotAndExpiryControl(event.target.checked)}
          disabled={submitting}
        />
        <span>{translate('react.stockScan.trackLotAndExpiry.label', 'Track lot and expiry')}</span>
      </label>
      <label htmlFor="stock-scan-controlled" className="stock-scan__choice">
        <input
          id="stock-scan-controlled"
          type="checkbox"
          checked={controlledSubstance}
          onChange={(event) => setControlledSubstance(event.target.checked)}
          disabled={submitting}
        />
        <span>{translate('react.stockScan.controlledSubstance.label', 'Controlled substance')}</span>
      </label>

      {lotAndExpiryControl && (
        <div className="stock-scan__options">
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
            required
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
            required
          />
        </div>
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
        disabled={submitting || !isNewProductComplete(form)}
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

/** The server's suggested new product (newProduct in the lookup) */
export const newProductDraftShape = PropTypes.shape({
  name: PropTypes.string,
  upc: PropTypes.string,
  ndc: PropTypes.string,
  genericName: PropTypes.string,
  brandName: PropTypes.string,
  manufacturer: PropTypes.string,
  description: PropTypes.string,
  lotAndExpiryControl: PropTypes.bool,
  controlledSubstance: PropTypes.bool,
});

/** Details from a product facts provider (facts in the lookup) */
export const productFactsShape = PropTypes.shape({
  name: PropTypes.string,
  genericName: PropTypes.string,
  brandName: PropTypes.string,
  manufacturer: PropTypes.string,
  ndc: PropTypes.string,
  packageDescription: PropTypes.string,
  kind: PropTypes.string,
  deaSchedule: PropTypes.string,
  imageUrl: PropTypes.string,
  source: PropTypes.shape({
    id: PropTypes.string.isRequired,
    name: PropTypes.string.isRequired,
    url: PropTypes.string,
  }).isRequired,
});

NewProductForm.propTypes = {
  /** The code that was looked up */
  barcode: PropTypes.string.isRequired,
  draft: newProductDraftShape,
  facts: productFactsShape,
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
  draft: null,
  facts: null,
  error: null,
};
