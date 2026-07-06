import React from 'react';
import { useTranslation } from 'react-i18next';
import Tooltip from 'components/common/Tooltip/Tooltip';
import InfoIcon from 'components/common/Icons/InfoIcon';

import * as S from './SkewHeader.styled';

const SkewHeader: React.FC = () => {
  const { t } = useTranslation();
  return (
    <S.CellWrapper>
      {t('topic.partitionsSkew')}
      <Tooltip
        value={<InfoIcon />}
        content={t('topic.partitionsSkewTooltip')}
      />
    </S.CellWrapper>
  );
};

export default SkewHeader;
