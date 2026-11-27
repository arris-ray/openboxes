import React from 'react';

import PropTypes from 'prop-types';

import useTranslate from 'hooks/useTranslate';

/** A large − / number / + control for thumbs. */
const QuantityStepper = ({
  id, value, max, onChange, disabled,
}) => {
  const translate = useTranslate();
  const clamp = (quantity) => Math.min(Math.max(quantity, 1), max);

  const onInput = (event) => {
    const parsed = parseInt(event.target.value, 10);
    onChange(Number.isNaN(parsed) ? '' : clamp(parsed));
  };

  return (
    <div className="stock-scan__stepper">
      <button
        type="button"
        className="stock-scan__stepper-button"
        onClick={() => onChange(clamp((value || 1) - 1))}
        disabled={disabled || !value || value <= 1}
        aria-label={translate('react.stockScan.decrease.label', 'Decrease quantity')}
      >
        −
      </button>
      <input
        id={id}
        className="stock-scan__stepper-input"
        type="number"
        inputMode="numeric"
        pattern="[0-9]*"
        min={1}
        max={max}
        value={value}
        onChange={onInput}
        onFocus={(event) => event.target.select()}
        disabled={disabled}
      />
      <button
        type="button"
        className="stock-scan__stepper-button"
        onClick={() => onChange(clamp((value || 0) + 1))}
        disabled={disabled || value >= max}
        aria-label={translate('react.stockScan.increase.label', 'Increase quantity')}
      >
        +
      </button>
    </div>
  );
};

export default QuantityStepper;

QuantityStepper.propTypes = {
  id: PropTypes.string.isRequired,
  /** The quantity, or '' while the field is being edited */
  value: PropTypes.oneOfType([PropTypes.number, PropTypes.string]).isRequired,
  max: PropTypes.number.isRequired,
  onChange: PropTypes.func.isRequired,
  disabled: PropTypes.bool,
};

QuantityStepper.defaultProps = {
  disabled: false,
};
