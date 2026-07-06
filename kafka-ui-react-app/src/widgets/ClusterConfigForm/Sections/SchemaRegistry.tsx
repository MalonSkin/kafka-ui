import React from 'react';
import { useTranslation } from 'react-i18next';
import Input from 'components/common/Input/Input';
import { useFormContext } from 'react-hook-form';
import SectionHeader from 'widgets/ClusterConfigForm/common/SectionHeader';
import SSLForm from 'widgets/ClusterConfigForm/common/SSLForm';
import Credentials from 'widgets/ClusterConfigForm/common/Credentials';

const SchemaRegistry = () => {
  // 引入 i18n 翻译函数
  const { t } = useTranslation();
  const { setValue, watch } = useFormContext();
  const schemaRegistry = watch('schemaRegistry');
  const toggleConfig = () => {
    setValue(
      'schemaRegistry',
      schemaRegistry ? undefined : { url: '', isAuth: false },
      { shouldValidate: true, shouldDirty: true, shouldTouch: true }
    );
  };
  return (
    <>
      <SectionHeader
        title={t('clusterConfig.schemaRegistry')}
        adding={!schemaRegistry}
        addButtonText={t('clusterConfig.configureSchemaRegistry')}
        onClick={toggleConfig}
      />
      {schemaRegistry && (
        <>
          <Input
            label={t('clusterConfig.urlRequired')}
            name="schemaRegistry.url"
            type="text"
            placeholder="http://localhost:8081"
            withError
          />
          <Credentials
            prefix="schemaRegistry"
            title={t('clusterConfig.isSchemaRegistrySecured')}
          />
          <SSLForm prefix="schemaRegistry.keystore" title={t('clusterConfig.keystore')} />
        </>
      )}
    </>
  );
};
export default SchemaRegistry;
