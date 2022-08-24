const path = require("path");
const HtmlWebpackPlugin = require("html-webpack-plugin");
const CopyPlugin = require("copy-webpack-plugin");
const TsconfigPathsPlugin = require("tsconfig-paths-webpack-plugin");
const Dotenv = require("dotenv-webpack");
const {dependencies, federatedModuleName} = require("./package.json");
delete dependencies.serve; // Needed for nodeshift bug
const webpack = require("webpack");
const ChunkMapper = require("@redhat-cloud-services/frontend-components-config/chunk-mapper");
const MiniCssExtractPlugin = require("mini-css-extract-plugin");
const NodePolyfillPlugin = require('node-polyfill-webpack-plugin');

const isPatternflyStyles = (stylesheet) => stylesheet.includes("@patternfly/react-styles/css/") || stylesheet.includes("@patternfly/react-core/");


const cmdArgs = {};
process.argv.forEach(arg => {
  if (arg && arg.startsWith("--env=")) {
    const idx = arg.indexOf(":");
    const name = arg.substring(6, idx);
    const value = arg.substring(idx+1);
    cmdArgs[name] = value;
  }
});


module.exports = (env, argv) => {
  const isProduction = argv && argv.mode === "production";
  console.info("Is production build? %o", isProduction);
  return {
    entry: {
      app: path.resolve(__dirname, "src", "index.tsx")
    },
    module: {
      rules: [
        {
          test: /\.(tsx|ts|jsx)?$/,
          use: [
            {
              loader: "ts-loader",
              options: {
                transpileOnly: true,
                experimentalWatchApi: true,
              }
            }
          ]
        },
        {
          test: /\.css$/,
          use: [MiniCssExtractPlugin.loader, "css-loader"],
          include: (stylesheet => !isPatternflyStyles(stylesheet)),
          sideEffects: true
        },
        {
          test: /\.css$/,
          include: isPatternflyStyles,
          use: ["null-loader"],
          sideEffects: true
        },
        {
          test: /\.(ttf|eot|woff|woff2)$/,
          use: {
            loader: "file-loader",
            options: {
              limit: 5000,
              name: "[contenthash:8].[ext]",
            }
          }
        },
        {
          test: /\.(svg|jpg|jpeg|png|gif)$/i,
          use: [
            {
              loader: "url-loader",
              options: {
                limit: 5000,
                name: isProduction ? "[contenthash:8].[ext]" : "[name].[ext]",
              }
            }
          ]
        }
      ]
    },
    output: {
      filename: "[name].bundle.js",
      path: path.resolve(__dirname, "dist"),
      publicPath: "auto"
    },
    plugins: [
      new HtmlWebpackPlugin({
        template: "./src/index.html"
      }),
      new Dotenv({
        systemvars: true,
        silent: true
      }),
      new MiniCssExtractPlugin({
        filename: "[name].[contenthash:8].css",
        chunkFilename: "[contenthash:8].css",
        ignoreOrder: true,
        insert: (linkTag) => {
          const preloadLinkTag = document.createElement("link")
          preloadLinkTag.rel = "preload"
          preloadLinkTag.as = "style"
          preloadLinkTag.href = linkTag.href
          document.head.appendChild(preloadLinkTag)
          document.head.appendChild(linkTag)
        }
      }),
      new ChunkMapper({
        modules: [
          federatedModuleName
        ]
      }),
      new webpack.container.ModuleFederationPlugin({
        name: federatedModuleName,
        filename: `${federatedModuleName}${isProduction ? ".[chunkhash:8]" : ""}.js`,
        exposes: {
          "./FederatedArtifactsPage": "./src/app/pages/artifacts/artifacts.federated",
          "./FederatedArtifactRedirectPage": "./src/app/pages/artifact/artifact.federated",
          "./FederatedArtifactVersionPage": "./src/app/pages/artifactVersion/artifactVersion.federated",
          "./FederatedRulesPage": "./src/app/pages/rules/rules.federated",
          "./FederatedRolesPage": "./src/app/pages/roles/roles.federated",
          "./FederatedSettingsPage": "./src/app/pages/settings/settings.federated",
          "./FederatedSchemaMapping": "./src/app/components/schemaMapping/schemaMapping.federated",
          "./FederatedDownloadArtifacts":"./src/app/components/downloadArtifacts/downloadArtifacts.federated"
        },
        shared: {
          ...dependencies,
          react: {
            singleton: true,
            requiredVersion: dependencies["react"],
          },
          "react-dom": {
            singleton: true,
            requiredVersion: dependencies["react-dom"],
          },
          "react-router-dom": {
            singleton: true,
            requiredVersion: dependencies["react-router-dom"],
          },
        }
      }),
      new NodePolyfillPlugin()
    ],
    resolve: {
      extensions: [".js", ".ts", ".tsx", ".jsx"],
      plugins: [
        new TsconfigPathsPlugin({
          configFile: path.resolve(__dirname, "./tsconfig.json")
        })
      ],
      fallback: {
        tty: require.resolve("tty-browserify")
      },
      symlinks: false,
      cacheWithContext: false
    },
    performance: {
      hints: false,
      maxEntrypointSize: 2097152,
      maxAssetSize: 1048576
    }
  }
};
