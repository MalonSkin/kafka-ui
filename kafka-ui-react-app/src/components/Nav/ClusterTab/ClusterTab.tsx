import React from 'react';
import { useTranslation } from 'react-i18next';
import { ServerStatus } from 'generated-sources';

import * as S from './ClusterTab.styled';

export interface ClusterTabProps {
  title?: string;
  status: ServerStatus;
  isOpen: boolean;
  toggleClusterMenu: () => void;
}

const ClusterTab: React.FC<ClusterTabProps> = ({
  status,
  title,
  isOpen,
  toggleClusterMenu,
}) => {
  const { t } = useTranslation();
  return (
    <S.Wrapper onClick={toggleClusterMenu} isOpen>
      <S.Title title={title}>{title}</S.Title>

      <S.StatusIconWrapper>
        <S.StatusIcon status={status} aria-label={t('common.status')}>
          <title>{t(`common.${status}`)}</title>
        </S.StatusIcon>
      </S.StatusIconWrapper>

      <S.ChevronWrapper>
        <S.ChevronIcon $open={isOpen} />
      </S.ChevronWrapper>
    </S.Wrapper>
  );
};

export default ClusterTab;
