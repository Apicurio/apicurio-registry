const path = require("path");
const { merge } = require("webpack-merge");
const common = require("./webpack.common.js");
const MiniCssExtractPlugin = require("mini-css-extract-plugin");
const OptimizeCSSAssetsPlugin = require("optimize-css-assets-webpack-plugin");
const HtmlWebpackPlugin = require("html-webpack-plugin");
// webpack 5 stop handling node polyfills by itself, this plugin re-enables the feature
const NodePolyfillPlugin = require("node-polyfill-webpack-plugin")

module.exports = merge(common, {
  mode: "production",
  devtool: "source-map",
  plugins: [
    new MiniCssExtractPlugin({
      filename: "[name].css",
      chunkFilename: "[id].css"
    }),
    new NodePolyfillPlugin(),
    new HtmlWebpackPlugin({
      template: "./src/index.html"
    }),
    new ModuleFederationPlugin({
      name: federatedModuleName,
      filename: "remoteEntry.js",
      exposes: {
        "./FederatedArtifactsPage": "./src/app/pages/artifacts/artifacts.federated",
        "./FederatedArtifactRedirectPage": "./src/app/pages/artifact/artifact.federated",
        "./FederatedArtifactVersionPage": "./src/app/pages/artifactVersion/artifactVersion.federated",
        "./FederatedRulesPage": "./src/app/pages/rules/rules.federated"
      },
      shared: {
        ...dependencies,
        react: {
          eager: true,
          singleton: true,
          requiredVersion: dependencies["react"],
        },
        "react-dom": {
          eager: true,
          singleton: true,
          requiredVersion: dependencies["react-dom"],
        },
        "react-router-dom": {
          singleton: true,
          requiredVersion: dependencies["react-router-dom"],
        },
      }
    })
  ],
  module: {
    rules: [
      {
        test: /\.css$/,
        include: [
          path.resolve(__dirname, "src"),
          path.resolve(__dirname, "node_modules/patternfly"),
          path.resolve(__dirname, "node_modules/@patternfly/patternfly"),
          path.resolve(__dirname, "node_modules/@patternfly/react-core/dist/styles/base.css"),
          path.resolve(__dirname, "node_modules/@patternfly/react-core/dist/esm/@patternfly/patternfly"),
          path.resolve(__dirname, "node_modules/@patternfly/react-styles/css")
        ],
        use: [MiniCssExtractPlugin.loader, "css-loader"]
      }
    ]
  },
  output: {
    filename: "[name].bundle.[contenthash].js"
  },
  optimization: {
    minimizer: [
      new OptimizeCSSAssetsPlugin({})
    ],
    moduleIds: "deterministic",
    runtimeChunk: "single",
    splitChunks: {
      cacheGroups: {
        vendor: {
          test: /[\\/]node_modules[\\/]/,
          name: "vendors",
          chunks: "all"
        }
      }
    }
  }
});
