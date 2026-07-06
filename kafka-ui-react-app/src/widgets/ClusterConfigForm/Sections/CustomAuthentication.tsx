import React from 'react';
import { useTranslation } from 'react-i18next';
import { useFormContext } from 'react-hook-form';
import Input from 'components/common/Input/Input';
import { convertFormKeyToPropsKey } from 'widgets/ClusterConfigForm/utils/convertFormKeyToPropsKey';
import SectionHeader from 'widgets/ClusterConfigForm/common/SectionHeader';

const CustomAuthentication: React.FC = () => {
  // 引入 i18n 翻译函数
  const { t } = useTranslation();
  const { watch, setValue } = useFormContext();
  const customConf = watch('customAuth');
  const hasCustomConfig =
    customConf && Object.values(customConf).some((v) => !!v);

  const remove = () =>
    setValue('customAuth', undefined, {
      shouldValidate: true,
      shouldDirty: true,
      shouldTouch: true,
    });
  return (
    <>
      <SectionHeader
        title={t('clusterConfig.authentication')}
        addButtonText={t('clusterConfig.configureAuthentication')}
        onClick={remove}
      />
      {hasCustomConfig && (
        <>
          {Object.keys(customConf).map((key) => (
            <Input
              key={key}
              type="text"
              name={`customAuth.${key}`}
              label={convertFormKeyToPropsKey(key)}
              withError
            />
          ))}
        </>
      )}
    </>
  );
};

export default CustomAuthentication;
