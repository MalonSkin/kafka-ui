import React, { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import PageHeading from 'components/common/PageHeading/PageHeading';
import * as Metrics from 'components/common/Metrics';
import { Tag } from 'components/common/Tag/Tag.styled';
import Switch from 'components/common/Switch/Switch';
import { useClusters } from 'lib/hooks/api/clusters';
import { Cluster, ResourceType, ServerStatus } from 'generated-sources';
import { ColumnDef, Row } from '@tanstack/react-table';
import Table, { SizeCell } from 'components/common/NewTable';
import useBoolean from 'lib/hooks/useBoolean';
import { clusterNewConfigPath } from 'lib/paths';
import { GlobalSettingsContext } from 'components/contexts/GlobalSettingsContext';
import { ActionCanButton } from 'components/common/ActionComponent';
import { useGetUserInfo } from 'lib/hooks/api/roles';
import RefreshIcon from '@mui/icons-material/Refresh'; // 导入 MUI 的刷新图标
import IconButton from '@mui/material/IconButton'; // 导入 IconButton

import * as S from './Dashboard.styled';
import ClusterName from './ClusterName';
import ClusterTableActionsCell from './ClusterTableActionsCell';

const Dashboard: React.FC = () => {
  const { t } = useTranslation();
  const { data } = useGetUserInfo();
  const clusters = useClusters();
  const { value: showOfflineOnly, toggle } = useBoolean(false);
  const appInfo = React.useContext(GlobalSettingsContext);

  const config = React.useMemo(() => {
    const clusterList = clusters.data || [];
    const offlineClusters = clusterList.filter(
      ({ status }) => status === ServerStatus.OFFLINE,
    );
    return {
      list: showOfflineOnly ? offlineClusters : clusterList,
      online: clusterList.length - offlineClusters.length,
      offline: offlineClusters.length,
    };
  }, [clusters, showOfflineOnly]);

  // 刷新集群列表的函数
  const refreshClusters = () => {
    clusters.refetch(); // 使用 refetch 方法重新获取集群数据
  };

  const renderActionsCell = ({ row }: { row: Row<Cluster> }) => (
    <ClusterTableActionsCell row={row} refreshClusters={refreshClusters} />
  );

  const columns = React.useMemo<ColumnDef<Cluster>[]>(() => {
    // 表头使用 i18n 翻译
    const initialColumns: ColumnDef<Cluster>[] = [
      { header: t('common.clusterName'), accessorKey: 'name', cell: ClusterName },
      { header: t('common.version'), accessorKey: 'version' },
      { header: t('common.brokersCount'), accessorKey: 'brokerCount' },
      { header: t('common.partitions'), accessorKey: 'onlinePartitionCount' },
      { header: t('common.topics'), accessorKey: 'topicCount' },
      { header: t('common.production'), accessorKey: 'bytesInPerSec', cell: SizeCell },
      { header: t('common.consumption'), accessorKey: 'bytesOutPerSec', cell: SizeCell },
    ];

    if (appInfo.hasDynamicConfig) {
      initialColumns.push({
        header: '',
        id: 'actions',
        cell: renderActionsCell
      });
    }

    return initialColumns;
  }, [t]);

  const hasPermissions = useMemo(() => {
    if (!data?.rbacEnabled) return true;
    return !!data?.userInfo?.permissions.some(
      (permission) => permission.resource === ResourceType.APPLICATIONCONFIG,
    );
  }, [data]);
  return (
    <>
      <PageHeading text={t('common.dashboard')} />
      <Metrics.Wrapper>
        <Metrics.Section>
          <Metrics.Indicator label={<Tag color="green">{t('common.online')}</Tag>}>
            <span>{config.online || 0}</span>{' '}
            <Metrics.LightText>{t('common.clusters')}</Metrics.LightText>
          </Metrics.Indicator>
          <Metrics.Indicator label={<Tag color="gray">{t('common.offline')}</Tag>}>
            <span>{config.offline || 0}</span>{' '}
            <Metrics.LightText>{t('common.clusters')}</Metrics.LightText>
          </Metrics.Indicator>
        </Metrics.Section>
      </Metrics.Wrapper>
      <S.Toolbar>
        <div>
          <Switch
            name="switchRoundedDefault"
            checked={showOfflineOnly}
            onChange={toggle}
          />
          <label>{t('common.onlyOfflineClusters')}</label>
          <IconButton onClick={refreshClusters} color="primary">
            <RefreshIcon />
          </IconButton>
        </div>
        {appInfo.hasDynamicConfig && (
          <ActionCanButton
            buttonType="primary"
            buttonSize="M"
            to={clusterNewConfigPath}
            canDoAction={hasPermissions}
          >
            {t('common.configureNewCluster')}
          </ActionCanButton>
        )}
      </S.Toolbar>
      <Table
        columns={columns}
        data={config?.list}
        enableSorting
        emptyMessage={clusters.isFetched ? t('common.noClustersFound') : t('common.loading')}
      />
    </>
  );
};

export default Dashboard;
