import { rsbuildConfig } from "@halo-dev/ui-plugin-bundler-kit";
import { pluginSass } from "@rsbuild/plugin-sass";
import { UnoCSSRspackPlugin } from "@unocss/webpack/rspack";
import Icons from "unplugin-icons/rspack";

const OUT_DIR_PROD = "../src/main/resources/console";
const OUT_DIR_DEV = "../build/resources/main/console";

export default rsbuildConfig({
  rsbuild: ({ envMode }) => {
    const isProduction = envMode === "production";
    const outDir = isProduction ? OUT_DIR_PROD : OUT_DIR_DEV;

    return {
      plugins: [pluginSass()],
      resolve: {
        alias: {
          "@": "./src",
        },
      },
      output: {
        distPath: {
          root: outDir,
        },
      },
      performance: {
        // 单文件打包，避免线上 main.js 与 chunks 版本不一致导致 404
        chunkSplit: {
          strategy: "all-in-one",
        },
      },
      tools: {
        rspack: {
          cache: false,
          output: {
            asyncChunks: false,
          },
          module: {
            parser: {
              javascript: {
                // 将动态 import 内联进 main.js，不再生成独立 chunk 文件
                dynamicImportMode: "eager",
              },
            },
          },
          plugins: [
            Icons({
              compiler: "vue3",
            }),
            UnoCSSRspackPlugin(),
          ],
        },
      },
    };
  },
});