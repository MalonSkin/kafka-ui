import React from 'react';
import { clusterConsumerGroupsPath, RouteParamsClusterTopic } from 'lib/paths';
import { ConsumerGroup } from 'generated-sources';
import useAppParams from 'lib/hooks/useAppParams';
import { useTopicConsumerGroups } from 'lib/hooks/api/topics';
import { ColumnDef } from '@tanstack/react-table';
import Table, { LinkCell, TagCell } from 'components/common/NewTable';
import Search from 'components/common/Search/Search';

import * as S from './TopicConsumerGroups.styled';
import { useTranslation } from 'react-i18next';

const TopicConsumerGroups: React.FC = () => {
  // 引入 i18n 翻译函数

  const { t } = useTranslation();
  const [keyword, setKeyword] = React.useState('');
  const { clusterName, topicName } = useAppParams<RouteParamsClusterTopic>();

  const { data = [] } = useTopicConsumerGroups({
    clusterName,
    topicName,
  });

  const consumerGroups = React.useMemo(
    () =>
      data.filter(
        (item) => item.groupId.toLocaleLowerCase().indexOf(keyword) > -1
      ),
    [data, keyword]
  );

  const columns = React.useMemo<ColumnDef<ConsumerGroup>[]>(
    () => [
      {
        header: t('consumerGroup.groupId'),
        accessorKey: 'groupId',
        enableSorting: false,
        // eslint-disable-next-line react/no-unstable-nested-components
        cell: ({ row }) => (
          <LinkCell
            value={row.original.groupId}
            to={`${clusterConsumerGroupsPath(clusterName)}/${
              row.original.groupId
            }`}
          />
        ),
      },
      {
        header: t('consumerGroup.activeConsumers'),
        accessorKey: 'members',
        enableSorting: false,
      },
      {
        header: t('consumerGroup.consumerLag'),
        accessorKey: 'consumerLag',
        enableSorting: false,
      },
      {
        header: t('consumerGroup.coordinator'),
        accessorKey: 'coordinator',
        enableSorting: false,
        cell: ({ getValue }) => {
          const coordinator = getValue<ConsumerGroup['coordinator']>();
          if (coordinator === undefined) {
            return 0;
          }
          return coordinator.id;
        },
      },
      {
        header: t('common.state'),
        accessorKey: 'state',
        enableSorting: false,
        cell: TagCell,
      },
    ],
    [t]
  );
  return (
    <>
      <S.SearchWrapper>
        <Search
          onChange={setKeyword}
          placeholder={t('consumerGroup.searchByConsumerName')}
          value={keyword}
        />
      </S.SearchWrapper>
      <Table
        columns={columns}
        data={consumerGroups}
        enableSorting
        emptyMessage={t('topic.noActiveConsumerGroups')}
      />
    </>
  );
};

export default TopicConsumerGroups;