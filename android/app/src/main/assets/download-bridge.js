(function () {
  if (window.__apiChatDownloadBridgeInstalled) return;
  window.__apiChatDownloadBridgeInstalled = true;

  document.addEventListener('click', function (event) {
    const target = event.target;
    const anchor = target && target.closest ? target.closest('a[download]') : null;
    if (!anchor || !window.AndroidDownloads) return;
    const href = anchor.href || '';
    if (!href.startsWith('blob:')) return;

    event.preventDefault();
    event.stopImmediatePropagation();
    const filename = anchor.download || 'export.txt';

    fetch(href)
      .then((response) => response.blob())
      .then((blob) => new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve({ base64: String(reader.result).split(',')[1], type: blob.type });
        reader.onerror = reject;
        reader.readAsDataURL(blob);
      }))
      .then((file) => {
        window.AndroidDownloads.saveBase64(
          file.base64,
          (file.type || 'application/octet-stream').split(';')[0],
          filename
        );
      })
      .catch((error) => console.error('APK export failed', error));
  }, true);
})();
