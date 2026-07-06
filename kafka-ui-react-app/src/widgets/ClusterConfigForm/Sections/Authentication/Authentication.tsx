import React from 'react';
import { useTranslation } from 'react-i18next';
import { useFormContext } from 'react-hook-form';
import { AUTH_OPTIONS, SECURITY_PROTOCOL_OPTIONS } from 'lib/constants';
import ControlledSelect from 'components/common/Select/ControlledSelect';
import SectionHeader from 'widgets/ClusterConfigForm/common/SectionHeader';

import AuthenticationMethods from './AuthenticationMethods';

const Authentication: React.FC = () => {
  // 引入 i18n 翻译函数
  const { t } = useTranslation();
  const { watch, setValue } = useFormContext();
  const hasAuth = !!watch('auth');
  const authMethod = watch('auth.method');
  const hasSecurityProtocolField =
    authMethod && !['Delegation tokens', 'mTLS'].includes(authMethod);

  const toggle = () =>
    setValue('auth', hasAuth ? undefined : {}, {
      shouldValidate: true,
      shouldDirty: true,
      shouldTouch: true,
    });

  return (
    <>
      <SectionHeader
        title={t('clusterConfig.authentication')}
        adding={!hasAuth}
        addButtonText={t('clusterConfig.configureAuthentication')}
        onClick={toggle}
      />
      {hasAuth && (
        <>
          <ControlledSelect
            name="auth.method"
            label={t('clusterConfig.authenticationMethod')}
            placeholder={t('clusterConfig.selectAuthMethod')}
            options={AUTH_OPTIONS}
          />
          {hasSecurityProtocolField && (
            <ControlledSelect
              name="auth.securityProtocol"
              label={t('clusterConfig.securityProtocol')}
              placeholder={t('clusterConfig.selectSecurityProtocol')}
              options={SECURITY_PROTOCOL_OPTIONS}
            />
          )}
          <AuthenticationMethods method={authMethod} />
        </>
      )}
    </>
  );
};

export default Authentication;
