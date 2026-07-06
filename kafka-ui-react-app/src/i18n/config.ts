/**
 * i18n 国际化配置文件
 * 使用 i18next + react-i18next 实现多语言支持
 * 默认语言设置为简体中文 (zh-CN)
 */

import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';

// 导入语言文件
import zhCN from './locales/zh-CN.json';
import en from './locales/en.json';

// 配置 i18n
i18n
  // 检测浏览器语言
  .use(LanguageDetector)
  // 集成 React
  .use(initReactI18next)
  // 初始化配置
  .init({
    // 语言资源
    resources: {
      'zh-CN': {
        translation: zhCN,
      },
      en: {
        translation: en,
      },
    },
    // 默认语言
    fallbackLng: 'zh-CN',
    // 默认命名空间
    defaultNS: 'translation',
    // 调试模式 (开发环境开启)
    debug: process.env.NODE_ENV === 'development',
    // 插值配置
    interpolation: {
      // 是否转义 HTML
      escapeValue: false,
    },
    // 支持的语言
    supportedLngs: ['zh-CN', 'en'],
    // 语言检测顺序
    // 移除 navigator：避免浏览器英文环境覆盖默认中文
    // 首次访问走 htmlTag (<html lang="zh-CN">)，用户切换后走 localStorage 持久化
    detection: {
      order: ['localStorage', 'htmlTag'],
      // 存储到 localStorage 的 key 名
      lookupLocalStorage: 'i18nextLng',
      // 缓存语言到 localStorage
      caches: ['localStorage'],
    },
  });

export default i18n;
