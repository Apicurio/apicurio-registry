const path = require("path");
const { ConsoleRemotePlugin } = require("@openshift-console/dynamic-plugin-sdk-webpack");

module.exports = {
  mode: "production",
  context: path.resolve(__dirname, "src"),
  entry: {},
  output: {
    path: path.resolve(__dirname, "dist"),
    filename: "[name]-bundle.js",
    chunkFilename: "[name]-chunk.js",
  },
  resolve: {
    extensions: [".ts", ".tsx", ".js", ".jsx"],
  },
  module: {
    rules: [
      {
        test: /\.(ts|tsx)$/,
        exclude: /node_modules/,
        use: [
          {
            loader: "swc-loader",
            options: {
              jsc: {
                parser: {
                  syntax: "typescript",
                  tsx: true,
                },
                transform: {
                  react: {
                    runtime: "automatic",
                  },
                },
              },
            },
          },
        ],
      },
      {
        test: /\.css$/,
        use: ["style-loader", "css-loader"],
      },
    ],
  },
  plugins: [new ConsoleRemotePlugin()],
  devtool: "source-map",
  devServer: {
    port: 9001,
    headers: {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, PATCH, OPTIONS",
      "Access-Control-Allow-Headers":
        "X-Requested-With, Content-Type, Authorization",
    },
  },
};
