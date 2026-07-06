import React from 'react';
import { useTranslation } from 'react-i18next';
import Input from 'components/common/Input/Input';
import { useFormContext } from 'react-hook-form';
import SectionHeader from 'widgets/ClusterConfigForm/common/SectionHeader';
import SSLForm from 'widgets/ClusterConfigForm/common/SSLForm';
import Credentials from 'widgets/ClusterConfigForm/common/Credentials';

const KSQL = () => {
  // 引入 i18n 翻译函数
  const { t } = useTranslation();
  const { setValue, watch } = useFormContext();
  const ksql = watch('ksql');
  const toggleConfig = () => {
    setValue('ksql', ksql ? undefined : { url: '', isAuth: false }, {
      shouldValidate: true,
      shouldDirty: true,
      shouldTouch: true,
    });
  };
  return (
    <>
      <SectionHeader
        title={t('clusterConfig.ksqlDb')}
        adding={!ksql}
        addButtonText={t('clusterConfig.configureKsqlDb')}
        onClick={toggleConfig}
      />
      {ksql && (
        <>
          <Input
            label={t('clusterConfig.urlRequired')}
            name="ksql.url"
            type="text"
            placeholder="http://localhost:8088"
            withError
          />
          <Credentials prefix="ksql" title={t('clusterConfig.isKsqlDbSecured')} />
          <SSLForm prefix="ksql.keystore" title={t('clusterConfig.ksqlDbKeystore')} />
        </>
      )}
    </>
  );
};
export default KSQL;
