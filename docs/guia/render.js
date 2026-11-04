// Regenera la guía en PDF:  node docs/guia/render.js   (requiere playwright)
const fs = require('fs'), path = require('path');
const { chromium } = require('playwright');
(async () => {
  const svg = fs.readFileSync(path.join(__dirname, 'qr.svg'), 'utf8');
  const html = fs.readFileSync(path.join(__dirname, 'guia.html'), 'utf8')
    .replace('__QR__', svg.slice(svg.indexOf('<svg')));
  const tmp = path.join(__dirname, 'guia_build.html');
  fs.writeFileSync(tmp, html);
  const b = await chromium.launch(); const p = await b.newPage();
  await p.goto('file://' + tmp); await p.waitForTimeout(500);
  await p.pdf({ path: path.join(__dirname, '..', 'Guia-instalacion-y-uso.pdf'), format: 'A4', printBackground: true,
    displayHeaderFooter: true, headerTemplate: '<span></span>',
    footerTemplate: '<div style="font-family:Inter,sans-serif;font-size:8pt;color:#777;width:100%;text-align:center;">Lee tu agenda · Guía de instalación y uso · <span class="pageNumber"></span>/<span class="totalPages"></span></div>',
    margin: { top: '18mm', bottom: '18mm', left: '18mm', right: '18mm' } });
  await b.close(); fs.unlinkSync(tmp);
})();
