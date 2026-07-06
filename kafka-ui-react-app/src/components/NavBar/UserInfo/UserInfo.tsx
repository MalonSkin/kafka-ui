import React from 'react';
import { useTranslation } from 'react-i18next';
import { Dropdown, DropdownItem } from 'components/common/Dropdown';
import UserIcon from 'components/common/Icons/UserIcon';
import DropdownArrowIcon from 'components/common/Icons/DropdownArrowIcon';
import { useUserInfo } from 'lib/hooks/useUserInfo';

import * as S from './UserInfo.styled';

const UserInfo = () => {
  const { t } = useTranslation();
  const { username } = useUserInfo();

  return username ? (
    <Dropdown
      label={
        <S.Wrapper>
          <UserIcon />
          <S.Text>{username}</S.Text>
          <DropdownArrowIcon isOpen={false} />
        </S.Wrapper>
      }
    >
      <DropdownItem href={`${window.basePath}/logout`}>
        <S.LogoutLink>{t('navBar.logOut')}</S.LogoutLink>
      </DropdownItem>
    </Dropdown>
  ) : null;
};

export default UserInfo;
