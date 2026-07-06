import React, { useEffect, useState } from 'react';
import {
  useAnalyzeTopic,
  useCancelTopicAnalysis,
  useTopicAnalysis,
} from 'lib/hooks/api/topics';
import useAppParams from 'lib/hooks/useAppParams';
import { RouteParamsClusterTopic } from 'lib/paths';
import * as Informers from 'components/common/Metrics';
import ProgressBar from 'components/common/ProgressBar/ProgressBar';
import {
  List,
  Label,
} from 'components/common/PropertiesList/PropertiesList.styled';
import BytesFormatted from 'components/common/BytesFormatted/BytesFormatted';
import { calculateTimer, formatTimestamp } from 'lib/dateTimeHelpers';
import { Action, ResourceType } from 'generated-sources';
import { ActionButton } from 'components/common/ActionComponent';
import { useTranslation } from 'react-i18next';

import * as S from './Statistics.styles';
import Total from './Indicators/Total';
import SizeStats from './Indicators/SizeStats';
import PartitionTable from './PartitionTable';

const Metrics: React.FC = () => {
  // 引入 i18n 翻译函数，用于替换硬编码英文标签
  const { t } = useTranslation();
  const params = useAppParams<RouteParamsClusterTopic>();

  const [isAnalyzing, setIsAnalyzing] = useState(true);
  const analyzeTopic = useAnalyzeTopic(params);
  const cancelTopicAnalysis = useCancelTopicAnalysis(params);

  const { data } = useTopicAnalysis(params, isAnalyzing);

  useEffect(() => {
    if (data && !data.progress) {
      setIsAnalyzing(false);
    }
  }, [data]);

  if (!data) {
    return null;
  }

  if (data.progress) {
    return (
      <S.ProgressContainer>
        <S.ProgressPct>
          {Math.floor(data.progress.completenessPercent || 0)}%
        </S.ProgressPct>
        <S.ProgressBarWrapper>
          <ProgressBar completed={data.progress.completenessPercent || 0} />
        </S.ProgressBarWrapper>
        <ActionButton
          onClick={async () => {
            await cancelTopicAnalysis.mutateAsync();
            setIsAnalyzing(true);
          }}
          buttonType="secondary"
          buttonSize="M"
          permission={{
            resource: ResourceType.TOPIC,
            action: Action.MESSAGES_READ,
            value: params.topicName,
          }}
        >
          {t('topic.stopAnalysis')}
        </ActionButton>
        <List>
          <Label>{t('topic.startedAt')}</Label>
          <span>
            {formatTimestamp(data.progress.startedAt, {
              hour: 'numeric',
              minute: 'numeric',
              second: 'numeric',
            })}
          </span>
          <Label>{t('topic.passedSinceStart')}</Label>
          <span>{calculateTimer(data.progress.startedAt as number)}</span>
          <Label>{t('topic.scannedMessages')}</Label>
          <span>{data.progress.msgsScanned}</span>
          <Label>{t('topic.scannedSize')}</Label>
          <span>
            <BytesFormatted value={data.progress.bytesScanned} />
          </span>
        </List>
      </S.ProgressContainer>
    );
  }

  if (!data.result) {
    return null;
  }

  const totalStats = data.result.totalStats || {};
  const partitionStats = data.result.partitionStats || [];

  return (
    <>
      <S.ActionsBar>
        <S.CreatedAt>{formatTimestamp(data?.result?.finishedAt)}</S.CreatedAt>
        <ActionButton
          onClick={async () => {
            await analyzeTopic.mutateAsync();
            setIsAnalyzing(true);
          }}
          buttonType="primary"
          buttonSize="S"
          permission={{
            resource: ResourceType.TOPIC,
            action: Action.MESSAGES_READ,
            value: params.topicName,
          }}
        >
          {t('topic.restartAnalysis')}
        </ActionButton>
      </S.ActionsBar>
      <Informers.Wrapper>
        <Total {...totalStats} />
        {totalStats.keySize && (
          <SizeStats stats={totalStats.keySize} title={t('topic.keySize')} />
        )}
        {totalStats.valueSize && (
          <SizeStats stats={totalStats.valueSize} title={t('topic.valueSize')} />
        )}
      </Informers.Wrapper>
      <PartitionTable data={partitionStats} />
    </>
  );
};

export default Metrics;
