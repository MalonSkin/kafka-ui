import React from 'react';
import { useTranslation } from 'react-i18next';
import { KsqlStreamDescription, KsqlTableDescription } from 'generated-sources';
import Table from 'components/common/NewTable';
import { ColumnDef } from '@tanstack/react-table';

interface TableViewProps {
  fetching: boolean;
  rows: KsqlTableDescription[] | KsqlStreamDescription[];
}

const TableView: React.FC<TableViewProps> = ({ fetching, rows }) => {
  // 引入 i18n 翻译函数
  const { t } = useTranslation();
  const columns = React.useMemo<
    ColumnDef<KsqlTableDescription | KsqlStreamDescription>[]
  >(
    () => [
      { header: t('ksqlDb.name'), accessorKey: 'name' },
      { header: t('topic.name'), accessorKey: 'topic' },
      { header: t('ksqlDb.keyFormat'), accessorKey: 'keyFormat' },
      { header: t('ksqlDb.valueFormat'), accessorKey: 'valueFormat' },
      {
        header: t('ksqlDb.isWindowed'),
        accessorKey: 'isWindowed',
        cell: ({ row }) =>
          'isWindowed' in row.original ? String(row.original.isWindowed) : '-',
      },
    ],
    [t]
  );
  return (
    <Table
      data={rows || []}
      columns={columns}
      emptyMessage={fetching ? t('common.loading') : t('common.noData')}
      enableSorting
    />
  );
};

export default TableView;
