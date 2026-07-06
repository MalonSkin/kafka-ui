import * as React from 'react';
import { useTranslation } from 'react-i18next';
import Input from 'components/common/Input/Input';
import * as S from 'widgets/ClusterConfigForm/ClusterConfigForm.styled';
import Checkbox from 'components/common/Checkbox/Checkbox';
import { useFormContext } from 'react-hook-form';

type CredentialsProps = {
  prefix: string;
  title?: string;
};

const Credentials: React.FC<CredentialsProps> = ({
  prefix,
  title,
}) => {
  // 引入 i18n 翻译函数
  const { t } = useTranslation();
  const displayTitle = title || t('clusterConfig.securedWithAuth');
  const { watch } = useFormContext();

  return (
    <S.GroupFieldWrapper>
      <Checkbox name={`${prefix}.isAuth`} label={displayTitle} />
      {watch(`${prefix}.isAuth`) && (
        <S.FlexRow>
          <S.FlexGrow1>
            <Input
              label={t('clusterConfig.usernameRequired')}
              type="text"
              name={`${prefix}.username`}
              withError
            />
          </S.FlexGrow1>
          <S.FlexGrow1>
            <Input
              label={t('clusterConfig.passwordRequired')}
              type="password"
              name={`${prefix}.password`}
              withError
            />
          </S.FlexGrow1>
        </S.FlexRow>
      )}
    </S.GroupFieldWrapper>
  );
};

export default Credentials;
