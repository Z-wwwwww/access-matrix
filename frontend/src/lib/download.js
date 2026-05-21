export function saveBlobResponse(res, fallbackName = 'download') {
  const disposition = res.headers?.['content-disposition']
  let filename = fallbackName
  if (disposition) {
    const match = disposition.match(/filename\*?=(?:UTF-8'')?([^;\n]+)/i)
    if (match) filename = decodeURIComponent(match[1].replace(/["']/g, ''))
  }
  const blob = new Blob([res.data])
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(link.href)
}
