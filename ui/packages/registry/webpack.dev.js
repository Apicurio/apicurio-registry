const path = require('path');
const { merge } = require('webpack-merge');
const common = require("./webpack.common.js");
const CopyWebpackPlugin = require('copy-webpack-plugin');
const HtmlWebpackPlugin = require('html-webpack-plugin');
// webpack 5 stop handling node polyfills by itself, this plugin re-enables the feature
const NodePolyfillPlugin = require("node-polyfill-webpack-plugin")
const { ModuleFederationPlugin } = require("webpack").container;
const {federatedModuleName, dependencies} = require("./package.json");

const HOST = process.env.HOST || "localhost";
const PORT = process.env.PORT || "8888";

module.exports = merge(common, {
  mode: "development",
  devtool: "eval-source-map",
  plugins: [
    new CopyWebpackPlugin({
      patterns: [
        {from: './src/version.js'},
        {from: './src/config.js'}
      ]}),
    new NodePolyfillPlugin(),
    new HtmlWebpackPlugin({
      template: './src/index.html'
    }),
    new ModuleFederationPlugin({
      name: federatedModuleName,
      filename: "remoteEntry.js",
      exposes: {
        "./ArtifactTypeIcon": "./src/app/components/common/artifactTypeIcon",
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
      }
    })
  ],
  output: {
    publicPath: "auto"
  },
  devServer: {
    contentBase: "./dist",
    host: HOST,
    port: PORT,
    compress: true,
    inline: true,
    historyApiFallback: true,
    hot: true,
    overlay: true,
    open: true
  },
  module: {
    rules: [
      {
        test: /\.css$/,
        include: [
          path.resolve(__dirname, 'src'),
          path.resolve(__dirname, 'node_modules/patternfly'),
          path.resolve(__dirname, 'node_modules/@patternfly/patternfly'),
          path.resolve(__dirname, 'node_modules/@patternfly/react-styles/css'),
          path.resolve(__dirname, 'node_modules/@patternfly/react-core/dist/styles/base.css'),
          path.resolve(__dirname, 'node_modules/@patternfly/react-core/dist/esm/@patternfly/patternfly')
        ],
        use: ["style-loader", "css-loader"]
      }
    ]
  }
});
