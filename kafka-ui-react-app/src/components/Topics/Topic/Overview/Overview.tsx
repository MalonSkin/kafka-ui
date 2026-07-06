import React from 'react';
import { useTranslation } from 'react-i18next';
import type { Partition, Replica } from 'generated-sources';
import BytesFormatted from 'components/common/BytesFormatted/BytesFormatted';
import Table from 'components/common/NewTable';
import * as Metrics from 'components/common/Metrics';
import { Tag } from 'components/common/Tag/Tag.styled';
import { RouteParamsClusterTopic } from 'lib/paths';
import useAppParams from 'lib/hooks/useAppParams';
import { useTopicDetails } from 'lib/hooks/api/topics';
import { ColumnDef } from '@tanstack/react-table';

import * as S from './Overview.styled';
import ActionsCell from './ActionsCell';

const Overview: React.FC = () => {
  const { t } = useTranslation();
  const { clusterName, topicName } = useAppParams<RouteParamsClusterTopic>();
  const { data } = useTopicDetails({ clusterName, topicName });

  const messageCount = React.useMemo(
    () =>
      (data?.partitions || []).reduce((memo, partition) => {
        return memo + partition.offsetMax - partition.offsetMin;
      }, 0),
    [data]
  );
  const newData = React.useMemo(() => {
    if (!data?.partitions) return [];

    return data.partitions.map((items: Partition) => {
      return {
        ...items,
        messageCount: items.offsetMax - items.offsetMin,
      };
    });
  }, [data?.partitions]);

  const columns = React.useMemo<ColumnDef<Partition>[]>(
    () => [
      {
        header: t('topic.partitionId'),
        enableSorting: false,
        accessorKey: 'partition',
      },
      {
        header: t('topic.replicas'),
        enableSorting: false,

        accessorKey: 'replicas',
        cell: ({ getValue }) => {
          const replicas = getValue<Partition['replicas']>();
          if (replicas === undefined || replicas.length === 0) {
            return 0;
          }
          return replicas?.map(({ broker, leader, inSync }: Replica) => (
            <S.Replica
              leader={leader}
              outOfSync={!inSync}
              key={broker}
              title={leader ? t('topic.leader') : ''}
            >
              {broker}
            </S.Replica>
          ));
        },
      },
      {
        header: t('topic.firstOffset'),
        enableSorting: false,
        accessorKey: 'offsetMin',
      },
      { header: t('topic.nextOffset'), enableSorting: false, accessorKey: 'offsetMax' },
      {
        header: t('topic.messageCount'),
        enableSorting: false,
        accessorKey: `messageCount`,
      },
      {
        header: '',
        enableSorting: false,
        accessorKey: 'actions',
        cell: ActionsCell,
      },
    ],
    [t]
  );
  return (
    <>
      <Metrics.Wrapper>
        <Metrics.Section>
          <Metrics.Indicator label={t('common.partitions')}>
            {data?.partitionCount}
          </Metrics.Indicator>
          <Metrics.Indicator label={t('topic.replicationFactor')}>
            {data?.replicationFactor}
          </Metrics.Indicator>
          <Metrics.Indicator
            label={t('broker.urp')}
            title={t('broker.underReplicatedPartitions')}
            isAlert
            alertType={
              data?.underReplicatedPartitions === 0 ? 'success' : 'error'
            }
          >
            {data?.underReplicatedPartitions === 0 ? (
              <Metrics.LightText>
                {data?.underReplicatedPartitions}
              </Metrics.LightText>
            ) : (
              <Metrics.RedText>
                {data?.underReplicatedPartitions}
              </Metrics.RedText>
            )}
          </Metrics.Indicator>
          <Metrics.Indicator
            label={t('broker.inSyncReplicas')}
            isAlert
            alertType={
              data?.inSyncReplicas === data?.replicas ? 'success' : 'error'
            }
          >
            {data?.inSyncReplicas &&
            data?.replicas &&
            data?.inSyncReplicas < data?.replicas ? (
              <Metrics.RedText>{data?.inSyncReplicas}</Metrics.RedText>
            ) : (
              data?.inSyncReplicas
            )}
            <Metrics.LightText>{t('topic.ofTotal', { current: data?.inSyncReplicas, total: data?.replicas })}</Metrics.LightText>
          </Metrics.Indicator>
          <Metrics.Indicator label={t('common.type')}>
            <Tag color="gray">{data?.internal ? t('topic.internal') : t('topic.external')}</Tag>
          </Metrics.Indicator>
          <Metrics.Indicator label={t('broker.segmentSize')} title="">
            <BytesFormatted value={data?.segmentSize} />
          </Metrics.Indicator>
          <Metrics.Indicator label={t('broker.segmentCount')}>
            {data?.segmentCount}
          </Metrics.Indicator>
          <Metrics.Indicator label={t('topic.cleanupPolicy')}>
            <Tag color="gray">{data?.cleanUpPolicy || t('common.unknown')}</Tag>
          </Metrics.Indicator>
          <Metrics.Indicator label={t('topic.messageCount')}>
            {messageCount}
          </Metrics.Indicator>
        </Metrics.Section>
      </Metrics.Wrapper>
      <Table
        columns={columns}
        data={newData}
        enableSorting
        emptyMessage={t('topic.noPartitionsFound')}
      />
    </>
  );
};

export default Overview;
