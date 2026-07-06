import React from 'react';
import { useTranslation } from 'react-i18next';
import * as Metrics from 'components/common/Metrics';
import { TopicAnalysisSizeStats } from 'generated-sources';
import BytesFormatted from 'components/common/BytesFormatted/BytesFormatted';

/**
 * Size 尺寸统计组件
 *
 * 显示 Topic 分析结果中 Key/Value 的尺寸统计信息，
 * 包括总大小、最小/最大/平均大小及各百分位数据。
 */
const SizeStats: React.FC<{
  stats: TopicAnalysisSizeStats;
  title: string;
}> = ({
  stats: { sum, min, max, avg, prctl50, prctl75, prctl95, prctl99, prctl999 },
  title,
}) => {
  // 引入 i18n 翻译函数，用于标签国际化
  const { t } = useTranslation();
  return (
    <Metrics.Section title={title}>
      <Metrics.Indicator label={t('topic.totalSize')}>
        <BytesFormatted value={sum} />
      </Metrics.Indicator>
      <Metrics.Indicator label={t('topic.minSize')}>
        <BytesFormatted value={min} />
      </Metrics.Indicator>
      <Metrics.Indicator label={t('topic.maxSize')}>
        <BytesFormatted value={max} />
      </Metrics.Indicator>
      <Metrics.Indicator label={t('topic.avgKey')}>
        <BytesFormatted value={avg} />
      </Metrics.Indicator>
      <Metrics.Indicator label={t('topic.percentile50')}>
        <BytesFormatted value={prctl50} />
      </Metrics.Indicator>
      <Metrics.Indicator label={t('topic.percentile75')}>
        <BytesFormatted value={prctl75} />
      </Metrics.Indicator>
      <Metrics.Indicator label={t('topic.percentile95')}>
        <BytesFormatted value={prctl95} />
      </Metrics.Indicator>
      <Metrics.Indicator label={t('topic.percentile99')}>
        <BytesFormatted value={prctl99} />
      </Metrics.Indicator>
      <Metrics.Indicator label={t('topic.percentile999')}>
        <BytesFormatted value={prctl999} />
      </Metrics.Indicator>
    </Metrics.Section>
  );
};

export default SizeStats;
