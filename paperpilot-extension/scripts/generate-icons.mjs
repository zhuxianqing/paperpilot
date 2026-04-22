import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// 简单的 PNG 文件头 + IHDR + IDAT + IEND
// 这是一个纯蓝色的 16x16 PNG
function generateSimplePNG(width, height, color) {
  const pngSignature = Buffer.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]);

  // IHDR chunk
  const ihdrData = Buffer.alloc(13);
  ihdrData.writeUInt32BE(width, 0);
  ihdrData.writeUInt32BE(height, 4);
  ihdrData.writeUInt8(8, 8); // bit depth
  ihdrData.writeUInt8(2, 9); // color type (RGB)
  ihdrData.writeUInt8(0, 10); // compression
  ihdrData.writeUInt8(0, 11); // filter
  ihdrData.writeUInt8(0, 12); // interlace

  const ihdrChunk = createChunk('IHDR', ihdrData);

  // IDAT chunk (compressed image data)
  const rawData = [];
  for (let y = 0; y < height; y++) {
    rawData.push(0); // filter byte
    for (let x = 0; x < width; x++) {
      rawData.push(color.r, color.g, color.b);
    }
  }

  const compressed = zlibDeflate(Buffer.from(rawData));
  const idatChunk = createChunk('IDAT', compressed);

  // IEND chunk
  const iendChunk = createChunk('IEND', Buffer.alloc(0));

  return Buffer.concat([pngSignature, ihdrChunk, idatChunk, iendChunk]);
}

function createChunk(type, data) {
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length, 0);

  const typeBuffer = Buffer.from(type);

  const chunkData = Buffer.concat([typeBuffer, data]);
  const crc = crc32(chunkData);

  const crcBuffer = Buffer.alloc(4);
  crcBuffer.writeUInt32BE(crc, 0);

  return Buffer.concat([length, chunkData, crcBuffer]);
}

// 简化的 CRC32 计算
function crc32(buffer) {
  let crc = 0xFFFFFFFF;
  const table = [];

  for (let i = 0; i < 256; i++) {
    let c = i;
    for (let j = 0; j < 8; j++) {
      c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
    }
    table[i] = c;
  }

  for (let i = 0; i < buffer.length; i++) {
    crc = table[(crc ^ buffer[i]) & 0xFF] ^ (crc >>> 8);
  }

  return (crc ^ 0xFFFFFFFF) >>> 0;
}

// 简化的 zlib deflate
function zlibDeflate(data) {
  // 使用简单的无压缩 deflate
  const result = [];
  result.push(0x78, 0x9C); // zlib header

  let pos = 0;
  while (pos < data.length) {
    const blockSize = Math.min(data.length - pos, 65535);
    const isLast = pos + blockSize >= data.length;

    result.push(isLast ? 0x01 : 0x00); // BFINAL + BTYPE
    result.push(blockSize & 0xFF, blockSize >>> 8);
    result.push((~blockSize) & 0xFF, (~blockSize) >>> 8);

    for (let i = 0; i < blockSize; i++) {
      result.push(data[pos + i]);
    }

    pos += blockSize;
  }

  const adler = adler32(data);
  result.push((adler >>> 24) & 0xFF, (adler >>> 16) & 0xFF, (adler >>> 8) & 0xFF, adler & 0xFF);

  return Buffer.from(result);
}

function adler32(data) {
  let a = 1, b = 0;
  for (let i = 0; i < data.length; i++) {
    a = (a + data[i]) % 65521;
    b = (b + a) % 65521;
  }
  return ((b << 16) | a) >>> 0;
}

// 生成图标
const assetsDir = path.join(__dirname, '..', 'public', 'assets');
if (!fs.existsSync(assetsDir)) {
  fs.mkdirSync(assetsDir, { recursive: true });
}

const primaryBlue = { r: 37, g: 99, b: 235 }; // #2563eb

[16, 48, 128].forEach(size => {
  const png = generateSimplePNG(size, size, primaryBlue);
  fs.writeFileSync(path.join(assetsDir, `icon${size}.png`), png);
  console.log(`Generated icon${size}.png`);
});

console.log('All icons generated!');
