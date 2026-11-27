import React, { useState } from 'react';

import PropTypes from 'prop-types';

import { normalizeTypedCode } from 'components/stock-scan/ndc';
import useTranslate from 'hooks/useTranslate';

/** Typing a barcode, or a drug's NDC, instead of scanning it. */
const BarcodeEntry = ({ onSubmit, disabled }) => {
  const translate = useTranslate();
  const [barcode, setBarcode] = useState('');

  const submit = (event) => {
    event.preventDefault();
    const code = normalizeTypedCode(barcode);
    if (code) {
      onSubmit(code);
      setBarcode('');
    }
  };

  return (
    <form className="stock-scan__barcode-entry" onSubmit={submit}>
      <label htmlFor="stock-scan-barcode" className="stock-scan__label">
        {translate('react.stockScan.typeBarcodeOrNdc.label', 'Type a barcode or NDC')}
      </label>
      <div className="stock-scan__inline">
        <input
          id="stock-scan-barcode"
          className="stock-scan__input"
          type="text"
          inputMode="numeric"
          autoComplete="off"
          placeholder="012345678905"
          value={barcode}
          onChange={(event) => setBarcode(event.target.value)}
          disabled={disabled}
        />
        <button type="submit" className="stock-scan__button stock-scan__button--secondary" disabled={disabled || !barcode.trim()}>
          {translate('react.stockScan.find.label', 'Find')}
        </button>
      </div>
    </form>
  );
};

export default BarcodeEntry;

BarcodeEntry.propTypes = {
  onSubmit: PropTypes.func.isRequired,
  disabled: PropTypes.bool,
};

BarcodeEntry.defaultProps = {
  disabled: false,
};
