import React, { PropsWithChildren } from 'react';
import Heading from 'components/common/heading/Heading.styled';
import { Button } from 'components/common/Button/Button';
import { useTranslation } from 'react-i18next';

import * as S from './SlidingSidebar.styled';

interface SlidingSidebarProps extends PropsWithChildren<unknown> {
  open?: boolean;
  title: string;
  onClose?: () => void;
}

const SlidingSidebar: React.FC<SlidingSidebarProps> = ({
  open,
  title,
  children,
  onClose,
}) => {
  const { t } = useTranslation();
  return (
    <S.Wrapper $open={open}>
      <Heading level={3}>
        <span>{title}</span>
        <Button buttonSize="M" buttonType="primary" onClick={onClose}>
          {t('common.close')}
        </Button>
      </Heading>
      <S.Content>{children}</S.Content>
    </S.Wrapper>
  );
};

export default SlidingSidebar;
