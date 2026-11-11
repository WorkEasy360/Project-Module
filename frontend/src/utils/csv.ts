/** One CSV field, quoted when needed and protected against spreadsheet formula injection. */
function csvField(value: string | number | null | undefined): string {
  let text = value === null || value === undefined ? '' : String(value)
  // A cell starting with one of these runs as a formula when opened in Excel.
  if (/^[=+\-@\t\r]/.test(text)) text = `'${text}`
  return /[",\n\r]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
}

export function toCsv(headers: string[], rows: (string | number | null | undefined)[][]): string {
  return [headers, ...rows].map((row) => row.map(csvField).join(',')).join('\r\n')
}

/** Saves text as a file. The leading byte-order mark makes Excel read it as UTF-8. */
export function downloadCsv(fileName: string, csv: string): void {
  const url = URL.createObjectURL(new Blob(['﻿', csv], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
}
