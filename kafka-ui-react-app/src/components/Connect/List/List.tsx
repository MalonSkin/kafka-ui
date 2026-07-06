import React from 'react';
import { useTranslation } from 'react-i18next';
import useAppParams from 'lib/hooks/useAppParams';
import { clusterConnectConnectorPath, ClusterNameRoute } from 'lib/paths';
import Table, { TagCell } from 'components/common/NewTable';
import { FullConnectorInfo } from 'generated-sources';
import { useConnectors } from 'lib/hooks/api/kafkaConnect';
import { ColumnDef } from '@tanstack/react-table';
import { useNavigate, useSearchParams } from 'react-router-dom';

import ActionsCell from './ActionsCell';
import TopicsCell from './TopicsCell';
import RunningTasksCell from './RunningTasksCell';

const List: React.FC = () => {
  // 引入 i18n 翻译函数
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { clusterName } = useAppParams<ClusterNameRoute>();
  const [searchParams] = useSearchParams();
  const { data: connectors } = useConnectors(
    clusterName,
    searchParams.get('q') || ''
  );

  const columns = React.useMemo<ColumnDef<FullConnectorInfo>[]>(
    () => [
      { header: t('connector.name'), accessorKey: 'name' },
      { header: t('common.kafkaConnect'), accessorKey: 'connect' },
      { header: t('common.type'), accessorKey: 'type' },
      { header: t('common.plugin'), accessorKey: 'connectorClass' },
      { header: t('common.topics'), cell: TopicsCell },
      { header: t('common.status'), accessorKey: 'status.state', cell: TagCell },
      { header: t('connector.tasksRunning'), cell: RunningTasksCell },
      { header: '', id: 'action', cell: ActionsCell },
    ],
    [t]
  );

  return (
    <Table
      data={connectors || []}
      columns={columns}
      enableSorting
      onRowClick={({ original: { connect, name } }) =>
        navigate(clusterConnectConnectorPath(clusterName, connect, name))
      }
      emptyMessage={t('connector.noConnectorsFound')}
    />
  );
};

export default List;
