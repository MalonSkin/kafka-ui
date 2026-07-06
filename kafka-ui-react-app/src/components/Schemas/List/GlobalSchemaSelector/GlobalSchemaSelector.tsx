import React from 'react';
import {
  Action,
  CompatibilityLevelCompatibilityEnum,
  ResourceType,
} from 'generated-sources';
import { useAppDispatch } from 'lib/hooks/redux';
import useAppParams from 'lib/hooks/useAppParams';
import { fetchSchemas } from 'redux/reducers/schemas/schemasSlice';
import { ClusterNameRoute } from 'lib/paths';
import { schemasApiClient } from 'lib/api';
import { showServerError } from 'lib/errorHandling';
import { useConfirm } from 'lib/hooks/useConfirm';
import { useSearchParams } from 'react-router-dom';
import { PER_PAGE } from 'lib/constants';
import { ActionSelect } from 'components/common/ActionComponent';

import * as S from './GlobalSchemaSelector.styled';
import { useTranslation } from 'react-i18next';

const GlobalSchemaSelector: React.FC = () => {
  // 引入 i18n 翻译函数

  const { t } = useTranslation();
  const { clusterName } = useAppParams<ClusterNameRoute>();
  const dispatch = useAppDispatch();
  const [searchParams] = useSearchParams();
  const confirm = useConfirm();

  const [currentCompatibilityLevel, setCurrentCompatibilityLevel] =
    React.useState<CompatibilityLevelCompatibilityEnum | undefined>();

  const [isFetching, setIsFetching] = React.useState(false);

  React.useEffect(() => {
    const fetchData = async () => {
      setIsFetching(true);
      try {
        const { compatibility } =
          await schemasApiClient.getGlobalSchemaCompatibilityLevel({
            clusterName,
          });
        setCurrentCompatibilityLevel(compatibility);
      } catch (error) {
        // do nothing
      }
      setIsFetching(false);
    };

    fetchData();
  }, [clusterName]);

  const handleChangeCompatibilityLevel = (level: string | number) => {
    const nextLevel = level as CompatibilityLevelCompatibilityEnum;
    // 确认更新全局兼容性级别的提示
    confirm(
      t('schema.updateGlobalCompatibilityConfirm', { level: nextLevel }),
      async () => {
        try {
          await schemasApiClient.updateGlobalSchemaCompatibilityLevel({
            clusterName,
            compatibilityLevel: {
              compatibility: nextLevel,
            },
          });
          setCurrentCompatibilityLevel(nextLevel);
          dispatch(
            fetchSchemas({
              clusterName,
              page: Number(searchParams.get('page') || 1),
              perPage: Number(searchParams.get('perPage') || PER_PAGE),
              search: searchParams.get('q') || '',
            })
          );
        } catch (e) {
          showServerError(e as Response);
        }
      }
    );
  };

  if (!currentCompatibilityLevel) return null;

  return (
    <S.Wrapper>
      <div>{t('schema.globalCompatibilityLevel')}: </div>
      <ActionSelect
        selectSize="M"
        defaultValue={currentCompatibilityLevel}
        minWidth="200px"
        onChange={handleChangeCompatibilityLevel}
        disabled={isFetching}
        options={Object.keys(CompatibilityLevelCompatibilityEnum).map(
          (level) => ({ value: level, label: level })
        )}
        permission={{
          resource: ResourceType.SCHEMA,
          action: Action.MODIFY_GLOBAL_COMPATIBILITY,
        }}
      />
    </S.Wrapper>
  );
};

export default GlobalSchemaSelector;