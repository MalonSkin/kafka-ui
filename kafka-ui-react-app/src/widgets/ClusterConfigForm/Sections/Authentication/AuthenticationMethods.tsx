import React from 'react';
import { useTranslation } from 'react-i18next';
import Input from 'components/common/Input/Input';
import Checkbox from 'components/common/Checkbox/Checkbox';
import Fileupload from 'widgets/ClusterConfigForm/common/Fileupload';
import SSLForm from 'widgets/ClusterConfigForm/common/SSLForm';
import Credentials from 'widgets/ClusterConfigForm/common/Credentials';

const AuthenticationMethods: React.FC<{ method: string }> = ({ method }) => {
  // 引入 i18n 翻译函数
  const { t } = useTranslation();
  switch (method) {
    case 'SASL/JAAS':
      return (
        <>
          <Input
            type="text"
            name="auth.props.saslJaasConfig"
            label="sasl.jaas.config"
            withError
          />
          <Input
            type="text"
            name="auth.props.saslMechanism"
            label="sasl.mechanism"
            withError
          />
        </>
      );
    case 'SASL/GSSAPI':
      return (
        <>
          <Input
            label={t('clusterConfig.kerberosServiceName')}
            type="text"
            name="auth.props.saslKerberosServiceName"
            withError
          />
          <Checkbox name="auth.props.storeKey" label={t('clusterConfig.storeKey')} />
          <Fileupload
            name="auth.props.keyTabFile"
            label={t('clusterConfig.keyTabOptional')}
          />
          <Input
            type="text"
            name="auth.props.principal"
            label={t('clusterConfig.principalRequired')}
            withError
          />
        </>
      );
    case 'SASL/OAUTHBEARER':
      return (
        <Input
          label={t('clusterConfig.unsecuredLoginClaimRequired')}
          type="text"
          name="auth.props.unsecuredLoginStringClaim_sub"
          withError
        />
      );
    case 'SASL/PLAIN':
    case 'SASL/SCRAM-256':
    case 'SASL/SCRAM-512':
    case 'SASL/LDAP':
      return <Credentials prefix="auth.props" />;
    case 'Delegation tokens':
      return (
        <>
          <Input
            label={t('clusterConfig.tokenId')}
            type="text"
            name="auth.props.tokenId"
            withError
          />
          <Input
            label={t('clusterConfig.tokenValueRequired')}
            type="text"
            name="auth.props.tokenValue"
            withError
          />
        </>
      );
    case 'SASL/AWS IAM':
      return (
        <Input
          label={t('clusterConfig.awsProfileName')}
          type="text"
          name="auth.props.awsProfileName"
          withError
        />
      );
    case 'mTLS':
      return <SSLForm prefix="auth.keystore" title={t('clusterConfig.keystore')} />;
    default:
      return null;
  }
};

export default AuthenticationMethods;
