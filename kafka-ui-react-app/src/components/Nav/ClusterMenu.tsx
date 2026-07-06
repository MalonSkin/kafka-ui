import React from 'react';
import { useTranslation } from 'react-i18next';
import { Cluster, ClusterFeaturesEnum } from 'generated-sources';
import {
  clusterBrokersPath,
  clusterTopicsPath,
  clusterConsumerGroupsPath,
  clusterSchemasPath,
  clusterConnectorsPath,
  clusterKsqlDbPath,
  clusterACLPath,
} from 'lib/paths';

import ClusterMenuItem from './ClusterMenuItem';
import ClusterTab from './ClusterTab/ClusterTab';
import * as S from './Nav.styled';

interface Props {
  cluster: Cluster;
  singleMode?: boolean;
}

const ClusterMenu: React.FC<Props> = ({
  cluster: { name, status, features },
  singleMode,
}) => {
  const { t } = useTranslation();
  const hasFeatureConfigured = (key: ClusterFeaturesEnum) =>
    features?.includes(key);
  const [isOpen, setIsOpen] = React.useState(!!singleMode);
  return (
    <S.List>
      <hr />
      <ClusterTab
        title={name}
        status={status}
        isOpen={isOpen}
        toggleClusterMenu={() => setIsOpen((prev) => !prev)}
      />
      {isOpen && (
        <S.List>
          <ClusterMenuItem to={clusterBrokersPath(name)} title={t('common.brokers')} />
          <ClusterMenuItem to={clusterTopicsPath(name)} title={t('common.topics')} />
          <ClusterMenuItem
            to={clusterConsumerGroupsPath(name)}
            title={t('common.consumers')}
          />
          {hasFeatureConfigured(ClusterFeaturesEnum.SCHEMA_REGISTRY) && (
            <ClusterMenuItem
              to={clusterSchemasPath(name)}
              title={t('common.schemaRegistry')}
            />
          )}
          {hasFeatureConfigured(ClusterFeaturesEnum.KAFKA_CONNECT) && (
            <ClusterMenuItem
              to={clusterConnectorsPath(name)}
              title={t('common.kafkaConnect')}
            />
          )}
          {hasFeatureConfigured(ClusterFeaturesEnum.KSQL_DB) && (
            <ClusterMenuItem to={clusterKsqlDbPath(name)} title={t('common.ksqlDb')} />
          )}
          {(hasFeatureConfigured(ClusterFeaturesEnum.KAFKA_ACL_VIEW) ||
            hasFeatureConfigured(ClusterFeaturesEnum.KAFKA_ACL_EDIT)) && (
            <ClusterMenuItem to={clusterACLPath(name)} title={t('common.acl')} />
          )}
        </S.List>
      )}
    </S.List>
  );
};

export default ClusterMenu;
