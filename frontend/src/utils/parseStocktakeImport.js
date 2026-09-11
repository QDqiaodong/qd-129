/**
 * 盘点实盘导入文本解析：
 * 支持 CSV / TSV / 换行粘贴，每行字段为
 *   资产编号, 实盘分区(编码或名称，可空), 启用状态(启用/停用/1/0，可空), 标签(多个用 , 、 ; 分隔名称，可空)
 * 首行表头含“资产编号”时自动跳过。
 * 标签列留空表示本次未核对标签（actualTagIds = null）；
 * 填入“无 / 无标签 / -”表示已核对为无标签（actualTagIds = []）。
 *
 * @returns {{ lines: Array, errors: string[] }} lines 可直接作为 submitActuals 的 lines
 */
export function parseStocktakeText(text, options = {}) {
  const areas = options.areas || []
  const tags = options.tags || []
  const lines = []
  const errors = []
  if (!text || !text.trim()) {
    return { lines, errors: ['导入内容为空'] }
  }

  const rows = text
    .split(/\r?\n/)
    .map(row => splitRow(row))
    .filter(cols => cols.some(c => c.trim() !== ''))

  if (rows.length === 0) {
    return { lines, errors: ['导入内容为空'] }
  }

  let startIndex = 0
  if (rows[0].some(col => col.includes('资产编号') || col.toLowerCase().includes('asset'))) {
    startIndex = 1
  }

  const seen = new Set()
  rows.slice(startIndex).forEach((cols, idx) => {
    const rowNo = startIndex + idx + 1
    const code = (cols[0] || '').trim()
    if (!code) {
      errors.push(`第 ${rowNo} 行缺少资产编号`)
      return
    }
    if (seen.has(code)) {
      errors.push(`资产编号重复：${code}（第 ${rowNo} 行）`)
      return
    }
    seen.add(code)

    const line = { assetCode: code, actualAreaId: null, actualStatus: null, actualTagIds: null }

    const areaText = (cols[1] || '').trim()
    if (areaText) {
      const area = areas.find(a => a.areaCode === areaText || a.areaName === areaText)
      if (!area) {
        errors.push(`第 ${rowNo} 行分区不存在：${areaText}`)
      } else {
        line.actualAreaId = area.id
      }
    }

    const statusText = (cols[2] || '').trim()
    if (statusText) {
      const status = parseStatus(statusText)
      if (status === null) {
        errors.push(`第 ${rowNo} 行启用状态无法识别：${statusText}（请填 启用/停用/1/0）`)
      } else {
        line.actualStatus = status
      }
    }

    const tagText = (cols[3] || '').trim()
    if (tagText) {
      if (['无', '无标签', '-'].includes(tagText)) {
        line.actualTagIds = []
      } else {
        const names = tagText.split(/[,，、;；]/).map(s => s.trim()).filter(Boolean)
        const ids = []
        names.forEach(name => {
          const tag = tags.find(t => t.tagName === name || t.tagCode === name)
          if (!tag) {
            errors.push(`第 ${rowNo} 行标签不存在：${name}`)
          } else {
            ids.push(tag.id)
          }
        })
        line.actualTagIds = [...new Set(ids)]
      }
    }

    lines.push(line)
  })

  return { lines, errors }
}

function splitRow(row) {
  // 简单 CSV：支持英文逗号、制表符、中文逗号作为分隔符
  return row.split(/[,\t，]/).map(c => c.trim())
}

function parseStatus(text) {
  if (['启用', '正常', '1', 'true', 'TRUE'].includes(text)) return 1
  if (['停用', '禁用', '0', 'false', 'FALSE'].includes(text)) return 0
  return null
}
