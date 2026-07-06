import React from 'react';
import { useTranslation } from 'react-i18next';
import Input from 'components/common/Input/Input';
import { useFormContext } from 'react-hook-form';
import ControlledSelect from 'components/common/Select/ControlledSelect';
import { METRICS_OPTIONS } from 'lib/constants';
import * as S from 'widgets/ClusterConfigForm/ClusterConfigForm.styled';
import SectionHeader from 'widgets/ClusterConfigForm/common/SectionHeader';
import SSLForm from 'widgets/ClusterConfigForm/common/SSLForm';
import Credentials from 'widgets/ClusterConfigForm/common/Credentials';

const Metrics = () => {
  // 引入 i18n 翻译函数
  const { t } = useTranslation();
  const { setValue, watch } = useFormContext();
  const visibleMetrics = !!watch('metrics');
  const toggleMetrics = () =>
    setValue(
      'metrics',
      visibleMetrics
        ? undefined
        : {
            type: '',
            port: 0,
            isAuth: false,
          },
      { shouldValidate: true, shouldDirty: true, shouldTouch: true }
    );

  return (
    <>
      <SectionHeader
        title={t('clusterConfig.metrics')}
        adding={!visibleMetrics}
        addButtonText={t('clusterConfig.configureMetrics')}
        onClick={toggleMetrics}
      />
      {visibleMetrics && (
        <>
          <ControlledSelect
            name="metrics.type"
            label={t('clusterConfig.metricsType')}
            placeholder={t('clusterConfig.chooseMetricsType')}
            options={METRICS_OPTIONS}
          />
          <S.Port>
            <Input
              label={t('clusterConfig.portRequired')}
              name="metrics.port"
              type="number"
              positiveOnly
              withError
            />
          </S.Port>
          <Credentials prefix="metrics" />
          <SSLForm prefix="metrics.keystore" title={t('clusterConfig.metricsKeystore')} />
        </>
      )}
    </>
  );
};
export default Metrics;
