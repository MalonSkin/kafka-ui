import React from 'react';
import { MILLISECONDS_IN_DAY } from 'lib/constants';
import styled from 'styled-components';
import { useTranslation } from 'react-i18next';

import TimeToRetainBtn from './TimeToRetainBtn';

export interface Props {
  name: string;
  value: string;
}

const TimeToRetainBtnsWrapper = styled.div`
  display: flex;
  gap: 8px;
  padding-top: 8px;
`;

const TimeToRetainBtns: React.FC<Props> = ({ name }) => {
  const { t } = useTranslation();
  return (
    <TimeToRetainBtnsWrapper>
      <TimeToRetainBtn
        text={t('topic.retain12Hours')}
        inputName={name}
        value={MILLISECONDS_IN_DAY / 2}
      />
      <TimeToRetainBtn
        text={t('topic.retain1Day')}
        inputName={name}
        value={MILLISECONDS_IN_DAY}
      />
      <TimeToRetainBtn
        text={t('topic.retain2Days')}
        inputName={name}
        value={MILLISECONDS_IN_DAY * 2}
      />
      <TimeToRetainBtn
        text={t('topic.retain7Days')}
        inputName={name}
        value={MILLISECONDS_IN_DAY * 7}
      />
      <TimeToRetainBtn
        text={t('topic.retain4Weeks')}
        inputName={name}
        value={MILLISECONDS_IN_DAY * 7 * 4}
      />
    </TimeToRetainBtnsWrapper>
  );
};

export default TimeToRetainBtns;
