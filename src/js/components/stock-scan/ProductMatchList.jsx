import React from 'react';

import PropTypes from 'prop-types';

import { productStockShape } from 'components/stock-scan/FoundProduct';
import useTranslate from 'hooks/useTranslate';

/** Several products share the scanned barcode: pick one. */
const ProductMatchList = ({ matches, onSelect, onCancel }) => {
  const translate = useTranslate();

  return (
    <div className="stock-scan__panel">
      <p className="stock-scan__message">
        {translate('react.stockScan.severalMatches.label', 'More than one product has this barcode. Which one is it?')}
      </p>
      <ul className="stock-scan__matches">
        {matches.map((match) => (
          <li key={match.product.id}>
            <button type="button" className="stock-scan__match" onClick={() => onSelect(match)}>
              <span className="stock-scan__product-name">{match.product.name}</span>
              <span className="stock-scan__product-meta">
                {match.product.productCode}
                {' · '}
                {translate('react.stockScan.onHand.label', 'On hand')}
                {` ${match.quantityOnHand}`}
              </span>
            </button>
          </li>
        ))}
      </ul>
      <button type="button" className="stock-scan__button stock-scan__button--link" onClick={onCancel}>
        {translate('react.stockScan.cancel.label', 'Cancel')}
      </button>
    </div>
  );
};

export default ProductMatchList;

ProductMatchList.propTypes = {
  matches: PropTypes.arrayOf(productStockShape).isRequired,
  onSelect: PropTypes.func.isRequired,
  onCancel: PropTypes.func.isRequired,
};
