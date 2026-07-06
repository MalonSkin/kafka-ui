import styled from 'styled-components';

/** 语言切换组件容器，水平排列图标和下拉框 */
export const Wrapper = styled.div`
  display: flex;
  align-items: center;
  gap: 4px;
  color: ${({ theme }) => theme.default.color.normal};
  cursor: pointer;

  .MuiInputBase-root {
    color: ${({ theme }) => theme.default.color.normal};
    font-size: 14px;
  }

  .MuiSvgIcon-root {
    color: ${({ theme }) => theme.default.color.normal};
  }

  .MuiSelect-select {
    padding: 4px 8px;
    min-width: 80px;
  }
`;
