import React from 'react';
import { useTranslation } from 'react-i18next';
import { CellContext } from '@tanstack/react-table';
import { Tag } from 'components/common/Tag/Tag.styled';
import { Cluster } from 'generated-sources';

type ClusterNameProps = CellContext<Cluster, unknown>;

const ClusterName: React.FC<ClusterNameProps> = ({ row }) => {
  const { t } = useTranslation();
  const { readOnly, name } = row.original;
  return (
    <>
      {readOnly && <Tag color="blue">{t('dashboard.readonlyTag')}</Tag>}
      {name}
    </>
  );
};

export default ClusterName;
