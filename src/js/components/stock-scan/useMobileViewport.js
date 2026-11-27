import { useEffect } from 'react';

const MOBILE_VIEWPORT = 'width=device-width, initial-scale=1';

/**
 * The React layout (layouts/react.gsp) has no viewport meta tag, so phones render pages at
 * desktop width. Sets a mobile viewport while the component is mounted and restores the previous
 * one afterwards.
 */
const useMobileViewport = () => {
  useEffect(() => {
    let meta = document.querySelector('meta[name="viewport"]');
    const previous = meta ? meta.getAttribute('content') : null;
    const created = !meta;
    if (created) {
      meta = document.createElement('meta');
      meta.setAttribute('name', 'viewport');
      document.head.appendChild(meta);
    }
    meta.setAttribute('content', MOBILE_VIEWPORT);

    return () => {
      if (created) {
        meta.remove();
      } else {
        meta.setAttribute('content', previous);
      }
    };
  }, []);
};

export default useMobileViewport;
