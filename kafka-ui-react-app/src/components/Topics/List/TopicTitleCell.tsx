import React from 'react';
import { CellContext } from '@tanstack/react-table';
import { Tag } from 'components/common/Tag/Tag.styled';
import { Topic } from 'generated-sources';
import { NavLink } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

export const TopicTitleCell: React.FC<CellContext<Topic, unknown>> = ({
  row: { original },
}) => {
  const { internal, name } = original;
  const { t } = useTranslation();
  return (
    <NavLink to={name} title={name}>
      {internal && (
        <>
          <Tag color="gray">{t('topic.internalTag')}</Tag>
          &nbsp;
        </>
      )}
      {name}
    </NavLink>
  );
};
