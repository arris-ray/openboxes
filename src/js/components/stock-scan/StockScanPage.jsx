import React, { useCallback, useEffect, useState } from 'react';

import { useSelector } from 'react-redux';
import { getCurrentLocation } from 'selectors';

import stockScanApi from 'api/services/StockScanApi';
import BarcodeEntry from 'components/stock-scan/BarcodeEntry';
import BarcodeScanner from 'components/stock-scan/BarcodeScanner';
import flattenCategories from 'components/stock-scan/flattenCategories';
import FoundProduct from 'components/stock-scan/FoundProduct';
import NewProductForm from 'components/stock-scan/NewProductForm';
import ProductMatchList from 'components/stock-scan/ProductMatchList';
import {
  MODE_STORAGE_KEY, ScanMode, Screen,
} from 'components/stock-scan/stockScanConstants';
import useMobileViewport from 'components/stock-scan/useMobileViewport';
import { INVENTORY_ITEM_URL } from 'consts/applicationUrls';
import useTranslate from 'hooks/useTranslate';
import { handleError } from 'utils/apiClient';

import './StockScanPage.scss';

/** Errors the API explains itself (bad quantity, nothing in stock, ...), shown next to the form. */
const INLINE_ERROR_STATUSES = [400, 403, 404, 409];

const readStoredMode = () => {
  try {
    return localStorage.getItem(MODE_STORAGE_KEY) === ScanMode.USE ? ScanMode.USE : ScanMode.ADD;
  } catch (e) {
    return ScanMode.ADD;
  }
};

/**
 * Phone-first page for adding stock to, and using stock from, the current location by scanning
 * retail barcodes.
 */
const StockScanPage = () => {
  useMobileViewport();
  const translate = useTranslate();
  const location = useSelector(getCurrentLocation);

  const [mode, setMode] = useState(readStoredMode);
  const [screen, setScreen] = useState(Screen.SCAN);
  const [barcode, setBarcode] = useState(null);
  // Lot and expiry read from a DataMatrix with the scan, to fill in the forms
  const [scannedLot, setScannedLot] = useState(null);
  // The last code looked up; the camera briefly ignores it when it restarts
  const [lastScanned, setLastScanned] = useState(null);
  const [matches, setMatches] = useState([]);
  // For a code no product has: the providers' details, and the server's suggested new product
  const [unknown, setUnknown] = useState(null);
  const [selected, setSelected] = useState(null);
  const [categories, setCategories] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);
  const [banner, setBanner] = useState(null);

  useEffect(() => {
    try {
      localStorage.setItem(MODE_STORAGE_KEY, mode);
    } catch (e) {
      // Private browsing can refuse storage; the mode just won't be remembered
    }
  }, [mode]);

  const toErrorMessage = useCallback((requestError) => {
    const status = requestError?.response?.status;
    if (INLINE_ERROR_STATUSES.includes(status)) {
      return requestError.response.data?.errorMessage
        || translate('react.stockScan.requestFailed.label', 'That didn\'t work. Try again.');
    }
    // Session expiry shows the login dialog; other failures show the usual notification
    handleError(requestError).catch(() => {});
    return translate('react.stockScan.requestFailed.label', 'That didn\'t work. Try again.');
  }, [translate]);

  const backToScan = () => {
    setScreen(Screen.SCAN);
    setBarcode(null);
    setScannedLot(null);
    setMatches([]);
    setUnknown(null);
    setSelected(null);
    setError(null);
  };

  /**
   * @param {string|object} input a typed code, or a camera read: {code, ndc, lot, expirationDate}
   */
  const lookup = async (input) => {
    const read = typeof input === 'string' ? { code: input } : input;
    const shown = read.code || read.ndc;
    setBanner(null);
    setError(null);
    setBarcode(shown);
    setLastScanned(shown);
    setScannedLot(read.lot || read.expirationDate
      ? { lotNumber: read.lot || '', expirationDate: read.expirationDate || '' }
      : null);
    setScreen(Screen.LOADING);
    try {
      const response = await stockScanApi.lookup(read.code, read.ndc);
      const found = response.data.data;
      setMatches(found);
      setUnknown(found.length ? null : {
        facts: response.data.facts || null,
        draft: response.data.newProduct || null,
      });
      if (found.length === 1) {
        setSelected(found[0]);
        setScreen(Screen.FOUND);
      } else if (found.length > 1) {
        setScreen(Screen.MATCHES);
      } else {
        setScreen(Screen.UNKNOWN);
      }
    } catch (requestError) {
      setScreen(Screen.SCAN);
      setBanner({ tone: 'error', text: toErrorMessage(requestError) });
    }
  };

  useEffect(() => {
    if (screen !== Screen.UNKNOWN || mode !== ScanMode.ADD || categories) {
      return;
    }
    stockScanApi.getCategories()
      .then((response) => setCategories(flattenCategories(response.data.data)))
      .catch((requestError) => {
        setCategories([]);
        setError(toErrorMessage(requestError));
      });
  }, [screen, mode, categories, toErrorMessage]);

  const perform = async (payload) => {
    setSubmitting(true);
    setError(null);
    try {
      const response = await stockScanApi.perform(payload);
      const result = response.data.data;
      const verb = result.action === ScanMode.USE
        ? translate('react.stockScan.used.label', 'Used')
        : translate('react.stockScan.added.label', 'Added');
      setBanner({
        tone: result.action === ScanMode.USE ? 'use' : 'add',
        text: `${verb} ${result.quantity} × ${result.product.name}. ${translate('react.stockScan.onHand.label', 'On hand')}: ${result.quantityOnHand}.`,
        productId: result.product.id,
      });
      backToScan();
    } catch (requestError) {
      setError(toErrorMessage(requestError));
    } finally {
      setSubmitting(false);
    }
  };

  const switchMode = (newMode) => {
    setMode(newMode);
    setError(null);
  };

  if (!location?.id) {
    return (
      <div className="stock-scan">
        <p className="stock-scan__message">{translate('react.stockScan.loading.label', 'Loading…')}</p>
      </div>
    );
  }

  return (
    <div className={`stock-scan stock-scan--${mode === ScanMode.USE ? 'use' : 'add'}`}>
      <header className="stock-scan__header">
        <h1 className="stock-scan__title">{translate('react.stockScan.title.label', 'Stock scan')}</h1>
        <div className="stock-scan__location">{location.name}</div>
      </header>

      <div className="stock-scan__mode" role="radiogroup" aria-label={translate('react.stockScan.mode.label', 'Mode')}>
        {[ScanMode.ADD, ScanMode.USE].map((option) => (
          <button
            key={option}
            type="button"
            role="radio"
            aria-checked={mode === option}
            className={`stock-scan__mode-option stock-scan__mode-option--${option.toLowerCase()}${mode === option ? ' is-active' : ''}`}
            onClick={() => switchMode(option)}
            disabled={submitting}
          >
            {option === ScanMode.ADD
              ? translate('react.stockScan.modeAdd.label', 'Add')
              : translate('react.stockScan.modeUse.label', 'Use')}
          </button>
        ))}
      </div>

      {banner && (
        <div className={`stock-scan__banner stock-scan__banner--${banner.tone}`} role="status">
          {banner.text}
          {banner.productId && (
            <>
              {' '}
              <a href={INVENTORY_ITEM_URL.showStockCard(banner.productId)}>
                {translate('react.stockScan.stockCard.label', 'Stock card')}
              </a>
            </>
          )}
        </div>
      )}

      {screen === Screen.SCAN && (
        <div className="stock-scan__panel">
          <p className="stock-scan__message">
            {mode === ScanMode.USE
              ? translate('react.stockScan.scanToUse.label', 'Scan an item to record using it.')
              : translate('react.stockScan.scanToAdd.label', 'Scan an item to add it to stock.')}
          </p>
          <BarcodeScanner onScan={lookup} ignoreCode={lastScanned} />
          <BarcodeEntry onSubmit={lookup} />
        </div>
      )}

      {screen === Screen.LOADING && (
        <div className="stock-scan__panel">
          <p className="stock-scan__message">
            {translate('react.stockScan.lookingUp.label', 'Looking up')}
            {` ${barcode}…`}
          </p>
          <p className="stock-scan__message stock-scan__message--muted">
            {translate('react.stockScan.lookingUpDetails.label', 'New products can take a few seconds while their details are found.')}
          </p>
        </div>
      )}

      {screen === Screen.MATCHES && (
        <ProductMatchList
          matches={matches}
          onSelect={(match) => {
            setSelected(match);
            setScreen(Screen.FOUND);
          }}
          onCancel={backToScan}
        />
      )}

      {screen === Screen.FOUND && selected && (
        <FoundProduct
          // A new key resets the form when the product or mode changes
          key={`${selected.product.id}-${mode}`}
          mode={mode}
          productStock={selected}
          scannedLot={scannedLot}
          submitting={submitting}
          error={error}
          onSubmit={perform}
          onCancel={backToScan}
          onSwitchMode={() => switchMode(ScanMode.ADD)}
        />
      )}

      {screen === Screen.UNKNOWN && mode === ScanMode.ADD && (
        categories ? (
          <NewProductForm
            barcode={barcode}
            draft={unknown?.draft}
            scannedLot={scannedLot}
            facts={unknown?.facts}
            categories={categories}
            submitting={submitting}
            error={error}
            onSubmit={perform}
            onCancel={backToScan}
          />
        ) : (
          <div className="stock-scan__panel">
            <p className="stock-scan__message">{translate('react.stockScan.loading.label', 'Loading…')}</p>
          </div>
        )
      )}

      {screen === Screen.UNKNOWN && mode === ScanMode.USE && (
        <div className="stock-scan__panel">
          <p className="stock-scan__message">
            {unknown?.facts?.name
              ? `${unknown.facts.name} ${translate('react.stockScan.notInOpenBoxes.label', 'isn\'t in OpenBoxes yet.')}`
              : `${translate('react.stockScan.unknownBarcode.label', 'No product has barcode')} ${barcode}.`}
          </p>
          <button type="button" className="stock-scan__button stock-scan__button--add" onClick={() => switchMode(ScanMode.ADD)}>
            {translate('react.stockScan.switchToAdd.label', 'Switch to Add')}
          </button>
          <button type="button" className="stock-scan__button stock-scan__button--link" onClick={backToScan}>
            {translate('react.stockScan.scanAnother.label', 'Scan another')}
          </button>
        </div>
      )}
    </div>
  );
};

export default StockScanPage;
