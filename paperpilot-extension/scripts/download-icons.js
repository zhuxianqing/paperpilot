// 下载占位图标
import fs from 'fs';
import path from 'path';
import https from 'https';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const icons = [
  { size: 16, file: 'icon16.png' },
  { size: 48, file: 'icon48.png' },
  { size: 128, file: 'icon128.png' }
];

const assetsDir = path.join(__dirname, '..', 'public', 'assets');

// 确保目录存在
if (!fs.existsSync(assetsDir)) {
  fs.mkdirSync(assetsDir, { recursive: true });
}

// 使用 placeholder.com 下载图标
async function downloadIcon(size, filename) {
  const url = `https://via.placeholder.com/${size}/2563eb/ffffff.png?text=P`;
  const filepath = path.join(assetsDir, filename);

  return new Promise((resolve, reject) => {
    https.get(url, (response) => {
      if (response.statusCode !== 200) {
        reject(new Error(`Failed to download: ${response.statusCode}`));
        return;
      }

      const data = [];
      response.on('data', chunk => data.push(chunk));
      response.on('end', () => {
        const buffer = Buffer.concat(data);
        fs.writeFileSync(filepath, buffer);
        console.log(`Downloaded: ${filename} (${buffer.length} bytes)`);
        resolve();
      });
    }).on('error', reject);
  });
}

async function main() {
  for (const { size, file } of icons) {
    try {
      await downloadIcon(size, file);
    } catch (error) {
      console.error(`Failed to download ${file}:`, error.message);
    }
  }
}

main();
