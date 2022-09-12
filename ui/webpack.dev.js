const path = require("path");
const { merge } = require("webpack-merge");
const common = require("./webpack.common.js");
const CopyWebpackPlugin = require("copy-webpack-plugin");

const HOST = process.env.HOST || "localhost";
const PORT = process.env.PORT || "8888";
const PROTOCOL = process.env.PROTOCOL || "http";

module.exports = merge(common("development"), {
  mode: "development",
  devtool: "eval-source-map",
  devServer: {
    static: {
      directory: "./dist",
    },
    host: HOST,
    port: PORT,
    compress: true,
    //inline: true,
    historyApiFallback: true,
    allowedHosts: "all",
    hot: true,
    client: {
      overlay: {
        warnings: false,
        errors: true
      }
    },
    open: true,
    https: PROTOCOL === "https",
    headers: {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, PATCH, OPTIONS",
      "Access-Control-Allow-Headers": "X-Requested-With, content-type, Authorization",
    },
  },
  plugins: [
    new CopyWebpackPlugin({
      patterns: [
        {from: "./src/version.js"},
        {from: "./src/config.js"},
        {from: "./src/favicon.ico"},
      ]})
  ]
});
