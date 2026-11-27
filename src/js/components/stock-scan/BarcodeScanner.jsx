import React, { useEffect, useRef, useState } from 'react';

import { BrowserMultiFormatReader } from '@zxing/browser';
import { BarcodeFormat, DecodeHintType } from '@zxing/library';
import PropTypes from 'prop-types';

import { normalizeBarcode } from 'components/stock-scan/barcode';
import useTranslate from 'hooks/useTranslate';

/** Retail formats only: fewer formats decode faster and misread less. */
const FORMATS = [
  BarcodeFormat.UPC_A, BarcodeFormat.UPC_E, BarcodeFormat.EAN_13, BarcodeFormat.EAN_8,
];

/**
 * How long to ignore the code that was just handled after the camera restarts, so an item still
 * in front of the camera isn't counted twice.
 */
const REPEAT_COOLDOWN_MS = 2500;

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
    let handled = false;
    const startedAt = Date.now();
    const hints = new Map([[DecodeHintType.POSSIBLE_FORMATS, FORMATS]]);
    const reader = new BrowserMultiFormatReader(hints, { delayBetweenScanAttempts: 150 });

    const onDecode = (result, decodeError, controls) => {
      if (!result || handled || cancelled) {
        return;
      }
      const code = normalizeBarcode(result.getText(), BarcodeFormat[result.getBarcodeFormat()]);
      if (code === ignoreCodeRef.current && Date.now() - startedAt < REPEAT_COOLDOWN_MS) {
        return;
      }
      handled = true;
      // Passed to the callback, so this works even before the start promise resolves
      controls.stop();
      onScanRef.current(code);
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
        {status === 'starting'
          ? translate('react.stockScan.startingCamera.label', 'Starting the camera…')
          : translate('react.stockScan.pointCamera.label', 'Point the camera at a barcode')}
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
  /** Called once with the normalized code of the first barcode read */
  onScan: PropTypes.func.isRequired,
  /** The code handled last; ignored briefly after the camera starts */
  ignoreCode: PropTypes.string,
};

BarcodeScanner.defaultProps = {
  ignoreCode: null,
};
