import React from 'react';
import { useTranslation } from 'react-i18next';
import useAppParams from 'lib/hooks/useAppParams';
import { ClusterBrokerParam } from 'lib/paths';
import { useBrokerLogDirs } from 'lib/hooks/api/brokers';
import Table from 'components/common/NewTable';
import { ColumnDef } from '@tanstack/react-table';
import { BrokersLogdirs } from 'generated-sources';

const BrokerLogdir: React.FC = () => {
  // 引入 i18n 翻译函数
  const { t } = useTranslation();
  const { clusterName, brokerId } = useAppParams<ClusterBrokerParam>();
  const { data } = useBrokerLogDirs(clusterName, Number(brokerId));

  const columns = React.useMemo<ColumnDef<BrokersLogdirs>[]>(
    () => [
      { header: t('common.name'), accessorKey: 'name' },
      { header: t('common.error'), accessorKey: 'error' },
      {
        header: t('common.topics'),
        accessorKey: 'topics',
        cell: ({ getValue }) =>
          getValue<BrokersLogdirs['topics']>()?.length || 0,
        enableSorting: false,
      },
      {
        id: 'partitions',
        header: t('common.partitions'),
        accessorKey: 'topics',
        cell: ({ getValue }) => {
          const topics = getValue<BrokersLogdirs['topics']>();
          if (!topics) {
            return 0;
          }
          return topics.reduce(
            (acc, topic) => acc + (topic.partitions?.length || 0),
            0
          );
        },
        enableSorting: false,
      },
    ],
    [t]
  );

  return (
    <Table
      data={data || []}
      columns={columns}
      emptyMessage={t('broker.logDirNotAvailable')}
      enableSorting
    />
  );
};

export default BrokerLogdir;
