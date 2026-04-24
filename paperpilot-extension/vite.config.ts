import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';
import fs from 'fs';

const __dirname = dirname(fileURLToPath(import.meta.url));

// Plugin: move HTML files from dist/src/x/ to dist/x/
function moveHtmlPlugin() {
  return {
    name: 'move-html',
    closeBundle() {
      const outDir = resolve(__dirname, 'dist');

      const htmlEntries = [
        { from: 'src/popup', to: 'popup' },
        { from: 'src/options', to: 'options' }
      ];

      for (const { from, to } of htmlEntries) {
        const srcPath = resolve(outDir, from, 'index.html');
        const destDir = resolve(outDir, to);
        const destPath = resolve(destDir, 'index.html');

        if (fs.existsSync(srcPath)) {
          fs.mkdirSync(destDir, { recursive: true });
          fs.copyFileSync(srcPath, destPath);
          console.log(`[vite] Moved ${from}/index.html -> ${to}/index.html`);
        }
      }

      // Clean up dist/src/
      const srcDir = resolve(outDir, 'src');
      if (fs.existsSync(srcDir)) {
        fs.rmSync(srcDir, { recursive: true, force: true });
      }
    }
  };
}

export default defineConfig({
  plugins: [vue(), moveHtmlPlugin()],
  // publicDir 默认为 'public'，manifest.json 会自动复制到 dist
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
      '@shared': resolve(__dirname, 'src/shared'),
      '@popup': resolve(__dirname, 'src/popup'),
      '@content': resolve(__dirname, 'src/content'),
      '@background': resolve(__dirname, 'src/background'),
      '@options': resolve(__dirname, 'src/options')
    }
  },
  build: {
    rollupOptions: {
      input: {
        popup: resolve(__dirname, 'src/popup/index.html'),
        options: resolve(__dirname, 'src/options/index.html'),
        content: resolve(__dirname, 'src/content/index.ts'),
        'hook-inject': resolve(__dirname, 'src/content/hook-inject.ts'),
        background: resolve(__dirname, 'src/background/index.ts')
      },
      output: {
        entryFileNames: (chunkInfo) => {
          if (chunkInfo.name === 'content' || chunkInfo.name === 'hook-inject') {
            return 'content/[name].js';
          }
          if (chunkInfo.name === 'background') {
            return 'background/[name].js';
          }
          return '[name]/[name].js';
        },
        chunkFileNames: 'chunks/[name].[hash].js',
        assetFileNames: (assetInfo) => {
          const info = assetInfo.name?.split('.') || [];
          const ext = info[info.length - 1];
          if (assetInfo.name?.endsWith('.css')) {
            return 'assets/[name][extname]';
          }
          return `assets/[name][extname]`;
        }
      }
    },
    outDir: 'dist',
    emptyOutDir: true
  }
});
