import React, { useContext } from 'react';
import { useTranslation } from 'react-i18next';
import Select from 'components/common/Select/Select';
import Logo from 'components/common/Logo/Logo';
import Version from 'components/Version/Version';
import GitIcon from 'components/common/Icons/GitIcon';
import DiscordIcon from 'components/common/Icons/DiscordIcon';
import AutoIcon from 'components/common/Icons/AutoIcon';
import SunIcon from 'components/common/Icons/SunIcon';
import MoonIcon from 'components/common/Icons/MoonIcon';
import { ThemeModeContext } from 'components/contexts/ThemeModeContext';

import UserInfo from './UserInfo/UserInfo';
// 语言切换组件
import LanguageSwitcher from './LanguageSwitcher/LanguageSwitcher';
import * as S from './NavBar.styled';

interface Props {
  onBurgerClick: () => void;
}

export type ThemeDropDownValue = 'auto_theme' | 'light_theme' | 'dark_theme';

const NavBar: React.FC<Props> = ({ onBurgerClick }) => {
  const { t } = useTranslation();
  const { themeMode, setThemeMode } = useContext(ThemeModeContext);

  // 主题切换下拉选项，使用 i18n 翻译标签
  const options = [
    {
      label: (
        <>
          <AutoIcon />
          <div>{t('navBar.autoTheme')}</div>
        </>
      ),
      value: 'auto_theme',
    },
    {
      label: (
        <>
          <SunIcon />
          <div>{t('navBar.lightTheme')}</div>
        </>
      ),
      value: 'light_theme',
    },
    {
      label: (
        <>
          <MoonIcon />
          <div>{t('navBar.darkTheme')}</div>
        </>
      ),
      value: 'dark_theme',
    },
  ];

  return (
    <S.Navbar role="navigation" aria-label={t('navBar.pageHeader')}>
      <S.NavbarBrand>
        <S.NavbarBrand>
          <S.NavbarBurger
            onClick={onBurgerClick}
            onKeyDown={onBurgerClick}
            role="button"
            tabIndex={0}
            aria-label={t('navBar.burgerMenu')} // 汉化无障碍标签
          >
            <S.Span role="separator" />
            <S.Span role="separator" />
            <S.Span role="separator" />
          </S.NavbarBurger>

          <S.Hyperlink to="/">
            <Logo />
            {t('navBar.appName')}
          </S.Hyperlink>

          <S.NavbarItem>
            <Version />
          </S.NavbarItem>
        </S.NavbarBrand>
      </S.NavbarBrand>
      <S.NavbarSocial>
        <Select
          options={options}
          value={themeMode}
          onChange={setThemeMode}
          isThemeMode
        />
        {/* 语言切换组件 */}
        <LanguageSwitcher />
        <S.SocialLink
          href="https://github.com/MalonSkin/kafka-ui"
          target="_blank"
        >
          <GitIcon />
        </S.SocialLink>
        {/* <S.SocialLink */}
        {/*  href="https://discord.com/invite/4DWzD7pGE5" */}
        {/*  target="_blank" */}
        {/* > */}
        {/*  <DiscordIcon /> */}
        {/* </S.SocialLink> */}
        <UserInfo />
      </S.NavbarSocial>
    </S.Navbar>
  );
};

export default NavBar;