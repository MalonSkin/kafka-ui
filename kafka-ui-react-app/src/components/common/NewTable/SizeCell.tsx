import React from 'react';
import { CellContext } from '@tanstack/react-table';
import BytesFormatted from 'components/common/BytesFormatted/BytesFormatted';
import { useTranslation } from 'react-i18next';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type AsAny = any;

const SizeCell: React.FC<
  CellContext<AsAny, unknown> & { renderSegments?: boolean; precision?: number }
> = ({ getValue, row, renderSegments = false, precision = 0 }) => {
  const { t } = useTranslation();
  return (
    <>
      <BytesFormatted value={getValue<string | number>()} precision={precision} />
      {renderSegments ? t('common.segmentsCount', { count: row?.original.count }) : null}
    </>
  );
};

export default SizeCell;
