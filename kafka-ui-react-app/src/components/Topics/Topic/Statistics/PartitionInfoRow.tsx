import React from 'react';
import { Row } from '@tanstack/react-table';
import Heading from 'components/common/heading/Heading.styled';
import BytesFormatted from 'components/common/BytesFormatted/BytesFormatted';
import {
  List,
  Label,
} from 'components/common/PropertiesList/PropertiesList.styled';
import { TopicAnalysisStats } from 'generated-sources';
import { formatTimestamp } from 'lib/dateTimeHelpers';
import { useTranslation } from 'react-i18next';

import * as S from './Statistics.styles';

const PartitionInfoRow: React.FC<{ row: Row<TopicAnalysisStats> }> = ({
  row,
}) => {
  // 引入 i18n 翻译函数，用于替换硬编码英文标签
  const { t } = useTranslation();
  const {
    totalMsgs,
    minTimestamp,
    maxTimestamp,
    nullKeys,
    nullValues,
    approxUniqKeys,
    approxUniqValues,
    keySize,
    valueSize,
  } = row.original;
  return (
    <S.PartitionInfo>
      <div>
        <Heading level={4}>{t('topic.partitionStats')}</Heading>
        <List>
          <Label>{t('topic.totalMessage')}</Label>
          <span>{totalMsgs}</span>
          <Label>{t('topic.totalSize')}</Label>
          <BytesFormatted value={(keySize?.sum || 0) + (valueSize?.sum || 0)} />
          <Label>{t('topic.minTimestamp')}</Label>
          <span>{formatTimestamp(minTimestamp)}</span>
          <Label>{t('topic.maxTimestamp')}</Label>
          <span>{formatTimestamp(maxTimestamp)}</span>
          <Label>{t('topic.nullKeysLabel')}</Label>
          <span>{nullKeys}</span>
          <Label>{t('topic.nullValuesLabel')}</Label>
          <span>{nullValues}</span>
          <Label>{t('topic.approxUniqKeys')}</Label>
          <span>{approxUniqKeys}</span>
          <Label>{t('topic.approxUniqValues')}</Label>
          <span>{approxUniqValues}</span>
        </List>
      </div>
      <div>
        <Heading level={4}>{t('topic.keySizes')}</Heading>
        <List>
          <Label>{t('topic.totalKeysSize')}</Label>
          <BytesFormatted value={keySize?.sum} />
          <Label>{t('topic.minKeySize')}</Label>
          <BytesFormatted value={keySize?.min} />
          <Label>{t('topic.maxKeySize')}</Label>
          <BytesFormatted value={keySize?.max} />
          <Label>{t('topic.avgKeySize')}</Label>
          <BytesFormatted value={keySize?.avg} />
          <Label>{t('topic.percentile50')}</Label>
          <BytesFormatted value={keySize?.prctl50} />
          <Label>{t('topic.percentile75')}</Label>
          <BytesFormatted value={keySize?.prctl75} />
          <Label>{t('topic.percentile95')}</Label>
          <BytesFormatted value={keySize?.prctl95} />
          <Label>{t('topic.percentile99')}</Label>
          <BytesFormatted value={keySize?.prctl99} />
          <Label>{t('topic.percentile999')}</Label>
          <BytesFormatted value={keySize?.prctl999} />
        </List>
      </div>
      <div>
        <Heading level={4}>{t('topic.valueSizes')}</Heading>
        <List>
          <Label>{t('topic.totalKeysSize')}</Label>
          <BytesFormatted value={valueSize?.sum} />
          <Label>{t('topic.minKeySize')}</Label>
          <BytesFormatted value={valueSize?.min} />
          <Label>{t('topic.maxKeySize')}</Label>
          <BytesFormatted value={valueSize?.max} />
          <Label>{t('topic.avgKeySize')}</Label>
          <BytesFormatted value={valueSize?.avg} />
          <Label>{t('topic.percentile50')}</Label>
          <BytesFormatted value={valueSize?.prctl50} />
          <Label>{t('topic.percentile75')}</Label>
          <BytesFormatted value={valueSize?.prctl75} />
          <Label>{t('topic.percentile95')}</Label>
          <BytesFormatted value={valueSize?.prctl95} />
          <Label>{t('topic.percentile99')}</Label>
          <BytesFormatted value={valueSize?.prctl99} />
          <Label>{t('topic.percentile999')}</Label>
          <BytesFormatted value={valueSize?.prctl999} />
        </List>
      </div>
    </S.PartitionInfo>
  );
};

export default PartitionInfoRow;
