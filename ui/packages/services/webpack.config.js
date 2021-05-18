const path = require('path');

module.exports = {
  mode: 'production',
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
      },
      // fixes issue with yaml dependency not declaring the package correctly for webpack 5
      {
        test: /node_modules\/yaml\/browser\/dist\/.*/,
        type: 'javascript/auto'
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