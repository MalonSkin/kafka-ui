import React from 'react';
import { useTranslation } from 'react-i18next';
import { FullConnectorInfo } from 'generated-sources';
import { CellContext } from '@tanstack/react-table';

const RunningTasksCell: React.FC<CellContext<FullConnectorInfo, unknown>> = ({
  row,
}) => {
  const { t } = useTranslation();
  const { tasksCount, failedTasksCount } = row.original;

  if (!tasksCount) {
    return null;
  }

  return (
    <>
      {t('connector.runningTasksOf', { running: tasksCount - (failedTasksCount || 0), total: tasksCount })}
    </>
  );
};

export default RunningTasksCell;
