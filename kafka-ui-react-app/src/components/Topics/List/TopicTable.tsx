import React from 'react';
import { useTranslation } from 'react-i18next';
import { SortOrder, Topic, TopicColumnsToSort } from 'generated-sources';
import { ColumnDef } from '@tanstack/react-table';
import Table, { SizeCell } from 'components/common/NewTable';
import useAppParams from 'lib/hooks/useAppParams';
import { ClusterName } from 'redux/interfaces';
import { useSearchParams } from 'react-router-dom';
import ClusterContext from 'components/contexts/ClusterContext';
import { useTopics } from 'lib/hooks/api/topics';
import { PER_PAGE } from 'lib/constants';

import { TopicTitleCell } from './TopicTitleCell';
import ActionsCell from './ActionsCell';
import BatchActionsbar from './BatchActionsBar';

const TopicTable: React.FC = () => {
  // 引入 i18n 翻译函数
  const { t } = useTranslation();
  const { clusterName } = useAppParams<{ clusterName: ClusterName }>();
  const [searchParams] = useSearchParams();
  const { isReadOnly } = React.useContext(ClusterContext);
  const { data } = useTopics({
    clusterName,
    page: Number(searchParams.get('page') || 1),
    perPage: Number(searchParams.get('perPage') || PER_PAGE),
    search: searchParams.get('q') || undefined,
    showInternal: !searchParams.has('hideInternal'),
    orderBy: (searchParams.get('sortBy') as TopicColumnsToSort) || undefined,
    sortOrder:
      (searchParams.get('sortDirection')?.toUpperCase() as SortOrder) ||
      undefined,
  });

  const topics = data?.topics || [];
  const pageCount = data?.pageCount || 0;

  const columns = React.useMemo<ColumnDef<Topic>[]>(
    () => [
      {
        id: TopicColumnsToSort.NAME,
        header: t('topic.topicName'),
        accessorKey: 'name',
        cell: TopicTitleCell,
      },
      {
        id: TopicColumnsToSort.TOTAL_PARTITIONS,
        header: t('topic.partitions'),
        accessorKey: 'partitionCount',
      },
      {
        id: TopicColumnsToSort.OUT_OF_SYNC_REPLICAS,
        header: t('topic.outOfSyncReplicas'),
        accessorKey: 'partitions',
        cell: ({ getValue }) => {
          const partitions = getValue<Topic['partitions']>();
          if (partitions === undefined || partitions.length === 0) {
            return 0;
          }
          return partitions.reduce((memo, { replicas }) => {
            const outOfSync = replicas?.filter(({ inSync }) => !inSync);
            return memo + (outOfSync?.length || 0);
          }, 0);
        },
      },
      {
        header: t('topic.replicationFactor'),
        accessorKey: 'replicationFactor',
        enableSorting: false,
      },
      {
        header: t('topic.totalMessage'),
        accessorKey: 'partitions',
        enableSorting: false,
        cell: ({ getValue }) => {
          const partitions = getValue<Topic['partitions']>();
          if (partitions === undefined || partitions.length === 0) {
            return 0;
          }
          return partitions.reduce((memo, { offsetMax, offsetMin }) => {
            return memo + (offsetMax - offsetMin);
          }, 0);
        },
      },
      {
        id: TopicColumnsToSort.SIZE,
        header: t('topic.totalSize'),
        accessorKey: 'segmentSize',
        cell: SizeCell,
      },
      {
        id: 'actions',
        header: '',
        cell: ActionsCell,
      },
    ],
    [t]
  );

  return (
    <Table
      data={topics}
      pageCount={pageCount}
      columns={columns}
      enableSorting
      serverSideProcessing
      batchActionsBar={BatchActionsbar}
      enableRowSelection={
        !isReadOnly ? (row) => !row.original.internal : undefined
      }
      emptyMessage={t('topic.noTopicsFound')}
    />
  );
};

export default TopicTable;
