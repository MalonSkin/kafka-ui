import * as React from 'react';
import { useTranslation } from 'react-i18next';
import Input from 'components/common/Input/Input';
import Fileupload from 'widgets/ClusterConfigForm/common/Fileupload';
import * as S from 'widgets/ClusterConfigForm/ClusterConfigForm.styled';

type SSLFormProps = {
  prefix: string;
  title: string;
};

const SSLForm: React.FC<SSLFormProps> = ({ prefix, title }) => {
  // 引入 i18n 翻译函数
  const { t } = useTranslation();
  return (
    <S.GroupFieldWrapper>
      <Fileupload
        name={`${prefix}.location`}
        label={t('clusterConfig.sslLocation', { title })}
      />
      <Input
        label={t('clusterConfig.sslPassword', { title })}
        name={`${prefix}.password`}
        type="password"
        withError
      />
    </S.GroupFieldWrapper>
  );
};

export default SSLForm;
