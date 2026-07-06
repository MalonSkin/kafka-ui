import React, { Suspense } from 'react';
import { useTranslation } from 'react-i18next';
import { NavLink, Route, Routes } from 'react-router-dom';
import useAppParams from 'lib/hooks/useAppParams';
import {
  clusterConnectConnectorConfigPath,
  clusterConnectConnectorConfigRelativePath,
  clusterConnectConnectorPath,
  clusterConnectorsPath,
  RouterParamsClusterConnectConnector,
} from 'lib/paths';
import Navbar from 'components/common/Navigation/Navbar.styled';
import PageHeading from 'components/common/PageHeading/PageHeading';
import PageLoader from 'components/common/PageLoader/PageLoader';

import Overview from './Overview/Overview';
import Tasks from './Tasks/Tasks';
import Config from './Config/Config';
import Actions from './Actions/Actions';

const DetailsPage: React.FC = () => {
  const { t } = useTranslation();
  const { clusterName, connectName, connectorName } =
    useAppParams<RouterParamsClusterConnectConnector>();

  return (
    <div>
      <PageHeading
        text={connectorName}
        backTo={clusterConnectorsPath(clusterName)}
        backText={t('connector.backToConnectors')}
      >
        <Actions />
      </PageHeading>
      <Overview />
      <Navbar role="navigation">
        <NavLink
          to={clusterConnectConnectorPath(
            clusterName,
            connectName,
            connectorName
          )}
          className={({ isActive }) => (isActive ? 'is-active' : '')}
          end
        >
          {t('common.tasks')}
        </NavLink>
        <NavLink
          to={clusterConnectConnectorConfigPath(
            clusterName,
            connectName,
            connectorName
          )}
          className={({ isActive }) => (isActive ? 'is-active' : '')}
        >
          {t('connector.tabConfig')}
        </NavLink>
      </Navbar>
      <Suspense fallback={<PageLoader />}>
        <Routes>
          <Route index element={<Tasks />} />
          <Route
            path={clusterConnectConnectorConfigRelativePath}
            element={<Config />}
          />
        </Routes>
      </Suspense>
    </div>
  );
};

export default DetailsPage;
