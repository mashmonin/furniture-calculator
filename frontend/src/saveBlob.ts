// Мост десктопной оболочки (DesktopLauncher, JavaFX WebView) — там скачивание через a[download] не работает.
interface DesktopBridge {
  saveFile(filename: string, base64Content: string): boolean
}

function blobToBase64(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve((reader.result as string).split(',')[1] ?? '')
    reader.onerror = () => reject(reader.error ?? new Error('Не удалось прочитать файл'))
    reader.readAsDataURL(blob)
  })
}

// Сохраняет файл: в десктопном приложении — через нативное окно «Сохранить как», в браузере — обычной загрузкой.
export async function saveBlob(blob: Blob, filename: string): Promise<void> {
  const bridge = (window as unknown as { desktopBridge?: DesktopBridge }).desktopBridge
  if (bridge) {
    bridge.saveFile(filename, await blobToBase64(blob))
    return
  }
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}
