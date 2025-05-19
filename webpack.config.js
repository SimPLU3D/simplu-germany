const path = require('path');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const MiniCssExtractPlugin = require("mini-css-extract-plugin");
const CopyWebpackPlugin = require('copy-webpack-plugin');
module.exports = {
    entry: './src/main/js/main.js',
    mode: 'production',
    plugins: [
        new HtmlWebpackPlugin({
          title: 'Simplu Germany results with iTowns',
        }),
        new MiniCssExtractPlugin(),
        new CopyWebpackPlugin({
            patterns: [
                { from: 'output/buildings',to: 'buildings' }
            ]
        })
      ],
    output: {
        path: path.resolve(__dirname, "dist"),
        filename: 'bundle.js',
        clean: true,
    },
    module: {
        rules: [
          {
            test: /\.css$/i,
            use: [MiniCssExtractPlugin.loader, "css-loader"],
          },
        ],
      },    
};