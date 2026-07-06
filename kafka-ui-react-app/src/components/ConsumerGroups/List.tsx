import React from 'react';
import { useTranslation } from 'react-i18next';
import PageHeading from 'components/common/PageHeading/PageHeading';
import Search from 'components/common/Search/Search';
import { ControlPanelWrapper } from 'components/common/ControlPanel/ControlPanel.styled';
import {
  ConsumerGroupDetails,
  ConsumerGroupOrdering,
  ConsumerGroupState,
  SortOrder,
} from 'generated-sources';
import useAppParams from 'lib/hooks/useAppParams';
import { clusterConsumerGroupDetailsPath, ClusterNameRoute } from 'lib/paths';
import { ColumnDef } from '@tanstack/react-table';
import Table, { LinkCell, TagCell } from 'components/common/NewTable';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { CONSUMER_GROUP_STATE_TOOLTIPS, PER_PAGE } from 'lib/constants';
import { useConsumerGroups } from 'lib/hooks/api/consumers';
import Tooltip from 'components/common/Tooltip/Tooltip';

const List = () => {
  const { t } = useTranslation();
  const { clusterName } = useAppParams<ClusterNameRoute>();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();

  const consumerGroups = useConsumerGroups({
    clusterName,
    orderBy: (searchParams.get('sortBy') as ConsumerGroupOrdering) || undefined,
    sortOrder:
      (searchParams.get('sortDirection')?.toUpperCase() as SortOrder) ||
      undefined,
    page: Number(searchParams.get('page') || 1),
    perPage: Number(searchParams.get('perPage') || PER_PAGE),
    search: searchParams.get('q') || '',
  });

  const columns = React.useMemo<ColumnDef<ConsumerGroupDetails>[]>(
    () => [
      {
        id: ConsumerGroupOrdering.NAME,
        header: t('consumerGroup.groupId'),
        accessorKey: 'groupId',
        // eslint-disable-next-line react/no-unstable-nested-components
        cell: ({ getValue }) => (
          <LinkCell
            value={`${getValue<string | number>()}`}
            to={encodeURIComponent(`${getValue<string | number>()}`)}
          />
        ),
      },
      {
        id: ConsumerGroupOrdering.MEMBERS,
        header: t('consumerGroup.members'),
        accessorKey: 'members',
      },
      {
        id: ConsumerGroupOrdering.TOPIC_NUM,
        header: t('consumerGroup.topics'),
        accessorKey: 'topics',
      },
      {
        id: ConsumerGroupOrdering.MESSAGES_BEHIND,
        header: t('consumerGroup.consumerLag'),
        accessorKey: 'consumerLag',
        cell: (args) => {
          return args.getValue() || t('common.notAvailable');
        },
      },
      {
        header: t('consumerGroup.coordinator'),
        accessorKey: 'coordinator.id',
        enableSorting: false,
      },
      {
        id: ConsumerGroupOrdering.STATE,
        header: t('common.state'),
        accessorKey: 'state',
        // eslint-disable-next-line react/no-unstable-nested-components
        cell: (args) => {
          const value = args.getValue() as ConsumerGroupState;
          return (
            <Tooltip
              value={<TagCell {...args} />}
              content={CONSUMER_GROUP_STATE_TOOLTIPS[value]}
              placement="bottom-end"
            />
          );
        },
      },
    ],
    [t]
  );

  return (
    <>
      <PageHeading text={t('common.consumers')} />
      <ControlPanelWrapper hasInput>
        <Search placeholder={t('consumerGroup.searchByConsumerGroupID')} />
      </ControlPanelWrapper>
      <Table
        columns={columns}
        pageCount={consumerGroups.data?.pageCount || 0}
        data={consumerGroups.data?.consumerGroups || []}
        emptyMessage={
          consumerGroups.isSuccess
            ? t('consumerGroup.noConsumerGroupsFound')
            : t('common.loading')
        }
        serverSideProcessing
        enableSorting
        onRowClick={({ original }) =>
          navigate(
            clusterConsumerGroupDetailsPath(clusterName, original.groupId)
          )
        }
        disabled={consumerGroups.isFetching}
      />
    </>
  );
};

export default List;
