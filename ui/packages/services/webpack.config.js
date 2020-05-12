const path = require('path');

module.exports = {
  entry: {
    'index': './src/index.ts'
  },
  devtool: 'source-map',
  module: {
    rules: [
      {
        test: /\.ts?$/,
        use: 'ts-loader',
        exclude: /node_modules/
      }
    ]
  },
  resolve: {
    extensions: [ '.ts', '.js' ]
  },
  output: {
    filename: '[name].js',
    path: path.resolve(__dirname, 'dist/umd'),
    libraryTarget: 'umd',
    library: 'apicurio-registry-services',
    umdNamedDefine: true
  }
};