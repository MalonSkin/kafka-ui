import React from 'react';
import { useTranslation } from 'react-i18next';
import { TopicAnalysisStats } from 'generated-sources';
import { ColumnDef } from '@tanstack/react-table';
import Table from 'components/common/NewTable';

import PartitionInfoRow from './PartitionInfoRow';

const PartitionTable: React.FC<{ data: TopicAnalysisStats[] }> = ({ data }) => {
  // 引入 i18n 翻译函数
  const { t } = useTranslation();
  const columns = React.useMemo<ColumnDef<TopicAnalysisStats>[]>(
    () => [
      {
        header: t('topic.partitionId'),
        accessorKey: 'partition',
      },
      {
        header: t('topic.totalMessage'),
        accessorKey: 'totalMsgs',
      },
      {
        header: t('topic.minOffset'),
        accessorKey: 'minOffset',
      },
      { header: t('topic.maxOffset'), accessorKey: 'maxOffset' },
    ],
    [t]
  );

  return (
    <Table
      data={data}
      columns={columns}
      getRowCanExpand={() => true}
      renderSubComponent={PartitionInfoRow}
      enableSorting
    />
  );
};

export default PartitionTable;
