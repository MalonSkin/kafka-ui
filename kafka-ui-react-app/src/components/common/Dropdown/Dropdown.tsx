import { MenuProps } from '@szhsin/react-menu';
import React, { PropsWithChildren, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import VerticalElipsisIcon from 'components/common/Icons/VerticalElipsisIcon';
import useBoolean from 'lib/hooks/useBoolean';

import * as S from './Dropdown.styled';

interface DropdownProps extends PropsWithChildren<Partial<MenuProps>> {
  label?: React.ReactNode;
  disabled?: boolean;
}

const Dropdown: React.FC<DropdownProps> = ({ label, disabled, children }) => {
  // i18n: 获取国际化翻译函数
  const { t } = useTranslation();
  const ref = useRef(null);
  const { value: isOpen, setFalse, setTrue } = useBoolean(false);

  const handleClick: React.MouseEventHandler<HTMLButtonElement> = (e) => {
    e.preventDefault();
    e.stopPropagation();
    setTrue();
  };

  return (
    <S.Wrapper>
      <S.DropdownButton
        onClick={handleClick}
        ref={ref}
        aria-label={t('common.dropdownToggle')}
        disabled={disabled}
      >
        {label || <VerticalElipsisIcon />}
      </S.DropdownButton>
      <S.Dropdown
        anchorRef={ref}
        state={isOpen ? 'open' : 'closed'}
        onMouseLeave={setFalse}
        onClose={setFalse}
        align="end"
        direction="bottom"
        offsetY={10}
        viewScroll="auto"
      >
        {children}
      </S.Dropdown>
    </S.Wrapper>
  );
};

export default Dropdown;
