import React from 'react';
import { useTranslation } from 'react-i18next';
import * as Metrics from 'components/common/Metrics';
import { TopicAnalysisStats } from 'generated-sources';
import { formatTimestamp } from 'lib/dateTimeHelpers';

const Total: React.FC<TopicAnalysisStats> = ({
  totalMsgs,
  minOffset,
  maxOffset,
  minTimestamp,
  maxTimestamp,
  nullKeys,
  nullValues,
  approxUniqKeys,
  approxUniqValues,
}) => {
  // 获取 i18n 翻译函数
  const { t } = useTranslation();
  return (
    <Metrics.Section title={t('topic.messages')}>
      <Metrics.Indicator label={t('topic.totalNumberLabel')}>{totalMsgs}</Metrics.Indicator>
      <Metrics.Indicator label={t('topic.offsetsMinMax')}>
        {`${minOffset} - ${maxOffset}`}
      </Metrics.Indicator>
      <Metrics.Indicator label={t('topic.timestampMinMax')}>
        {`${formatTimestamp(minTimestamp)} - ${formatTimestamp(maxTimestamp)}`}
      </Metrics.Indicator>
      <Metrics.Indicator label={t('topic.nullKeysLabel')}>{nullKeys}</Metrics.Indicator>
      <Metrics.Indicator
        label={t('topic.uniqueKeysLabel')}
        title={t('topic.approxUniqKeys')}
      >
        {approxUniqKeys}
      </Metrics.Indicator>
      <Metrics.Indicator label={t('topic.nullValuesLabel')}>{nullValues}</Metrics.Indicator>
      <Metrics.Indicator
        label={t('topic.uniqueValuesLabel')}
        title={t('topic.approxUniqValues')}
      >
        {approxUniqValues}
      </Metrics.Indicator>
    </Metrics.Section>
  );
};

export default Total;
