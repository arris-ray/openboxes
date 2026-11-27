import React, { useEffect, useRef, useState } from 'react';

import { BrowserMultiFormatReader } from '@zxing/browser';
import { BarcodeFormat, DecodeHintType } from '@zxing/library';
import PropTypes from 'prop-types';

import { createScanResolver, normalizeBarcode } from 'components/stock-scan/barcode';
import createDataMatrixRegionScanner from 'components/stock-scan/dataMatrixRegions';
import { parseGs1 } from 'components/stock-scan/gs1';
import useTranslate from 'hooks/useTranslate';

/**
 * Retail formats, and DataMatrix for drug packages. Fewer formats decode faster and misread less.
 */
const FORMATS = [
  BarcodeFormat.UPC_A, BarcodeFormat.UPC_E, BarcodeFormat.EAN_13, BarcodeFormat.EAN_8,
  BarcodeFormat.DATA_MATRIX,
];

/**
 * @returns {object|null} what a decoded barcode says: {code, ndc, lot, expirationDate, matrix},
 *   or null if it isn't a product code
 */
const toRead = (text, format) => {
  if (format === 'DATA_MATRIX') {
    const gs1 = parseGs1(text);
    if (gs1) {
      return {
        code: gs1.gtin,
        ndc: gs1.ndc,
        lot: gs1.lot,
        expirationDate: gs1.expirationDate,
        matrix: true,
      };
    }
    // Some DataMatrix codes hold just a GTIN
    return /^\d{8,14}$/.test(text) ? { code: text, matrix: true } : null;
  }
  return { code: normalizeBarcode(text, format), matrix: false };
};

/**
 * How long to ignore the code that was just handled after the camera restarts, so an item still
 * in front of the camera isn't counted twice.
 */
const REPEAT_COOLDOWN_MS = 2500;

/** How often to look for a DataMatrix away from the middle of the view, while waiting for one. */
const REGION_SCAN_INTERVAL_MS = 200;

const CAMERA_CONSTRAINTS = {
  audio: false,
  video: {
    facingMode: { ideal: 'environment' },
    width: { ideal: 1280 },
    height: { ideal: 720 },
  },
};

/** The kind of camera problem, so the message is translated when rendered. */
const CameraProblem = {
  NEEDS_HTTPS: 'NEEDS_HTTPS',
  UNSUPPORTED: 'UNSUPPORTED',
  DENIED: 'DENIED',
  NOT_FOUND: 'NOT_FOUND',
  BUSY: 'BUSY',
  FAILED: 'FAILED',
};

const toCameraProblem = (error) => {
  switch (error?.name) {
    case 'NotAllowedError':
    case 'SecurityError':
      return CameraProblem.DENIED;
    case 'NotFoundError':
    case 'OverconstrainedError':
      return CameraProblem.NOT_FOUND;
    case 'NotReadableError':
      return CameraProblem.BUSY;
    default:
      return CameraProblem.FAILED;
  }
};

const cameraProblemMessage = (problem, translate) => {
  switch (problem) {
    case CameraProblem.NEEDS_HTTPS:
      return translate('react.stockScan.needsHttps.label', 'The camera only works over HTTPS. Type the barcode instead.');
    case CameraProblem.UNSUPPORTED:
      return translate('react.stockScan.noCameraSupport.label', 'This browser can\'t use the camera. Type the barcode instead.');
    case CameraProblem.DENIED:
      return translate(
        'react.stockScan.cameraDenied.label',
        'Camera access is blocked. Allow it for this site in Safari settings, or type the barcode.',
      );
    case CameraProblem.NOT_FOUND:
      return translate('react.stockScan.noCamera.label', 'No camera was found. Type the barcode instead.');
    case CameraProblem.BUSY:
      return translate(
        'react.stockScan.cameraBusy.label',
        'The camera is in use by another app. Close it and try again, or type the barcode.',
      );
    default:
      return translate('react.stockScan.cameraFailed.label', 'The camera couldn\'t start. Type the barcode instead.');
  }
};

/**
 * Live camera preview that reports the first retail barcode it reads. Mounted only while the
 * page is waiting for a scan, so the camera is off on the other screens.
 */
const BarcodeScanner = ({ onScan, ignoreCode }) => {
  const translate = useTranslate();
  const videoRef = useRef(null);
  const controlsRef = useRef(null);
  const [status, setStatus] = useState('starting');
  const [problem, setProblem] = useState(null);
  const [torchOn, setTorchOn] = useState(false);
  const [canTorch, setCanTorch] = useState(false);
  const [waitingForMatrix, setWaitingForMatrix] = useState(false);

  // Keep the latest props for the decode callback, which is created once
  const onScanRef = useRef(onScan);
  onScanRef.current = onScan;
  const ignoreCodeRef = useRef(ignoreCode);
  ignoreCodeRef.current = ignoreCode;

  useEffect(() => {
    if (!navigator.mediaDevices?.getUserMedia) {
      setStatus('unavailable');
      setProblem(window.isSecureContext ? CameraProblem.UNSUPPORTED : CameraProblem.NEEDS_HTTPS);
      return undefined;
    }

    let cancelled = false;
    let latestControls = null;
    let regionTimer = null;
    const stopRegionScan = () => {
      clearInterval(regionTimer);
      regionTimer = null;
    };
    const startedAt = Date.now();
    const hints = new Map([[DecodeHintType.POSSIBLE_FORMATS, FORMATS]]);
    const reader = new BrowserMultiFormatReader(hints, { delayBetweenScanAttempts: 150 });

    const resolver = createScanResolver({
      onWait: () => {
        setWaitingForMatrix(true);
        // The reader returns the first format it finds, trying linear barcodes first, so while a
        // drug's linear barcode is in view only a DataMatrix-only reader can see the DataMatrix
        reader.setHints(new Map([[DecodeHintType.POSSIBLE_FORMATS, [BarcodeFormat.DATA_MATRIX]]]));
        // That reader only finds a DataMatrix near the middle, so also look in each half of the
        // view
        const scanRegions = createDataMatrixRegionScanner(videoRef.current);
        regionTimer = setInterval(() => {
          const text = scanRegions();
          const read = text ? toRead(text, 'DATA_MATRIX') : null;
          if (read) {
            resolver.offer(read);
          }
        }, REGION_SCAN_INTERVAL_MS);
      },
      onResolve: (read) => {
        stopRegionScan();
        if (cancelled) {
          return;
        }
        latestControls?.stop();
        const {
          code, ndc, lot, expirationDate,
        } = read;
        onScanRef.current({
          code, ndc: ndc || null, lot: lot || null, expirationDate: expirationDate || null,
        });
      },
    });

    const onDecode = (result, decodeError, controls) => {
      if (!result || cancelled) {
        return;
      }
      // Passed to the callback, so the camera can be stopped even before the start promise resolves
      latestControls = controls;
      const read = toRead(result.getText(), BarcodeFormat[result.getBarcodeFormat()]);
      if (!read) {
        return;
      }
      const key = read.code || read.ndc;
      if (key === ignoreCodeRef.current && Date.now() - startedAt < REPEAT_COOLDOWN_MS) {
        return;
      }
      resolver.offer(read);
    };

    reader.decodeFromConstraints(CAMERA_CONSTRAINTS, videoRef.current, onDecode)
      .then((controls) => {
        if (cancelled) {
          controls.stop();
          return;
        }
        controlsRef.current = controls;
        setCanTorch(Boolean(controls.switchTorch));
        setStatus('scanning');
      })
      .catch((cameraError) => {
        if (!cancelled) {
          setStatus('unavailable');
          setProblem(toCameraProblem(cameraError));
        }
      });

    return () => {
      cancelled = true;
      resolver.cancel();
      stopRegionScan();
      controlsRef.current?.stop();
      controlsRef.current = null;
    };
    // Start the camera once per mount; the latest props are read through refs
  }, []);

  const toggleTorch = () => {
    const next = !torchOn;
    controlsRef.current?.switchTorch?.(next)
      .then(() => setTorchOn(next))
      .catch(() => setCanTorch(false));
  };

  if (status === 'unavailable') {
    return (
      <div className="stock-scan__camera-message" role="status">
        {cameraProblemMessage(problem, translate)}
      </div>
    );
  }

  return (
    <div className="stock-scan__camera">
      <video ref={videoRef} className="stock-scan__video" muted playsInline autoPlay />
      <div className="stock-scan__guide" aria-hidden="true" />
      <div className="stock-scan__camera-status">
        {status === 'starting' && translate('react.stockScan.startingCamera.label', 'Starting the camera…')}
        {status !== 'starting' && (waitingForMatrix
          ? translate('react.stockScan.lookingForMatrix.label', 'Hold still: looking for the square code with the lot and expiry')
          : translate('react.stockScan.pointCamera.label', 'Point the camera at a barcode'))}
      </div>
      {canTorch && (
        <button type="button" className="stock-scan__torch" onClick={toggleTorch} aria-pressed={torchOn}>
          {torchOn
            ? translate('react.stockScan.torchOff.label', 'Light off')
            : translate('react.stockScan.torchOn.label', 'Light on')}
        </button>
      )}
    </div>
  );
};

export default BarcodeScanner;

BarcodeScanner.propTypes = {
  /**
   * Called once with the first product code read: {code, ndc, lot, expirationDate}. code is the
   * normalized barcode (a GTIN-14 from a DataMatrix); the others come from a GS1 DataMatrix.
   */
  onScan: PropTypes.func.isRequired,
  /** The code (or NDC) handled last; ignored briefly after the camera starts */
  ignoreCode: PropTypes.string,
};

BarcodeScanner.defaultProps = {
  ignoreCode: null,
};
