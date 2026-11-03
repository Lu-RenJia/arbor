#!/usr/bin/env node
/**
 * Arbor 演示用 Mock API
 * ------------------------------------------------------------
 * 零依赖，仅使用 Node.js 内置模块。启动：
 *   node demo/mock-api/server.mjs
 * 默认端口 8090，可用 DEMO_MOCK_PORT 覆盖。
 *
 * 接口：
 *   GET  /api/demo/ping
 *   GET  /api/demo/echo
 *   GET  /api/demo/orders?status=PAID&limit=5
 *   POST /api/demo/order-stat          body: {"status":"PAID"} 或 {"orders":[...]}
 *   GET  /api/demo/rate?currency=USD
 *   GET  /api/demo/token
 *   GET  /api/demo/fail?mode=business|http|slow|timeout
 *
 * 所有响应均为 JSON，并带 CORS 头（API 节点由后端调用，CORS 仅便于浏览器调试）。
 */
import http from 'node:http'

const PORT = Number(process.env.DEMO_MOCK_PORT || 8090)
const HOST = process.env.DEMO_MOCK_HOST || '127.0.0.1'

const ORDERS = [
  { order_no: 'DEMO-1001', customer_name: '张伟', product_name: '机械键盘', quantity: 1, amount: 399.00, status: 'PAID', order_date: '2026-10-01 10:12:00' },
  { order_no: 'DEMO-1002', customer_name: '李娜', product_name: '无线鼠标', quantity: 2, amount: 258.00, status: 'SHIPPED', order_date: '2026-10-02 14:20:00' },
  { order_no: 'DEMO-1003', customer_name: '王强', product_name: '27寸显示器', quantity: 1, amount: 1599.00, status: 'COMPLETED', order_date: '2026-10-03 09:05:00' },
  { order_no: 'DEMO-1004', customer_name: '赵敏', product_name: 'USB-C 扩展坞', quantity: 1, amount: 329.00, status: 'PAID', order_date: '2026-10-04 16:42:00' },
  { order_no: 'DEMO-1005', customer_name: '陈晨', product_name: '降噪耳机', quantity: 1, amount: 899.00, status: 'CANCELLED', order_date: '2026-10-05 11:30:00' },
  { order_no: 'DEMO-1006', customer_name: '刘洋', product_name: '固态硬盘 1TB', quantity: 1, amount: 599.00, status: 'PAID', order_date: '2026-10-06 08:55:00' },
  { order_no: 'DEMO-1007', customer_name: '孙悦', product_name: '笔记本支架', quantity: 2, amount: 178.00, status: 'SHIPPED', order_date: '2026-10-07 19:10:00' },
  { order_no: 'DEMO-1008', customer_name: '周杰', product_name: '桌面音箱', quantity: 1, amount: 449.00, status: 'COMPLETED', order_date: '2026-10-08 12:00:00' },
]

const RATES = { USD: 7.12, EUR: 7.68, JPY: 0.047, HKD: 0.91, GBP: 8.95 }

function json(res, status, body) {
  const payload = JSON.stringify(body)
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(payload),
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET,POST,OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type,Authorization',
  })
  res.end(payload)
}

function readBody(req) {
  return new Promise((resolve) => {
    let raw = ''
    let size = 0
    req.on('data', (chunk) => {
      size += chunk.length
      if (size > 1024 * 1024) { raw = ''; req.destroy(); return }
      raw += chunk.toString('utf8')
    })
    req.on('end', () => {
      if (!raw) return resolve(null)
      try { resolve(JSON.parse(raw)) } catch { resolve(raw) }
    })
    req.on('error', () => resolve(null))
  })
}

function filterOrders(url) {
  const status = (url.searchParams.get('status') || '').trim().toUpperCase()
  const limit = Math.min(Math.max(Number(url.searchParams.get('limit') || 20) || 20, 1), 50)
  return ORDERS.filter((o) => !status || o.status === status).slice(0, limit)
}

function round2(n) { return Math.round(n * 100) / 100 }

const server = http.createServer(async (req, res) => {
  const started = Date.now()
  const url = new URL(req.url, `http://${req.headers.host || HOST}`)
  const method = (req.method || 'GET').toUpperCase()
  console.log(`[mock-api] ${new Date().toISOString()} ${method} ${url.pathname}${url.search}`)

  if (method === 'OPTIONS') {
    res.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET,POST,OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type,Authorization',
      'Access-Control-Max-Age': '3600',
    })
    return res.end()
  }

  try {
    if (url.pathname === '/api/demo/ping') {
      return json(res, 200, { code: 0, message: 'pong', timestamp: new Date().toISOString() })
    }

    if (url.pathname === '/api/demo/echo') {
      const body = method === 'POST' ? await readBody(req) : null
      return json(res, 200, {
        code: 0,
        method,
        path: url.pathname,
        query: Object.fromEntries(url.searchParams.entries()),
        headers: { 'content-type': req.headers['content-type'] || null, 'x-demo-header': req.headers['x-demo-header'] || null },
        body,
        timestamp: new Date().toISOString(),
      })
    }

    if (url.pathname === '/api/demo/orders' && method === 'GET') {
      const data = filterOrders(url)
      return json(res, 200, { code: 0, total: data.length, data })
    }

    if (url.pathname === '/api/demo/order-stat' && method === 'POST') {
      const body = await readBody(req)
      let rows = ORDERS
      if (body && typeof body === 'object' && Array.isArray(body.orders)) {
        rows = body.orders
      } else if (body && typeof body === 'object' && body.status) {
        rows = ORDERS.filter((o) => o.status === String(body.status).toUpperCase())
      }
      const byStatus = {}
      let totalAmount = 0
      for (const o of rows) {
        const amount = Number(o.amount) || 0
        totalAmount += amount
        byStatus[o.status] = (byStatus[o.status] || 0) + 1
      }
      return json(res, 200, {
        code: 0,
        count: rows.length,
        totalAmount: round2(totalAmount),
        byStatus,
        timestamp: new Date().toISOString(),
      })
    }

    if (url.pathname === '/api/demo/rate' && method === 'GET') {
      const currency = (url.searchParams.get('currency') || 'USD').toUpperCase()
      const rate = RATES[currency]
      if (rate == null) {
        return json(res, 200, { code: 4001, message: `不支持的币种：${currency}`, currency })
      }
      return json(res, 200, {
        code: 0,
        currency,
        rate,
        source: 'mock',
        timestamp: new Date().toISOString(),
      })
    }

    if (url.pathname === '/api/demo/token' && method === 'GET') {
      return json(res, 200, {
        code: 0,
        token: 'demo-token-' + Math.random().toString(36).slice(2, 10),
        expiresIn: 3600,
        timestamp: new Date().toISOString(),
      })
    }

    if (url.pathname === '/api/demo/fail' && method === 'GET') {
      const mode = (url.searchParams.get('mode') || 'business').toLowerCase()
      if (mode === 'http') {
        return json(res, 500, { code: 500, message: '模拟 HTTP 500 服务端错误', mode })
      }
      if (mode === 'slow') {
        await new Promise((r) => setTimeout(r, 5000))
        return json(res, 200, { code: 0, message: '慢响应：已等待 5 秒', mode, elapsedMs: Date.now() - started })
      }
      if (mode === 'timeout') {
        await new Promise((r) => setTimeout(r, 15000))
        return json(res, 200, { code: 0, message: '超时响应：已等待 15 秒', mode })
      }
      return json(res, 200, { code: 5001, message: '模拟业务失败：订单状态不允许继续处理', mode: 'business' })
    }

    return json(res, 404, { code: 404, message: `未实现的演示接口：${method} ${url.pathname}` })
  } catch (e) {
    console.error('[mock-api] error', e)
    return json(res, 500, { code: 500, message: String(e?.message || e) })
  }
})

server.listen(PORT, HOST, () => {
  console.log(`[mock-api] listening on http://${HOST}:${PORT}`)
  console.log('[mock-api] try: curl http://127.0.0.1:' + PORT + '/api/demo/ping')
})
