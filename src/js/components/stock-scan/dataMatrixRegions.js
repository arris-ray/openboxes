import { HTMLCanvasElementLuminanceSource } from '@zxing/browser';
import { BinaryBitmap, DataMatrixReader, HybridBinarizer } from '@zxing/library';

/**
 * ZXing looks for a DataMatrix outward from the middle of the image, so it misses one that's off
 * to the side, as it is when a drug's linear barcode is in the middle of the view. These regions
 * (overlapping halves of the frame, as fractions of its width and height) each put a different
 * part of the frame in the middle.
 */
const REGIONS = [
  [0, 0, 0.6, 1],
  [0.4, 0, 0.6, 1],
  [0, 0, 1, 0.6],
  [0, 0.4, 1, 0.6],
];

/**
 * @param {HTMLVideoElement} video the camera preview
 * @returns {function(): string|null} reads the current frame, region by region, and returns the
 *   text of the first DataMatrix found
 */
const createDataMatrixRegionScanner = (video) => {
  const reader = new DataMatrixReader();
  const canvas = document.createElement('canvas');
  const context = canvas.getContext('2d', { willReadFrequently: true });

  return () => {
    const width = video.videoWidth;
    const height = video.videoHeight;
    if (!width || !height || !context) {
      return null;
    }
    for (let i = 0; i < REGIONS.length; i += 1) {
      const [left, top, regionWidth, regionHeight] = REGIONS[i];
      const sw = Math.round(width * regionWidth);
      const sh = Math.round(height * regionHeight);
      canvas.width = sw;
      canvas.height = sh;
      const sx = Math.round(width * left);
      const sy = Math.round(height * top);
      context.drawImage(video, sx, sy, sw, sh, 0, 0, sw, sh);
      try {
        const source = new HTMLCanvasElementLuminanceSource(canvas);
        const bitmap = new BinaryBitmap(new HybridBinarizer(source));
        return reader.decode(bitmap).getText();
      } catch (e) {
        // Not found in this region
      }
    }
    return null;
  };
};

export default createDataMatrixRegionScanner;
