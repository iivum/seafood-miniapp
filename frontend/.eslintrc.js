// 海鲜商城小程序前端 - ESLint 配置
// root: true 阻止向上查找父目录配置

module.exports = {
  root: true,

  env: {
    browser: true,
    es2020: true,
    node: true,
    jest: true,
  },

  globals: {
    wx: 'readonly',
    my: 'readonly',
    getApp: 'readonly',
    getCurrentPages: 'readonly',
    Page: 'readonly',
    Component: 'readonly',
    App: 'readonly',
  },

  // 默认配置（针对 JS 文件）
  extends: [
    'eslint:recommended',
  ],

  rules: {
    'no-eval': 'error',
    'eqeqeq': ['error', 'always', { null: 'ignore' }],
  },

  overrides: [
    // TypeScript 文件：使用 TypeScript 解析器和规则
    {
      files: ['**/*.ts', '**/*.tsx'],
      parser: '@typescript-eslint/parser',
      parserOptions: {
        ecmaVersion: 2020,
        sourceType: 'module',
        project: './tsconfig.json',
        tsconfigRootDir: __dirname,
      },
      plugins: ['@typescript-eslint'],
      extends: [
        'eslint:recommended',
        'plugin:@typescript-eslint/recommended',
      ],
      rules: {
        '@typescript-eslint/no-explicit-any': 'error',
        '@typescript-eslint/no-unused-vars': [
          'error',
          {
            argsIgnorePattern: '^_',
            varsIgnorePattern: '^_',
          },
        ],
      },
    },
    // 测试文件：放宽 any 类型限制
    {
      files: ['**/*.test.ts', '**/*.spec.ts'],
      parser: '@typescript-eslint/parser',
      parserOptions: {
        ecmaVersion: 2020,
        sourceType: 'module',
        project: './tsconfig.json',
        tsconfigRootDir: __dirname,
      },
      plugins: ['@typescript-eslint'],
      rules: {
        '@typescript-eslint/no-explicit-any': 'off',
        '@typescript-eslint/no-non-null-assertion': 'off',
      },
    },
    // JS 配置文件：使用 CommonJS
    {
      files: ['**/*.config.js', '.eslintrc.js', 'jest.setup.js'],
      env: {
        node: true,
        commonjs: true,
      },
      rules: {
        'no-undef': 'off',
      },
    },
  ],

  ignorePatterns: [
    'node_modules/',
    'coverage/',
    'dist/',
    'pages/**/*.js',
    'pages-sub/**/*.js',
    'src/api/*.test.runner.js',
    'src/modules/payment/payment.js',
  ],
};
