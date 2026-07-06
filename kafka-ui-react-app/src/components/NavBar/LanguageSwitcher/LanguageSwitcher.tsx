import React from 'react';
import { useTranslation } from 'react-i18next';
import { SelectChangeEvent } from '@mui/material/Select';
import {
  FormControl,
  MenuItem,
  Select,
} from '@mui/material';
import LanguageIcon from '@mui/icons-material/Language';

import * as S from './LanguageSwitcher.styled';

/**
 * 语言切换组件
 *
 * 在 NavBar 中提供语言切换下拉框，支持简体中文和英文。
 * 语言切换后通过 i18n.changeLanguage() 立即生效，
 * 并由 i18next-browser-languagedetector 持久化到 localStorage。
 */
const LanguageSwitcher: React.FC = () => {
  const { i18n, t } = useTranslation();

  // 处理语言切换事件
  const handleChange = (event: SelectChangeEvent<string>) => {
    i18n.changeLanguage(event.target.value);
  };

  return (
    <S.Wrapper>
      <LanguageIcon fontSize="small" />
      <FormControl variant="standard" size="small">
        <Select
          value={i18n.language}
          onChange={handleChange}
          disableUnderline
          aria-label={t('navBar.language')}
        >
          <MenuItem value="zh-CN">简体中文</MenuItem>
          <MenuItem value="en">English</MenuItem>
        </Select>
      </FormControl>
    </S.Wrapper>
  );
};

export default LanguageSwitcher;
