import React from 'react';
import { useTranslation } from 'react-i18next';
import { CellContext, ColumnDef } from '@tanstack/react-table';
import { ClusterBrokerParam } from 'lib/paths';
import useAppParams from 'lib/hooks/useAppParams';
import {
  useBrokerConfig,
  useUpdateBrokerConfigByName,
} from 'lib/hooks/api/brokers';
import Table from 'components/common/NewTable';
import { BrokerConfig, ConfigSource } from 'generated-sources';
import Search from 'components/common/Search/Search';
import Tooltip from 'components/common/Tooltip/Tooltip';
import InfoIcon from 'components/common/Icons/InfoIcon';

import InputCell from './InputCell';
import * as S from './Configs.styled';

const Configs: React.FC = () => {
  const { t } = useTranslation();
  const [keyword, setKeyword] = React.useState('');
  const { clusterName, brokerId } = useAppParams<ClusterBrokerParam>();
  const { data = [] } = useBrokerConfig(clusterName, Number(brokerId));
  const stateMutation = useUpdateBrokerConfigByName(
    clusterName,
    Number(brokerId)
  );

  const getData = () => {
    return data
      .filter((item) => {
        const nameMatch = item.name
          .toLocaleLowerCase()
          .includes(keyword.toLocaleLowerCase());
        return nameMatch
          ? true
          : item.value &&
              item.value
                .toLocaleLowerCase()
                .includes(keyword.toLocaleLowerCase()); // try to match the keyword on any of the item.value elements when nameMatch fails but item.value exists
      })
      .sort((a, b) => {
        if (a.source === b.source) return 0;
        return a.source === ConfigSource.DYNAMIC_BROKER_CONFIG ? -1 : 1;
      });
  };

  const dataSource = React.useMemo(() => getData(), [data, keyword]);

  const renderCell = (props: CellContext<BrokerConfig, unknown>) => (
    <InputCell
      {...props}
      onUpdate={(name, value) => {
        stateMutation.mutateAsync({
          name,
          brokerConfigItem: {
            value,
          },
        });
      }}
    />
  );

  const columns = React.useMemo<ColumnDef<BrokerConfig>[]>(
    () => [
      { header: t('common.key'), accessorKey: 'name' },
      {
        header: t('common.value'),
        accessorKey: 'value',
        cell: renderCell,
      },
      {
        // eslint-disable-next-line react/no-unstable-nested-components
        header: () => {
          return (
            <S.Source>
              {t('common.source')}
              <Tooltip
                value={<InfoIcon />}
                content={t('broker.configSourceTooltip')}
                placement="top-end"
              />
            </S.Source>
          );
        },
        accessorKey: 'source',
      },
    ],
    [t]
  );

  return (
    <>
      <S.SearchWrapper>
        <Search
          onChange={setKeyword}
          placeholder={t('broker.searchByKeyOrValue')}
          value={keyword}
        />
      </S.SearchWrapper>
      <Table columns={columns} data={dataSource} />
    </>
  );
};

export default Configs;
