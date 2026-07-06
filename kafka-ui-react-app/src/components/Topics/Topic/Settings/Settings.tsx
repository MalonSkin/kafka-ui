import React from 'react';
import { useTranslation } from 'react-i18next';
import Table from 'components/common/NewTable';
import { RouteParamsClusterTopic } from 'lib/paths';
import useAppParams from 'lib/hooks/useAppParams';
import { useTopicConfig } from 'lib/hooks/api/topics';
import { CellContext, ColumnDef } from '@tanstack/react-table';
import { TopicConfig } from 'generated-sources';

import * as S from './Settings.styled';

const ValueCell: React.FC<CellContext<TopicConfig, unknown>> = ({
  row,
  renderValue,
}) => {
  const { defaultValue } = row.original;
  const { value } = row.original;
  const hasCustomValue = !!defaultValue && value !== defaultValue;

  return (
    <S.Value $hasCustomValue={hasCustomValue}>{renderValue<string>()}</S.Value>
  );
};

const DefaultValueCell: React.FC<CellContext<TopicConfig, unknown>> = ({
  row,
  getValue,
}) => {
  const defaultValue = getValue<TopicConfig['defaultValue']>();
  const { value } = row.original;
  const hasCustomValue = !!defaultValue && value !== defaultValue;
  return <S.DefaultValue>{hasCustomValue && defaultValue}</S.DefaultValue>;
};

const Settings: React.FC = () => {
  // 引入 i18n 翻译函数
  const { t } = useTranslation();
  const props = useAppParams<RouteParamsClusterTopic>();
  const { data = [] } = useTopicConfig(props);

  const columns = React.useMemo<ColumnDef<TopicConfig>[]>(
    () => [
      {
        header: t('common.key'),
        accessorKey: 'name',
        cell: ValueCell,
      },
      {
        header: t('common.value'),
        accessorKey: 'value',
        cell: ValueCell,
      },
      {
        header: t('topic.defaultValue'),
        accessorKey: 'defaultValue',
        cell: DefaultValueCell,
      },
    ],
    [t]
  );

  return <Table columns={columns} data={data} />;
};

export default Settings;
