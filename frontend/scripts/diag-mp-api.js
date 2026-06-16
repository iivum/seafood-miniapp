// 最终 diag:从 home page context 调 ProductAPI.getProducts,看 response 实际结构
const automator = require('miniprogram-automator');

async function main() {
  const miniProgram = await automator.connect({ wsEndpoint: 'ws://127.0.0.1:9420' });
  console.log('[diag4] connected');
  miniProgram.on('console', (msg) => console.log(`[${msg.type}] ${msg.message}`));

  await miniProgram.reLaunch('/pages/index/index');
  await new Promise((r) => setTimeout(r, 5000));

  // 从 home page this 上下文找 ProductAPI
  const result = await miniProgram.evaluate(() => {
    return new Promise((resolve) => {
      const pages = getCurrentPages();
      const home = pages.find((p) => p.route === 'pages/index/index');
      if (!home) { resolve({ err: 'home not found' }); return; }
      const module = home.productModule;
      if (!module) { resolve({ err: 'module missing' }); return; }
      // 用 module.loadProducts 重新调一次,看 response
      module.loadProducts({ page: 0, pageSize: 3 })
        .then(() => {
          resolve({
            ok: true,
            afterLoadProducts: module.state.products?.length,
            firstName: module.state.products?.[0]?.name,
            totalProducts: module.state.pagination?.totalProducts,
            totalPages: module.state.pagination?.totalPages,
          });
        })
        .catch((e) => resolve({ ok: false, err: e.message, stack: e.stack }));
      setTimeout(() => resolve({ ok: false, err: 'timeout' }), 6000);
    });
  });
  console.log('[diag4] result:', JSON.stringify(result, null, 2));

  miniProgram.disconnect();
}

main().catch((e) => { console.error(e); process.exit(1); });

