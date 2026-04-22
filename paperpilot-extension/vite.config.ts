import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { resolve } from 'path';

export default defineConfig({
  plugins: [vue()],
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
