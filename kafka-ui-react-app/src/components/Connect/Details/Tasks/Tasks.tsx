import React from 'react';
import { useTranslation } from 'react-i18next';
import { useConnectorTasks } from 'lib/hooks/api/kafkaConnect';
import useAppParams from 'lib/hooks/useAppParams';
import { RouterParamsClusterConnectConnector } from 'lib/paths';
import { ColumnDef, Row } from '@tanstack/react-table';
import { Task } from 'generated-sources';
import Table, { TagCell } from 'components/common/NewTable';

import ActionsCellTasks from './ActionsCellTasks';

const ExpandedTaskRow: React.FC<{ row: Row<Task> }> = ({ row }) => {
  return <div>{row.original.status.trace}</div>;
};

const MAX_LENGTH = 100;

const Tasks: React.FC = () => {
  // 引入 i18n 翻译函数
  const { t } = useTranslation();
  const routerProps = useAppParams<RouterParamsClusterConnectConnector>();
  const { data = [] } = useConnectorTasks(routerProps);

  const columns = React.useMemo<ColumnDef<Task>[]>(
    () => [
      { header: t('common.id'), accessorKey: 'status.id' },
      { header: t('connector.worker'), accessorKey: 'status.workerId' },
      { header: t('common.state'), accessorKey: 'status.state', cell: TagCell },
      {
        header: t('connector.trace'),
        accessorKey: 'status.trace',
        enableSorting: false,
        cell: ({ getValue }) => {
          const trace = getValue<string>() || '';
          return trace.toString().length > MAX_LENGTH
            ? `${trace.toString().substring(0, MAX_LENGTH - 3)}...`
            : trace;
        },
        meta: { width: '70%' },
      },
      {
        id: 'actions',
        header: '',
        cell: ActionsCellTasks,
      },
    ],
    [t]
  );

  return (
    <Table
      columns={columns}
      data={data}
      emptyMessage={t('connector.noTasksFound')}
      enableSorting
      getRowCanExpand={(row) => row.original.status.trace?.length > 0}
      renderSubComponent={ExpandedTaskRow}
    />
  );
};

export default Tasks;
