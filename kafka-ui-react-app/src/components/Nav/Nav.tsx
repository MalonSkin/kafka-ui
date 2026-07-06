import { useClusters } from 'lib/hooks/api/clusters';
import React from 'react';
import { useTranslation } from 'react-i18next';

import ClusterMenu from './ClusterMenu';
import ClusterMenuItem from './ClusterMenuItem';
import * as S from './Nav.styled';

const Nav: React.FC = () => {
  const { t } = useTranslation();
  const clusters = useClusters();

  return (
    <aside aria-label={t('common.sidebar')}>
      <S.List>
        <ClusterMenuItem to="/" title={t('common.dashboard')} isTopLevel />
      </S.List>
      {clusters.isSuccess &&
        clusters.data.map((cluster) => (
          <ClusterMenu
            cluster={cluster}
            key={cluster.name}
            singleMode={clusters.data.length === 1}
          />
        ))}
    </aside>
  );
};

export default Nav;